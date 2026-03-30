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
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{StcTransactionCreateSingleRecordRequest, StcTransactionCreateSingleRecordResponse, SubmissionConnector, SubmissionTransformer}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.util.Try
import scala.concurrent.{ExecutionContext, Future}

trait SubmissionController:
  def submitSingleTransfer(data: TransferData)(using HeaderCarrier: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]]
  def submitMultipleTransfers(data: Seq[TransferData])(using HeaderCarrier: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]]

@Singleton
class SubmissionControllerImpl @Inject()(
  cc: ControllerComponents,
  connector: SubmissionConnector,
  transformer: SubmissionTransformer
)(using ec: ExecutionContext) extends BackendController(cc) with SubmissionController:

  private case class TransformationError(recordId: Int, error: String)
  private object TransformationError:
    given OWrites[TransformationError] = Json.writes[TransformationError]

  private case class BatchTransformationException(errors: Seq[TransformationError])
      extends RuntimeException("invalid transfer data")

  // Play action endpoints — these are the methods referenced by the routes file.
  // JSON is parsed from the request body, a HeaderCarrier is derived from the
  // request headers, and the result is returned as a JSON array of charges.

  def submitSingleTransfer: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[TransferData] match
      case JsSuccess(data, _) =>
        submitSingleTransfer(data)
          .map(charges => Ok(Json.toJson(charges)))
          .recover(handleClientMappingErrors)
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj("errors" -> JsError.toJson(errors))))
  }

  def submitMultipleTransfers: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[Seq[TransferData]] match
      case JsSuccess(data, _) if data.nonEmpty =>
        submitMultipleTransfers(data)
          .map(charges => Ok(Json.toJson(charges)))
          .recover(handleClientMappingErrors)
      case JsSuccess(_, _) =>
        Future.successful(BadRequest(Json.obj("error" -> "at least one transfer must be provided")))
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj("errors" -> JsError.toJson(errors))))
  }

  // SubmissionController trait implementations

  override def submitSingleTransfer(data: TransferData)(using hc: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
    Future
      .fromTry(Try(transformer.toSingleRecordRequest(recordId = 1, data)))
      .flatMap(request => connector.submitTransfers(data.subscriptionId, correlationId, Seq(request)))

  override def submitMultipleTransfers(data: Seq[TransferData])(using hc: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
    require(data.nonEmpty, "data must not be empty")

    val (errors, requests) =
      data.zipWithIndex.foldLeft((Vector.empty[TransformationError], Vector.empty[StcTransactionCreateSingleRecordRequest])) {
        case ((errs, reqs), (transferData, idx)) =>
          val recordId = idx + 1
          Try(transformer.toSingleRecordRequest(recordId = recordId, transferData)).fold(
            e => (errs :+ TransformationError(recordId, Option(e.getMessage).getOrElse(e.getClass.getSimpleName)), reqs),
            req => (errs, reqs :+ req)
          )
      }

    if errors.nonEmpty then
      Future.failed(BatchTransformationException(errors))
    else
      connector.submitTransfers(
        stcId         = data.head.subscriptionId,
        correlationId = correlationId,
        transfers     = requests
      )

  private def correlationId: String = UUID.randomUUID().toString

  private def handleClientMappingErrors: PartialFunction[Throwable, Result] =
    case BatchTransformationException(errors) =>
      BadRequest(
        Json.obj(
          "error" -> "invalid transfer data",
          "details" -> Json.toJson(errors)
        )
      )
    case e: JsResultException =>
      BadRequest(
        Json.obj(
          "error" -> "invalid transfer data",
          "details" -> JsError.toJson(e.errors)
        )
      )
    case e: IllegalArgumentException =>
      BadRequest(Json.obj("error" -> e.getMessage))
