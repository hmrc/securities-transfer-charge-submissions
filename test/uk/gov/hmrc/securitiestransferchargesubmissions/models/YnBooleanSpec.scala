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
import play.api.libs.json.{JsError, JsString, Json}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.YnBoolean.toYnBoolean

class YnBooleanSpec extends AnyWordSpec with Matchers:

  "YnBoolean conversions" should:
    "convert from boolean" in:
      YnBoolean.fromBoolean(true) shouldBe YnBoolean.Yes
      YnBoolean.fromBoolean(false) shouldBe YnBoolean.No

    "convert to boolean" in:
      YnBoolean.Yes.toBoolean shouldBe true
      YnBoolean.No.toBoolean shouldBe false

    "support boolean extension conversion" in:
      true.toYnBoolean shouldBe YnBoolean.Yes
      false.toYnBoolean shouldBe YnBoolean.No

  "YnBoolean JSON format" should:
    "read Y as true and N as false" in:
      Json.fromJson[YnBoolean](JsString("Y")).get shouldBe YnBoolean.Yes
      Json.fromJson[YnBoolean](JsString("N")).get shouldBe YnBoolean.No

    "fail for unsupported string values" in:
      Json.fromJson[YnBoolean](JsString("T")) shouldBe a[JsError]
      Json.fromJson[YnBoolean](JsString("F")) shouldBe a[JsError]

    "write true as Y and false as N" in:
      Json.toJson(YnBoolean.Yes) shouldBe JsString("Y")
      Json.toJson(YnBoolean.No) shouldBe JsString("N")
