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
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcTransactionCreateResponse.given
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{StcTransactionCreateProcessed, StcTransactionCreateProcessedBody}
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{SubmissionConnector, ViewTransactionResult}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.SubmissionBatchPayload
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
          submissionConnector
            .submitTransfers(
              subscriptionId = subscriptionId,
              submissionId = submissionId,
              correlationId = correlationId,
              declaration = payload.declaration,
              transfers = payload.transfers
            )
            .map { charges =>
              Created(Json.toJson(StcTransactionCreateProcessed(
                success = StcTransactionCreateProcessedBody(
                  processingDate = LocalDate.now().toString,
                  charges = charges.toList
                )
              )))
            }
            .recover(handleClientMappingErrors)
    }
  }   
      
    
  def viewTransactionAction(utrn: String): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      requiredHeaders(request).flatMap(validateViewInputs(utrn)) match
        case Left(result) => Future.successful(result)
        case Right((correlationId, subscriptionId)) =>
          submissionConnector
            .viewTransaction(subscriptionId = subscriptionId, utrn = utrn, correlationId = correlationId)
            .map {
              case ViewTransactionResult.Found(transaction) => Ok(Json.toJson(transaction))
              case ViewTransactionResult.StcIdNotFound      => notFound(ErrorMessages.StcIdNotFoundCode, ErrorMessages.StcIdNotFound)
              case ViewTransactionResult.UtrnNotFound       => notFound(ErrorMessages.UtrnNotFoundCode, ErrorMessages.UtrnNotFound)
            }
            .recover { case e =>
              logger.error(s"Failed to view transaction [correlationId=$correlationId]", e)
              InternalServerError(ApiErrorResponse.asJson(ErrorMessages.ViewTransactionFailed))
            }
    }
  }

  // Limits from the ETMP STC Transaction View OAS; checked here so caller mistakes are 400s, not ETMP failures.
  private val CorrelationIdPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$".r
  private val MaxStcIdLength = 15
  private val MaxUtrnLength = 12

  private def validateViewInputs(utrn: String)(headers: (String, String)): Either[Result, (String, String)] =
    val (correlationId, subscriptionId) = headers
    val errors = Seq(
      Option.when(!CorrelationIdPattern.matches(correlationId))(ErrorMessages.InvalidCorrelationId),
      Option.when(subscriptionId.length > MaxStcIdLength)(ErrorMessages.InvalidStcId),
      Option.when(utrn.isBlank || utrn.length > MaxUtrnLength)(ErrorMessages.InvalidUtrn)
    ).flatten
    if errors.isEmpty then Right(headers)
    else Left(badRequest(ErrorMessages.InvalidRequest, Some(JsArray(errors.map(m => Json.obj("message" -> m))))))

  private def requiredHeaders(request: RequestHeader): Either[Result, (String, String)] =
    (headerValue(request, "correlation-id"), headerValue(request, "subscription-id")) match
      case (Some(correlationId), Some(subscriptionId)) => Right((correlationId, subscriptionId))
      case _ => Left(singleDetailBadRequest(Json.obj("message" -> ErrorMessages.MissingRequiredHeaders)))

  private def validateRequest(
    request: Request[AnyContent]
  ): Either[Result, (String, String, SubmissionBatchPayload)] =
    // Phase 1 – fail fast: both required headers must be present.
    val (correlationId, subscriptionId) = requiredHeaders(request) match
      case Left(result)   => return Left(result)
      case Right(headers) => headers

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
      Right((correlationId, subscriptionId, payload))

  private def singleDetailBadRequest(detail: JsValue): Result =
    badRequest(ErrorMessages.InvalidTransferData, Some(JsArray(Seq(detail))))

  private def hasUniqueRecordIds(payload: SubmissionBatchPayload): Boolean =
    val recordIds = payload.transfers.map(_.recordId)
    recordIds.distinct.size == recordIds.size

  private def headerValue(request: RequestHeader, name: String): Option[String] =
    request.headers.get(name).map(_.trim).filter(_.nonEmpty)


  // `code` is the stable value for callers to match on; `error` is human-readable and may change.
  private def notFound(code: String, error: String): Result =
    NotFound(ApiErrorResponse.asJson(error) ++ Json.obj("code" -> code))

  private def badRequest(error: String, details: Option[JsValue] = None): Result =
    BadRequest(ApiErrorResponse.asJson(error = error, details = details))

  private def handleClientMappingErrors: PartialFunction[Throwable, Result] =
    case e: JsResultException =>
      badRequest(ErrorMessages.InvalidTransferData, Some(JsError.toJson(e.errors)))
    case _: IllegalArgumentException =>
      badRequest(ErrorMessages.InvalidTransferData)
