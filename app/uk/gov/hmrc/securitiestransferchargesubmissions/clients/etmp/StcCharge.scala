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

package uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp

import play.api.libs.json.{JsDefined, Json, Reads, Writes}

sealed trait StcCharge:
  def recordId: Int

final case class StcChargeSuccess(
   recordId: Int,
   utrn: String,
   chargeTypeDescription: String,
   chargeReference: String,
   chargeType: String,
   chargeAmount: BigDecimal,
   chargeDueDate: String
 ) extends StcCharge

final case class StcChargeFailure(
   recordId: Int,
   errorCode: String,
   errorText: String
 ) extends StcCharge

object StcCharge:
  
  given Reads[StcChargeSuccess] = Json.reads[StcChargeSuccess]
  given Reads[StcChargeFailure] = Json.reads[StcChargeFailure]

  given Reads[StcCharge] = Reads { json =>
    (json \ "errorCode", json \ "errorText") match
      case (JsDefined(_), JsDefined(_)) =>
        summon[Reads[StcChargeFailure]].reads(json)
      case _ =>
        summon[Reads[StcChargeSuccess]].reads(json)
  }

  given Writes[StcChargeSuccess] = Json.writes[StcChargeSuccess]
  given Writes[StcChargeFailure] = Json.writes[StcChargeFailure]
  given Writes[StcCharge] = Json.writes[StcCharge]