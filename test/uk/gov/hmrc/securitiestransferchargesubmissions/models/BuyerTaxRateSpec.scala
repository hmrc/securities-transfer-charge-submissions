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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsNumber, JsString, Json}

class BuyerTaxRateSpec extends AnyWordSpec with Matchers:

  "BuyerTaxRate JSON format" should:
    "read known numeric values" in:
      Json.fromJson[BuyerTaxRate](JsNumber(1)).get shouldBe BuyerTaxRate.HalfPercent
      Json.fromJson[BuyerTaxRate](JsNumber(2)).get shouldBe BuyerTaxRate.OneAndHalfPercent

    "fail for unknown numeric values" in:
      Json.fromJson[BuyerTaxRate](JsNumber(3)) shouldBe a[JsError]

    "fail for non-integer numeric values" in:
      Json.fromJson[BuyerTaxRate](JsNumber(1.2)) shouldBe a[JsError]

    "fail for non-numeric values" in:
      Json.fromJson[BuyerTaxRate](JsString("1")) shouldBe a[JsError]

    "write enum values as numeric codes" in:
      Json.toJson(BuyerTaxRate.HalfPercent) shouldBe JsNumber(1)
      Json.toJson(BuyerTaxRate.OneAndHalfPercent) shouldBe JsNumber(2)
