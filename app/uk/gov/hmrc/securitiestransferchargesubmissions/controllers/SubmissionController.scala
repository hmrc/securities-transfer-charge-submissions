/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.securitiestransferchargesubmissions.controllers

import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.*
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcTransactionCreateResponse.given
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{StcCharge, StcTransactionCreateBadRequest, StcTransactionCreateBusinessError, StcTransactionCreateProcessed, StcTransactionCreateProcessedBody, StcTransactionCreateResponse}
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.SubmissionConnector
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{SubmissionBatchPayload, TransferDetailsFailure, TransferDetailsSuccess}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.ApiErrorResponse
import uk.gov.hmrc.securitiestransferchargesubmissions.services.ErrorMessages

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionController @Inject()(
  cc: ControllerComponents,
  submissionConnector: SubmissionConnector,
  val authConnector: AuthConnector
)(using ec: ExecutionContext) extends BackendController(cc) with AuthorisedFunctions with Logging:

  def submitBatchAction(submissionId: String): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      validateRequest(request) match
        case Left(result) => Future.successful(result)
        case Right((correlationId, subscriptionId, payload)) =>
          val resps = submissionConnector
            .submitTransfers(
              subscriptionId = subscriptionId,
              submissionId = submissionId,
              correlationId = correlationId,
              declaration = payload.declaration,
              transfers = payload.transfers
            )

          val response: Future[StcTransactionCreateResponse] = resps.map { charges =>
            StcTransactionCreateProcessed(
              success = StcTransactionCreateProcessedBody(
                processingDate = LocalDate.now().toString,
                charges = charges.asInstanceOf[List[StcCharge]]
              )
            )
          }
          
          val result = for {
            resp <- response
            httpResp = resp match {
              case processed: StcTransactionCreateProcessed    => Created(Json.toJson(processed))
              case bad: StcTransactionCreateBadRequest         => BadRequest(Json.toJson(bad))
              case business: StcTransactionCreateBusinessError => UnprocessableEntity(Json.toJson(business))
            }
          } yield httpResp

          result.recover(handleClientMappingErrors)
    }
  }

  /*
   * Maps the ETMP view outcome onto this service's API:
   *
   *   TransferDetailsSuccess                 => 200 with the transfer details
   *   TransferDetailsFailure, code 005       => 404 (UTRN not found for this subscription)
   *   TransferDetailsFailure, any other code => 500 and log. 002 (STC ID does not exist) means the
   *                                             subscription id from the caller's enrolment is unknown
   *                                             to ETMP, which the user can't fix.
   *   ETMP 404 with no business error        => 502. HIP couldn't route the request (e.g. bad base URL),
   *                                             so it must not look like "transfer not found".
   *   Other upstream failures                => rethrown for the bootstrap error handler, as for submissions.
   */
  def getTransferDetails(utrn: String): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      (headerValue(request, InboundHeaders.CorrelationId), headerValue(request, InboundHeaders.SubscriptionId)) match
        case (Some(correlationId), Some(subscriptionId)) =>
          submissionConnector
            .getTransferDetails(subscriptionId, correlationId, utrn)
            .map {
              case TransferDetailsSuccess(details) =>
                Ok(Json.toJson(details))
              case TransferDetailsFailure(error) if error.code == EtmpUtrnNotFound =>
                NotFound(ApiErrorResponse.asJson(ErrorMessages.TransferNotFound))
              case TransferDetailsFailure(error) =>
                logger.error(
                  s"ETMP returned business error ${error.code} (${error.text}) when getting transfer details for request $correlationId"
                )
                InternalServerError(ApiErrorResponse.asJson(ErrorMessages.TransferRetrievalFailed))
            }
            .recover {
              case e: UpstreamErrorResponse if e.reportAs == NOT_FOUND =>
                logger.error(s"ETMP returned HTTP 404 with no business error for request $correlationId: ${e.message}")
                BadGateway(ApiErrorResponse.asJson(ErrorMessages.TransferRetrievalFailed))
            }
        case _ =>
          Future.successful(badRequest(ErrorMessages.MissingRequiredHeaders))
    }
  }

  private val EtmpUtrnNotFound = "005"

  private def validateRequest(
    request: Request[AnyContent]
  ): Either[Result, (String, String, SubmissionBatchPayload)] =
    // Phase 1 – fail fast: both required headers must be present.
    val correlationId  = headerValue(request, InboundHeaders.CorrelationId)
    val subscriptionId = headerValue(request, InboundHeaders.SubscriptionId)
    if correlationId.isEmpty || subscriptionId.isEmpty then
      return Left(singleDetailBadRequest(Json.obj("message" -> ErrorMessages.MissingRequiredHeaders)))

    // Phase 2 – fail fast: body must be valid JSON that matches the expected schema.
    val payload: SubmissionBatchPayload = request.body.asJson match
      case None =>
        return Left(singleDetailBadRequest(Json.obj("message" -> ErrorMessages.MalformedJsonBody)))
      case Some(body) =>
        body.validate[SubmissionBatchPayload].asEither match
          case Left(jsErrors) =>
            return Left(singleDetailBadRequest(JsError.toJson(jsErrors)))
          case Right(p) => p

    // Phase 3 – payload constraints: checks are currently mutually exclusive.
    if payload.transfers.isEmpty then
      Left(singleDetailBadRequest(Json.obj("message" -> ErrorMessages.EmptyTransferBatch)))
    else if !hasUniqueRecordIds(payload) then
      Left(singleDetailBadRequest(Json.obj("message" -> ErrorMessages.DuplicateRecordIds)))
    else
      Right((correlationId.get, subscriptionId.get, payload))

  private def singleDetailBadRequest(detail: JsValue): Result =
    badRequest(ErrorMessages.InvalidTransferData, Some(JsArray(Seq(detail))))

  private def hasUniqueRecordIds(payload: SubmissionBatchPayload): Boolean =
    val recordIds = payload.transfers.map(_.recordId)
    recordIds.distinct.size == recordIds.size

  private def headerValue(request: RequestHeader, name: String): Option[String] =
    request.headers.get(name).map(_.trim).filter(_.nonEmpty)


  private def badRequest(error: String, details: Option[JsValue] = None): Result =
    BadRequest(ApiErrorResponse.asJson(error = error, details = details))

  private def handleClientMappingErrors: PartialFunction[Throwable, Result] =
    case e: JsResultException =>
      badRequest(ErrorMessages.InvalidTransferData, Some(JsError.toJson(e.errors)))
    case _: IllegalArgumentException =>
      badRequest(ErrorMessages.InvalidTransferData)
