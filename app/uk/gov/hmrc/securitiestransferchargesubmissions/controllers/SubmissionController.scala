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

import play.api.libs.json.*
import play.api.mvc.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcTransactionCreateResponse.given
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.SubmissionConnector
import uk.gov.hmrc.securitiestransferchargesubmissions.models.SubmissionBatchPayload
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.ApiErrorResponse
import uk.gov.hmrc.securitiestransferchargesubmissions.services.ErrorMessages
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.AuthorisedFunctions

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionController @Inject()(
  cc: ControllerComponents,
  submissionConnector: SubmissionConnector,
  val authConnector: AuthConnector
)(using ec: ExecutionContext) extends BackendController(cc) with AuthorisedFunctions:

  def submitBatchAction(submissionId: String): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      validateRequest(request) match
        case Left(result) => Future.successful(result)
        case Right((correlationId, subscriptionId, payload)) =>
          submissionConnector
            .submitTransfers(
              stcId = subscriptionId,
              submissionId = submissionId,
              correlationId = correlationId,
              declaration = payload.declaration,
              transfers = payload.transfers
            )
            .map(responses => Ok(Json.toJson(responses)))
            .recover(handleClientMappingErrors)
    }
  }

  private def validateRequest(
    request: Request[AnyContent]
  ): Either[Result, (String, String, SubmissionBatchPayload)] =
    // Phase 1 – fail fast: both required headers must be present.
    val correlationId  = headerValue(request, "correlation-id")
    val subscriptionId = headerValue(request, "subscription-id")
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
