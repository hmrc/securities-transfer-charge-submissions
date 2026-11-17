/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.securitiestransferchargesubmissions

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.*
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{SingleTransferResponse, SubmissionConnector, ViewTransactionResult}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.{SingleTransferDeclaration, SingleTransferRequest}

import scala.concurrent.Future

/** Test base classes: override only the operations a given spec exercises. */
abstract class StubSubmissionClient extends SubmissionClient:
  override def submitTransfer(subscriptionId: String, correlationId: String, request: StcTransactionCreateRequest)(using
    HeaderCarrier
  ): Future[StcTransactionCreateResponse] =
    Future.failed(new UnsupportedOperationException("submitTransfer not stubbed"))

  override def viewTransaction(subscriptionId: String, utrn: String, correlationId: String)(using
    HeaderCarrier
  ): Future[StcTransactionViewResponse] =
    Future.failed(new UnsupportedOperationException("viewTransaction not stubbed"))

abstract class StubSubmissionConnector extends SubmissionConnector:
  override def submitTransfers(
    subscriptionId: String,
    submissionId: String,
    correlationId: String,
    declaration: SingleTransferDeclaration,
    transfers: Seq[SingleTransferRequest]
  )(using HeaderCarrier): Future[Seq[SingleTransferResponse]] =
    Future.failed(new UnsupportedOperationException("submitTransfers not stubbed"))

  override def viewTransaction(subscriptionId: String, utrn: String, correlationId: String)(using
    HeaderCarrier
  ): Future[ViewTransactionResult] =
    Future.failed(new UnsupportedOperationException("viewTransaction not stubbed"))
