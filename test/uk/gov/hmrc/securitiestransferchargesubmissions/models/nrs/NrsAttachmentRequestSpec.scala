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

class NrsAttachmentRequestSpec extends AnyWordSpec with Matchers {

  "NrsAttachmentRequest" should {

    "serialize to JSON correctly" in {
      val expectedJson = Json.obj(
        "businessId" -> testAttachmentRequest.businessId,
        "notableEvent" -> testAttachmentRequest.notableEvent,
        "attachmentUrl" -> testAttachmentRequest.attachmentUrl,
        "attachmentId" -> testAttachmentRequest.attachmentId,
        "attachmentSha256Checksum" -> testAttachmentRequest.attachmentSha256Checksum,
        "attachmentContentType" -> testAttachmentRequest.attachmentContentType,
        "nrSubmissionId" -> testAttachmentRequest.nrSubmissionId
      )

      Json.toJson(testAttachmentRequest) shouldBe expectedJson
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "businessId" -> testAttachmentRequest.businessId,
        "notableEvent" -> testAttachmentRequest.notableEvent,
        "attachmentUrl" -> testAttachmentRequest.attachmentUrl,
        "attachmentId" -> testAttachmentRequest.attachmentId,
        "attachmentSha256Checksum" -> testAttachmentRequest.attachmentSha256Checksum,
        "attachmentContentType" -> testAttachmentRequest.attachmentContentType,
        "nrSubmissionId" -> testAttachmentRequest.nrSubmissionId
      )

      json.validate[NrsAttachmentRequest] shouldBe JsSuccess(testAttachmentRequest)
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(testAttachmentRequest2)
      val result = json.validate[NrsAttachmentRequest]

      result shouldBe JsSuccess(testAttachmentRequest2)
    }

    "handle xlsx content type" in {
      val json = Json.toJson(testAttachmentRequest)
      json.validate[NrsAttachmentRequest] shouldBe JsSuccess(testAttachmentRequest)
    }
  }
}