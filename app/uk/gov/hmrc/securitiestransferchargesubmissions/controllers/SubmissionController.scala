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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionController @Inject()(
  cc: ControllerComponents,
  submissionConnector: SubmissionConnector
)(using ec: ExecutionContext) extends BackendController(cc):

  def submitBatchAction(submissionId: String): Action[AnyContent] = Action.async { implicit request =>
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

  private def validateRequest(
    request: Request[AnyContent]
  ): Either[Result, (String, String, SubmissionBatchPayload)] =
    for
      correlationId <- headerValue(request, "correlation-id").toRight(badRequest(ErrorMessages.MissingRequiredHeaders))
      subscriptionId <- headerValue(request, "subscription-id").toRight(badRequest(ErrorMessages.MissingRequiredHeaders))
      body <- request.body.asJson.toRight(badRequest(ErrorMessages.InvalidTransferData, Some(malformedJsonDetails)))
      payload <- body.validate[SubmissionBatchPayload].asEither.left.map(errors =>
        badRequest(ErrorMessages.InvalidTransferData, Some(JsError.toJson(errors)))
      )
      _ <- Either.cond(payload.transfers.nonEmpty, (), badRequest(ErrorMessages.EmptyTransferBatch))
      _ <- Either.cond(hasUniqueRecordIds(payload), (), badRequest(ErrorMessages.DuplicateRecordIds))
    yield (correlationId, subscriptionId, payload)

  private def hasUniqueRecordIds(payload: SubmissionBatchPayload): Boolean =
    val recordIds = payload.transfers.map(_.recordId)
    recordIds.distinct.size == recordIds.size

  private def headerValue(request: RequestHeader, name: String): Option[String] =
    request.headers.get(name).map(_.trim).filter(_.nonEmpty)

  private def malformedJsonDetails: JsObject =
    Json.obj("message" -> ErrorMessages.MalformedJsonBody)

  private def badRequest(error: String, details: Option[JsValue] = None): Result =
    BadRequest(ApiErrorResponse.asJson(error = error, details = details))

  private def handleClientMappingErrors: PartialFunction[Throwable, Result] =
    case e: JsResultException =>
      badRequest(ErrorMessages.InvalidTransferData, Some(JsError.toJson(e.errors)))
    case _: IllegalArgumentException =>
      badRequest(ErrorMessages.InvalidTransferData)
