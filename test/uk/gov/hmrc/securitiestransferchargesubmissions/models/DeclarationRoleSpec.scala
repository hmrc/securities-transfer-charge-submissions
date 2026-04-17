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

class DeclarationRoleSpec extends AnyWordSpec with Matchers:

  "DeclarationRole JSON format" should:
    "read known string values" in:
      Json.fromJson[DeclarationRole](JsString("1")).get shouldBe DeclarationRole.Director
      Json.fromJson[DeclarationRole](JsString("8")).get shouldBe DeclarationRole.UkSocietas

    "fail for unknown string values" in:
      Json.fromJson[DeclarationRole](JsString("9")) shouldBe a[JsError]

    "fail for non-string values" in:
      Json.fromJson[DeclarationRole](JsNumber(1)) shouldBe a[JsError]

    "write enum values as string codes" in:
      Json.toJson(DeclarationRole.Director) shouldBe JsString("1")
      Json.toJson(DeclarationRole.CicManager) shouldBe JsString("7")
