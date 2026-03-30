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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcTransactionCreateResponse.given
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{StcTransactionCreateSingleRecordResponse, SubmissionConnector, SubmissionTransformer}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait SubmissionController:
  def submitSingleTransfer(data: TransferData)(using HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]]
  def submitMultipleTransfers(data: Seq[TransferData])(using HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]]

@Singleton
class SubmissionControllerImpl @Inject()(
  cc: ControllerComponents,
  connector: SubmissionConnector,
  transformer: SubmissionTransformer
)(using ec: ExecutionContext) extends BackendController(cc) with SubmissionController:

  // Play action endpoints — these are the methods referenced by the routes file.
  // JSON is parsed from the request body, a HeaderCarrier is derived from the
  // request headers, and the result is returned as a JSON array of charges.

  def submitSingleTransfer: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[TransferData] match
      case JsSuccess(data, _) =>
        submitSingleTransfer(data).map(charges => Ok(Json.toJson(charges)))
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj("errors" -> JsError.toJson(errors))))
  }

  def submitMultipleTransfers: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[Seq[TransferData]] match
      case JsSuccess(data, _) =>
        submitMultipleTransfers(data).map(charges => Ok(Json.toJson(charges)))
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj("errors" -> JsError.toJson(errors))))
  }

  // SubmissionController trait implementations

  override def submitSingleTransfer(data: TransferData)(using hc: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
    val request = transformer.toSingleRecordRequest(data)
    connector.submitTransfers(data.subscriptionId, correlationId, Seq(request))

  override def submitMultipleTransfers(data: Seq[TransferData])(using hc: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
    val requests = data.map(transformer.toSingleRecordRequest)
    connector.submitTransfers(
      stcId         = data.headOption.map(_.subscriptionId).getOrElse(""),
      correlationId = correlationId,
      transfers     = requests
    )

  private def correlationId: String = UUID.randomUUID().toString
