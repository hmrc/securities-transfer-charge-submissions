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
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait SubmissionConnector:
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

  override def submitTransfers(
    stcId: String,
    correlationId: String,
    transfers: Seq[StcTransactionCreateSingleRecordRequest]
  )(using hc: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
    require(transfers.nonEmpty, "transfers must not be empty")

    val requests = transformer.toRequests(transfers)
    val maxConcurrentCalls = math.max(1, appConfig.etmpCreateMaxConcurrentCalls)

    requests
      .grouped(maxConcurrentCalls)
      .foldLeft(Future.successful(Seq.empty[Seq[StcTransactionCreateSingleRecordResponse]])) {
        (accResponsesF, requestChunk) =>
          for {
            accResponses <- accResponsesF
            chunkResponses <- Future.sequence(requestChunk.map { request =>
              client
                .submitTransfer(stcId, correlationId, request)
                .map(response => transformer.toSingleRecordResponses(request, response))
            })
          } yield accResponses ++ chunkResponses
      }
      .map(_.flatten)
