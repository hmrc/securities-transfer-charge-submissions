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

package uk.gov.hmrc.securitiestransferchargesubmissions.services

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.StcTransactionCreateSingleRecordResponse
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.SubmissionConnector
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{TransferData, TransformationFailure}
import uk.gov.hmrc.securitiestransferchargesubmissions.validation.{TransformationValidationOutcome, TransferTransformationValidator}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


enum SubmissionOutcome:
  case Submitted(responses: Seq[StcTransactionCreateSingleRecordResponse])
  case TransformationFailed(errors: Seq[TransformationFailure])

trait SubmissionService:
  def submitSingleTransfer(data: TransferData)(using hc: HeaderCarrier): Future[SubmissionOutcome]

  /**
   * Processes a batch of transfers with all-or-nothing transformation validation.
   *
   * Contract:
   *   - If any initial transformation fails, returns [[SubmissionOutcome.TransformationFailed]] with all failures.
   *   - If initial transformation succeeds for all inputs, downstream processing must produce exactly one
   *     response per input transfer (success or synthetic failure).
   *   - Response ordering is not significant; callers correlate using `recordId`.
   */
  def submitMultipleTransfers(data: Seq[TransferData])(using hc: HeaderCarrier): Future[SubmissionOutcome]

@Singleton
class SubmissionServiceImpl @Inject()(
  connector: SubmissionConnector,
  validator: TransferTransformationValidator
)(using ec: ExecutionContext) extends SubmissionService:

  override def submitSingleTransfer(data: TransferData)(using hc: HeaderCarrier): Future[SubmissionOutcome] =
    submitMultipleTransfers(Seq(data))

  override def submitMultipleTransfers(data: Seq[TransferData])(using hc: HeaderCarrier): Future[SubmissionOutcome] =
    require(data.nonEmpty, "data must not be empty")

    validator.validate(data) match
      case TransformationValidationOutcome.Invalid(failures) =>
        Future.successful(SubmissionOutcome.TransformationFailed(failures))
      case TransformationValidationOutcome.Valid(requests) =>
        connector
          .submitTransfers(
            stcId = data.head.subscriptionId,
            correlationId = UUID.randomUUID().toString,
            transfers = requests
          )
          .map(SubmissionOutcome.Submitted.apply)
