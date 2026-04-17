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
import uk.gov.hmrc.securitiestransferchargesubmissions.models.TfBoolean.toTfBoolean

class TfBooleanSpec extends AnyWordSpec with Matchers:

  "TfBoolean conversions" should:
    "convert from boolean" in:
      TfBoolean.fromBoolean(true) shouldBe TfBoolean.True
      TfBoolean.fromBoolean(false) shouldBe TfBoolean.False

    "convert to boolean" in:
      TfBoolean.True.toBoolean shouldBe true
      TfBoolean.False.toBoolean shouldBe false

    "support boolean extension conversion" in:
      true.toTfBoolean shouldBe TfBoolean.True
      false.toTfBoolean shouldBe TfBoolean.False

  "TfBoolean JSON format" should:
    "read T as true and F as false" in:
      Json.fromJson[TfBoolean](JsString("T")).get shouldBe TfBoolean.True
      Json.fromJson[TfBoolean](JsString("F")).get shouldBe TfBoolean.False

    "fail for unsupported string values" in:
      Json.fromJson[TfBoolean](JsString("Y")) shouldBe a[JsError]
      Json.fromJson[TfBoolean](JsString("N")) shouldBe a[JsError]

    "write true as T and false as F" in:
      Json.toJson(TfBoolean.True) shouldBe JsString("T")
      Json.toJson(TfBoolean.False) shouldBe JsString("F")
