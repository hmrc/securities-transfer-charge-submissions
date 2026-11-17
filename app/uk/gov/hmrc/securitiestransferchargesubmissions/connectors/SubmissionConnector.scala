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

package uk.gov.hmrc.securitiestransferchargesubmissions.connectors

import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{SubmissionClient, StcChargeFailure, StcTransactionCreateRequest, StcTransactionViewBadRequest, StcTransactionViewBusinessError, StcTransactionViewFound}
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.{SingleTransferDeclaration, SingleTransferRequest, ViewTransferResponse}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait SubmissionConnector:
  /**
   * Submits already-transformed records to ETMP.
   *
   * Contract:
   *   - Input `transfers` is expected to be non-empty and already validated/transformed.
   *   - Returns at least one single-transfer response per input transfer.
   *   - Successful submissions may produce more than one response for an input transfer when ETMP
   *     returns multiple charges for the same `recordId`.
   *   - For downstream submission failures, synthetic [[StcChargeFailure]] responses are produced so
   *     that at least one response is returned for each input transfer.
   *   - Response ordering is not guaranteed; callers should correlate by `recordId`.
   */
  def submitTransfers(
    subscriptionId: String,
    submissionId: String,
    correlationId: String,
    declaration: SingleTransferDeclaration,
    transfers: Seq[SingleTransferRequest]
  )(using HeaderCarrier): Future[Seq[SingleTransferResponse]]

  /**
   * Retrieves a previously submitted transaction from ETMP.
   *
   * Contract:
   *   - [[ViewTransactionResult.Found]] when ETMP returns the transaction.
   *   - [[ViewTransactionResult.StcIdNotFound]] when the STC ID does not exist (422, code 002).
   *   - [[ViewTransactionResult.UtrnNotFound]] when the UTRN cannot be found (422, code 005).
   *   - A failed future for any other outcome (400, other 422 codes, 5xx, unparseable responses).
   */
  def viewTransaction(
    subscriptionId: String,
    utrn: String,
    correlationId: String
  )(using HeaderCarrier): Future[ViewTransactionResult]

@Singleton
class SubmissionConnectorImpl @Inject()(
  client: SubmissionClient,
  transformer: SubmissionTransformer,
  appConfig: AppConfig
)(using ec: ExecutionContext) extends SubmissionConnector:

  private type SingleTransferResponses = Seq[SingleTransferResponse]
  private type ChunkResponses = Seq[SingleTransferResponses]

  private val FailedSubmissionErrorCode = "500"
  private val FailedSubmissionErrorText = "Failed to submit transfer to ETMP"
  private val StcIdNotFoundErrorCode = "002"
  private val UtrnNotFoundErrorCode = "005"

  override def submitTransfers(
    subscriptionId: String,
    submissionId: String,
    correlationId: String,
    declaration: SingleTransferDeclaration,
    transfers: Seq[SingleTransferRequest]
  )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
    submitTransfersInternal(subscriptionId, submissionId, correlationId, declaration, transfers)

  override def viewTransaction(
    subscriptionId: String,
    utrn: String,
    correlationId: String
  )(using hc: HeaderCarrier): Future[ViewTransactionResult] =
    client.viewTransaction(subscriptionId, utrn, correlationId).flatMap {
      case StcTransactionViewFound(body) =>
        Future.successful(ViewTransactionResult.Found(transformer.toViewTransferResponse(body)))
      case StcTransactionViewBusinessError(errors) if errors.code == StcIdNotFoundErrorCode =>
        Future.successful(ViewTransactionResult.StcIdNotFound)
      case StcTransactionViewBusinessError(errors) if errors.code == UtrnNotFoundErrorCode =>
        Future.successful(ViewTransactionResult.UtrnNotFound)
      case StcTransactionViewBusinessError(errors) =>
        Future.failed(ViewTransactionFailed(s"ETMP view business error ${errors.code}: ${errors.text}"))
      case StcTransactionViewBadRequest(error) =>
        Future.failed(ViewTransactionFailed(s"ETMP view bad request: ${error.message} (logID ${error.logID})"))
    }

  private def submitTransfersInternal(
    subscriptionId: String,
    submissionId: String,
    correlationId: String,
    declaration: SingleTransferDeclaration,
    transfers: Seq[SingleTransferRequest]
  )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
    require(transfers.nonEmpty, "transfers must not be empty")

    val requests = transformer.toRequests(transfers, declaration, submissionId)
    val requestChunks = requests.grouped(maxConcurrentCalls).toSeq

    submitChunks(subscriptionId, correlationId, requestChunks)
      .map(_.flatten)

  private def maxConcurrentCalls: Int =
    math.max(1, appConfig.etmpCreateMaxConcurrentCalls)

  private def submitChunks(
    subscriptionId: String,
    correlationId: String,
    requestChunks: Seq[Seq[StcTransactionCreateRequest]]
  )(using hc: HeaderCarrier): Future[ChunkResponses] =
    requestChunks.foldLeft(Future.successful(Seq.empty[SingleTransferResponses])) {
      (accResponsesF, requestChunk) =>
        for {
          accResponses <- accResponsesF
          chunkResponses <- submitChunk(subscriptionId, correlationId, requestChunk)
        } yield accResponses ++ chunkResponses
    }

  private def submitChunk(
    subscriptionId: String,
    correlationId: String,
    requestChunk: Seq[StcTransactionCreateRequest]
  )(using hc: HeaderCarrier): Future[ChunkResponses] =
    Future.sequence(requestChunk.map(submitSingleBatch(subscriptionId, correlationId, _)))

  private def submitSingleBatch(
    subscriptionId: String,
    correlationId: String,
    request: StcTransactionCreateRequest
  )(using hc: HeaderCarrier): Future[SingleTransferResponses] =
    client
      .submitTransfer(subscriptionId, correlationId, request)
      .map(response => transformer.toSingleTransferResponses(request, response))
      .recover(recoverSubmissionFailure(request))

  private def recoverSubmissionFailure(
    request: StcTransactionCreateRequest
  ): PartialFunction[Throwable, SingleTransferResponses] =
    case _ => failedResponsesFor(request)

  private def failedResponsesFor(
    request: StcTransactionCreateRequest
  ): SingleTransferResponses =
    request.transactionDetails.map(td =>
      StcChargeFailure(
        recordId = td.recordId,
        errorCode = FailedSubmissionErrorCode,
        errorText = FailedSubmissionErrorText
      )
    )

enum ViewTransactionResult:
  case Found(transaction: ViewTransferResponse)
  case StcIdNotFound
  case UtrnNotFound

final case class ViewTransactionFailed(message: String) extends RuntimeException(message)
