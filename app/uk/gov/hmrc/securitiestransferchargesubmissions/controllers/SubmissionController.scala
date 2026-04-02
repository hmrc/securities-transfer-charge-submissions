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
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{TransferBatchRequest, TransferItem}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.ApiErrorResponse
import uk.gov.hmrc.securitiestransferchargesubmissions.services.{ErrorMessages, SubmissionOutcome, SubmissionService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionController @Inject()(
  cc: ControllerComponents,
  submissionService: SubmissionService
)(using ec: ExecutionContext) extends BackendController(cc):


  // Play action endpoints — these are the methods referenced by the routes file.
  // JSON is parsed from the request body, a HeaderCarrier is derived from the
  // request headers, and the result is returned as a JSON array of charges.

  def submitBatchAction: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[TransferBatchRequest] match
      case JsSuccess(data, _) if data.transfers.nonEmpty =>
        val items: Seq[TransferItem] = data.transfers.map { transfer =>
          TransferItem(
            transferType = data.transferType,
            subscriptionId = data.subscriptionId,
            submissionId = data.submissionId,
            submitterAffinity = data.submitterAffinity,
            data = transfer.data
          )
        }

        submissionService
          .submitMultipleTransfers(items)
          .map(toHttpResult)
          .recover(handleClientMappingErrors)
      case JsSuccess(_, _) =>
        Future.successful(badRequest(ErrorMessages.EmptyTransferBatch))
      case JsError(errors) =>
        Future.successful(badRequest(ErrorMessages.InvalidTransferData, Some(JsError.toJson(errors))))
  }

  private def toHttpResult(outcome: SubmissionOutcome): Result =
    outcome match
      case SubmissionOutcome.Submitted(responses) =>
        Ok(Json.toJson(responses))
      case SubmissionOutcome.TransformationFailed(errors) =>
        badRequest(ErrorMessages.InvalidTransferData, Some(Json.toJson(errors)))

  private def badRequest(error: String, details: Option[JsValue] = None): Result =
    BadRequest(Json.toJson(ApiErrorResponse(error = error, details = details)))

  private def handleClientMappingErrors: PartialFunction[Throwable, Result] =
    case e: JsResultException =>
      badRequest(ErrorMessages.InvalidTransferData, Some(JsError.toJson(e.errors)))
    case e: IllegalArgumentException =>
      badRequest(e.getMessage)
