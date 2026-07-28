/*
 * Copyright 2024 HM Revenue & Customs
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
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.NrsTestData.*

class NrsSubmissionSpec extends AnyWordSpec with Matchers {

  "NrsSubmission" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testSubmission)

      (json \ "payload").as[String] shouldBe encodedTestPayload
      (json \ "metadata" \ "businessId").as[String] shouldBe "stc"
      (json \ "metadata" \ "notableEvent").as[String] shouldBe "stc-submission"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "payload" -> encodedTestPayload,
        "metadata" -> Json.toJson(testMetadata)
      )

      val result = json.validate[NrsSubmission]
      result shouldBe a[JsSuccess[_]]

      val submission = result.get
      submission.payload shouldBe encodedTestPayload
      submission.metadata.businessId shouldBe "stc"
      submission.metadata.notableEvent shouldBe "stc-submission"
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(testSubmission)
      val result = json.validate[NrsSubmission]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testSubmission
    }

    "handle submission with empty payload" in {
      val submissionWithEmptyPayload = testSubmission.copy(payload = "")

      val json = Json.toJson(submissionWithEmptyPayload)
      val result = json.validate[NrsSubmission]

      result shouldBe a[JsSuccess[_]]
      result.get.payload shouldBe ""
    }

    "handle submission with minimal metadata" in {
      val json = Json.toJson(minimalSubmission)
      val result = json.validate[NrsSubmission]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe minimalSubmission
    }
  }

  "NrsSubmissionResponse" should {

    val testResponse = NrsSubmissionResponse("nrs-id-123456")

    "serialize to JSON correctly" in {
      val json = Json.toJson(testResponse)

      (json \ "nrSubmissionId").as[String] shouldBe "nrs-id-123456"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj("nrSubmissionId" -> "nrs-id-123456")

      val result = json.validate[NrsSubmissionResponse]
      result shouldBe a[JsSuccess[_]]

      val response = result.get
      response.nrSubmissionId shouldBe "nrs-id-123456"
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(testResponse)
      val result = json.validate[NrsSubmissionResponse]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testResponse
    }

    "handle empty submission ID" in {
      val emptyResponse = NrsSubmissionResponse("")

      val json = Json.toJson(emptyResponse)
      val result = json.validate[NrsSubmissionResponse]

      result shouldBe a[JsSuccess[_]]
      result.get.nrSubmissionId shouldBe ""
    }

    "handle long submission ID" in {
      val longId = "a" * 100
      val response = NrsSubmissionResponse(longId)

      val json = Json.toJson(response)
      val result = json.validate[NrsSubmissionResponse]

      result shouldBe a[JsSuccess[_]]
      result.get.nrSubmissionId shouldBe longId
    }
  }

  "NrsSingleSubmissionRequest" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(singleHtmlRequest)

      (json \ "payload").as[String] shouldBe encodedHtmlPayload
      (json \ "metadata" \ "notableEvent").as[String] shouldBe "stc-single-submission"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "payload" -> encodedHtmlPayload,
        "metadata" -> Json.toJson(singleHtmlMetadata)
      )

      val result = json.validate[NrsSingleSubmissionRequest]
      result shouldBe a[JsSuccess[_]]
      result.get.payload shouldBe encodedHtmlPayload
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(singleHtmlRequest)
      val result = json.validate[NrsSingleSubmissionRequest]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe singleHtmlRequest
    }
  }

  "NrsBulkSubmissionRequest" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(bulkXmlRequest)

      (json \ "payload").as[String] shouldBe encodedXmlPayload
      (json \ "metadata" \ "notableEvent").as[String] shouldBe "stc-bulk-submission"
      (json \ "metadata" \ "payloadContentType").as[String] shouldBe "application/xml"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "payload" -> encodedXmlPayload,
        "metadata" -> Json.toJson(bulkXmlMetadata)
      )

      val result = json.validate[NrsBulkSubmissionRequest]
      result shouldBe a[JsSuccess[_]]
      result.get.payload shouldBe encodedXmlPayload
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(bulkXmlRequest)
      val result = json.validate[NrsBulkSubmissionRequest]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe bulkXmlRequest
    }
  }
}
