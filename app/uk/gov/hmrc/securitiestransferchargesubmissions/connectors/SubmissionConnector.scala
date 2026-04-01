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

import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.SubmissionClient
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcChargeFailure
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcTransactionCreateRequest
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait SubmissionConnector:
  /**
   * Submits already-transformed records to ETMP.
   *
   * Contract:
   *   - Input `transfers` is expected to be non-empty and already validated/transformed.
   *   - Returns exactly one single-record response per input transfer.
   *   - For downstream submission failures, synthetic [[StcChargeFailure]] responses are produced so
   *     the one-response-per-input invariant is preserved.
   *   - Response ordering is not guaranteed; callers should correlate by `recordId`.
   */
  def submitTransfers(
    stcId: String,
    correlationId: String,
    transfers: Seq[StcTransactionCreateSingleRecordRequest]
  )(using HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]]

@Singleton
class SubmissionConnectorImpl @Inject()(
  client: SubmissionClient,
  transformer: SubmissionTransformer,
  appConfig: AppConfig
)(using ec: ExecutionContext) extends SubmissionConnector:

  private type SingleRecordResponses = Seq[StcTransactionCreateSingleRecordResponse]
  private type ChunkResponses = Seq[SingleRecordResponses]

  private val FailedSubmissionErrorCode = "500"
  private val FailedSubmissionErrorText = "Failed to submit transfer to ETMP"

  override def submitTransfers(
    stcId: String,
    correlationId: String,
    transfers: Seq[StcTransactionCreateSingleRecordRequest]
  )(using hc: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
    require(transfers.nonEmpty, "transfers must not be empty")

    val requests = transformer.toRequests(transfers)
    val requestChunks = requests.grouped(maxConcurrentCalls).toSeq

    submitChunks(stcId, correlationId, requestChunks)
      .map(_.flatten)

  private def maxConcurrentCalls: Int =
    math.max(1, appConfig.etmpCreateMaxConcurrentCalls)

  private def submitChunks(
    stcId: String,
    correlationId: String,
    requestChunks: Seq[Seq[StcTransactionCreateRequest]]
  )(using hc: HeaderCarrier): Future[ChunkResponses] =
    requestChunks.foldLeft(Future.successful(Seq.empty[SingleRecordResponses])) {
      (accResponsesF, requestChunk) =>
        for {
          accResponses <- accResponsesF
          chunkResponses <- submitChunk(stcId, correlationId, requestChunk)
        } yield accResponses ++ chunkResponses
    }

  private def submitChunk(
    stcId: String,
    correlationId: String,
    requestChunk: Seq[StcTransactionCreateRequest]
  )(using hc: HeaderCarrier): Future[ChunkResponses] =
    Future.sequence(requestChunk.map(submitSingleBatch(stcId, correlationId, _)))

  private def submitSingleBatch(
    stcId: String,
    correlationId: String,
    request: StcTransactionCreateRequest
  )(using hc: HeaderCarrier): Future[SingleRecordResponses] =
    client
      .submitTransfer(stcId, correlationId, request)
      .map(response => transformer.toSingleRecordResponses(request, response))
      .recover(recoverSubmissionFailure(request))

  private def recoverSubmissionFailure(
    request: StcTransactionCreateRequest
  ): PartialFunction[Throwable, SingleRecordResponses] =
    case _ => failedResponsesFor(request)

  private def failedResponsesFor(
    request: StcTransactionCreateRequest
  ): SingleRecordResponses =
    request.transactionDetails.map(td =>
      StcChargeFailure(
        recordId = td.recordId,
        errorCode = FailedSubmissionErrorCode,
        errorText = FailedSubmissionErrorText
      )
    )
