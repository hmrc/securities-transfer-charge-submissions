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

package uk.gov.hmrc.securitiestransferchargesubmissions.validation

import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{StcTransactionCreateSingleRecordRequest, SubmissionTransformer}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{TransferItem, TransformationFailure}
import uk.gov.hmrc.securitiestransferchargesubmissions.services.ErrorMessages

import javax.inject.{Inject, Singleton}
import scala.util.Try

enum TransformationValidationOutcome:
  case Valid(requests: Seq[StcTransactionCreateSingleRecordRequest])
  case Invalid(errors: Seq[TransformationFailure])

trait TransferTransformationValidator:
  def validate(data: Seq[TransferItem]): TransformationValidationOutcome

@Singleton
class TransferTransformationValidatorImpl @Inject()(transformer: SubmissionTransformer) extends TransferTransformationValidator:

  override def validate(data: Seq[TransferItem]): TransformationValidationOutcome =
    val subscriptionIdFailures =
      data.headOption.toSeq.flatMap { head =>
        data.zipWithIndex.collect {
          case (transferData, idx) if transferData.subscriptionId != head.subscriptionId =>
            TransformationFailure(
              recordId = idx + 1,
              requestIndex = idx,
              errorCode = ErrorMessages.InvalidRequestCode,
              errorText = ErrorMessages.MixedSubscriptionIds
            )
        }
      }

    val transformationResults =
      data.zipWithIndex.map { case (transferData, idx) =>
        val recordId = idx + 1
        Try(transformer.toSingleRecordRequest(recordId = recordId, transferData))
          .fold(
            error =>
              Left(
                TransformationFailure(
                  recordId = recordId,
                  requestIndex = idx,
                  errorCode = ErrorMessages.InvalidRequestCode,
                  errorText = Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
                )
              ),
            req => Right(req)
          )
      }

    val (transformationFailures, requests) = transformationResults.partitionMap(identity)
    val failures = subscriptionIdFailures ++ transformationFailures

    if failures.nonEmpty then TransformationValidationOutcome.Invalid(failures)
    else TransformationValidationOutcome.Valid(requests)
