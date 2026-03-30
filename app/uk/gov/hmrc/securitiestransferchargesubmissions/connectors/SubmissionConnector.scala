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
  transformer: SubmissionTransformer
)(using ec: ExecutionContext) extends SubmissionConnector:

  override def submitTransfers(
    stcId: String,
    correlationId: String,
    transfers: Seq[StcTransactionCreateSingleRecordRequest]
  )(using hc: HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
    val batchedRequests = transformer.toRequests(transfers)
    Future
      .sequence(batchedRequests.map { case (batch, request) =>
        client
          .submitTransfer(stcId, correlationId, request)
          .map(response => transformer.toSingleRecordResponses(batch, response))
      })
      .map(_.flatten)
