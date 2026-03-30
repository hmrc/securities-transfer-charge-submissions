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

import play.api.libs.json.*

// ---------------------------------------------------------------------------
// Address models
// ---------------------------------------------------------------------------

case class Country(name: String, code: String)
object Country:
  given Format[Country] = Json.format[Country]

case class ConfirmableAddress(lines: List[String], postcode: String, country: Option[Country] = None)
object ConfirmableAddress:
  given Format[ConfirmableAddress] = Json.format[ConfirmableAddress]

case class AlfAddress(lines: List[String], postcode: String, country: Country)
object AlfAddress:
  given Format[AlfAddress] = Json.format[AlfAddress]

case class AlfConfirmedAddress(auditRef: String, id: Option[String], address: AlfAddress)
object AlfConfirmedAddress:
  given Format[AlfConfirmedAddress] = Json.format[AlfConfirmedAddress]

// ---------------------------------------------------------------------------
// Complex page models
// ---------------------------------------------------------------------------

case class DetailsOfThisTransfer(
  numberOfShares: String,
  typeOfShares: String,
  amountPaid: BigDecimal,
  marketValue: Option[BigDecimal]
)
object DetailsOfThisTransfer:
  given Format[DetailsOfThisTransfer] = Json.format[DetailsOfThisTransfer]

case class SecuritiesTarget(businessName: String, crn: Option[String])
object SecuritiesTarget:
  given Format[SecuritiesTarget] = Json.format[SecuritiesTarget]

// ---------------------------------------------------------------------------
// Enumerated page models (stored as strings in JSON)
// ---------------------------------------------------------------------------

enum HowToNotifyAboutSecuritiesTransfer(val value: String):
  case OneAtATime      extends HowToNotifyAboutSecuritiesTransfer("oneAtATime")
  case MoreThanOneAtATime extends HowToNotifyAboutSecuritiesTransfer("moreThanOneAtATime")

object HowToNotifyAboutSecuritiesTransfer:
  given Reads[HowToNotifyAboutSecuritiesTransfer] = Reads {
    case JsString(s) =>
      HowToNotifyAboutSecuritiesTransfer.values
        .find(_.value == s)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Unknown HowToNotifyAboutSecuritiesTransfer value: $s"))
    case other => JsError(s"Expected a string for HowToNotifyAboutSecuritiesTransfer, got: $other")
  }

enum TaxRate(val value: String):
  case Half       extends TaxRate("half")
  case OneAndHalf extends TaxRate("oneAndHalf")

object TaxRate:
  given Reads[TaxRate] = Reads {
    case JsString(s) =>
      TaxRate.values
        .find(_.value == s)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Unknown TaxRate value: $s"))
    case other => JsError(s"Expected a string for TaxRate, got: $other")
  }

enum WhatTypeOfSecurities(val value: String):
  case Shares extends WhatTypeOfSecurities("shares")
  case Other  extends WhatTypeOfSecurities("other")

object WhatTypeOfSecurities:
  given Reads[WhatTypeOfSecurities] = Reads {
    case JsString(s) =>
      WhatTypeOfSecurities.values
        .find(_.value == s)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Unknown WhatTypeOfSecurities value: $s"))
    case other => JsError(s"Expected a string for WhatTypeOfSecurities, got: $other")
  }
