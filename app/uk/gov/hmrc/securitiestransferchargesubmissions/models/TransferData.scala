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

package uk.gov.hmrc.securitiestransferchargesubmissions.models

import play.api.libs.json.*
import uk.gov.hmrc.auth.core.AffinityGroup

case class TransferData(
  data: JsObject
)

object TransferData:
  given Reads[TransferData] = Json.reads[TransferData]

case class TransferBatchRequest(
  transferType: TransferType,
  subscriptionId: String,
  submissionId: String,
  submitterAffinity: AffinityGroup,
  transfers: Seq[TransferData]
):
  def context: TransferBatchContext =
    TransferBatchContext(
      transferType = transferType,
      subscriptionId = subscriptionId,
      submissionId = submissionId,
      submitterAffinity = submitterAffinity
    )

object TransferBatchRequest:
  given Reads[TransferBatchRequest] = Json.reads[TransferBatchRequest]

case class TransferBatchContext(
  transferType: TransferType,
  subscriptionId: String,
  submissionId: String,
  submitterAffinity: AffinityGroup
)

enum TransferType:
  case STF, SH03, Other

object TransferType:
  given Format[TransferType] = Format(
    Reads {
      case JsNumber(n) => n.toInt match
        case 2 => JsSuccess(TransferType.SH03)
        case 3 => JsSuccess(TransferType.Other)
        case _ => JsSuccess(TransferType.STF)
      case other => JsError(s"Expected a JSON number for TransferType, got: $other")
    },
    Writes {
      case TransferType.STF   => JsNumber(1)
      case TransferType.SH03  => JsNumber(2)
      case TransferType.Other => JsNumber(3)
    }
  )
