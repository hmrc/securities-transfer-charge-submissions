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

class NrsAttachmentSpec extends AnyWordSpec with Matchers {

  "NrsAttachment" should {

    "serialize to JSON correctly" in {
      val expectedJson = Json.obj(
        "attachmentUrl" -> testAttachment.attachmentUrl,
        "attachmentId" -> testAttachment.attachmentId,
        "attachmentSha256Checksum" -> testAttachment.attachmentSha256Checksum,
        "attachmentContentType" -> testAttachment.attachmentContentType
      )

      Json.toJson(testAttachment) shouldBe expectedJson
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "attachmentUrl" -> testAttachment.attachmentUrl,
        "attachmentId" -> testAttachment.attachmentId,
        "attachmentSha256Checksum" -> testAttachment.attachmentSha256Checksum,
        "attachmentContentType" -> testAttachment.attachmentContentType
      )

      json.validate[NrsAttachment] shouldBe JsSuccess(testAttachment)
    }

    "handle xlsx content type" in {
      val json = Json.toJson(testAttachment)
      json.validate[NrsAttachment] shouldBe JsSuccess(testAttachment)
    }
  }
}