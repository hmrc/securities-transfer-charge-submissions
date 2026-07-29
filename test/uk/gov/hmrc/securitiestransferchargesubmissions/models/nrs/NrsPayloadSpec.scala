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

package uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, Json}
import NrsTestData._

class NrsPayloadSpec extends AnyWordSpec with Matchers {

  "NrsSingleSubmissionRequest" should {

    "serialize and deserialize correctly" in {
      val json = Json.toJson(testSingleRequest)
      val result = json.validate[NrsSingleSubmissionRequest]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testSingleRequest
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(singleHtmlRequest)
      val result = json.validate[NrsSingleSubmissionRequest]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe singleHtmlRequest
    }

    "handle empty payload content" in {
      val emptyPayloadRequest = singleRequestWith(payload = "")
      
      val json = Json.toJson(emptyPayloadRequest)
      val result = json.validate[NrsSingleSubmissionRequest]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe emptyPayloadRequest
    }
  }

  "NrsBulkSubmissionRequest" should {

    "serialize and deserialize correctly" in {
      val json = Json.toJson(testBulkRequest)
      val result = json.validate[NrsBulkSubmissionRequest]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testBulkRequest
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(bulkXmlRequest)
      val result = json.validate[NrsBulkSubmissionRequest]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe bulkXmlRequest
    }

    "handle empty payload content" in {
      val emptyPayloadRequest = bulkRequestWith(payload = "")
      
      val json = Json.toJson(emptyPayloadRequest)
      val result = json.validate[NrsBulkSubmissionRequest]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe emptyPayloadRequest
    }
  }
}
