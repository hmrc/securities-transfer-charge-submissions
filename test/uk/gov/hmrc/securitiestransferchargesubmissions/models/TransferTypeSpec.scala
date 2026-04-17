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
import play.api.libs.json.{JsError, JsNumber, Json}

class TransferTypeSpec extends AnyWordSpec with Matchers:

  "TransferType JSON format" should:
    "read STF, SH03 and Other from numeric values" in:
      Json.fromJson[TransferType](JsNumber(1)).get shouldBe TransferType.STF
      Json.fromJson[TransferType](JsNumber(2)).get shouldBe TransferType.SH03
      Json.fromJson[TransferType](JsNumber(3)).get shouldBe TransferType.Other

    "fail for an unknown numeric value" in:
      val result = Json.fromJson[TransferType](JsNumber(99))

      result match
        case JsError(errors) =>
          errors.exists { case (_, validationErrors) =>
            validationErrors.exists(_.message.contains("Invalid TransferType value [99]"))
          } shouldBe true
        case _ => fail("Expected JsError for unknown transferType value")

    "fail for a non-integer numeric value" in:
      val result = Json.fromJson[TransferType](JsNumber(BigDecimal("1.5")))

      result match
        case JsError(errors) =>
          errors.exists { case (_, validationErrors) =>
            validationErrors.exists(_.message.contains("Expected an integer value"))
          } shouldBe true
        case _ => fail("Expected JsError for non-integer transferType value")

    "fail for a non-numeric value" in:
      val result = Json.fromJson[TransferType](Json.toJson("STF"))

      result match
        case JsError(errors) =>
          errors.exists { case (_, validationErrors) =>
            validationErrors.exists(_.message.contains("Expected a JSON number for TransferType"))
          } shouldBe true
        case _ => fail("Expected JsError for non-numeric transferType value")

    "write enum values as their expected numeric codes" in:
      Json.toJson(TransferType.STF) shouldBe JsNumber(1)
      Json.toJson(TransferType.SH03) shouldBe JsNumber(2)
      Json.toJson(TransferType.Other) shouldBe JsNumber(3)
