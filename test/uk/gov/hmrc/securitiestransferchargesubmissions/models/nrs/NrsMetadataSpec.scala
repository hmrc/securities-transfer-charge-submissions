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

class NrsMetadataSpec extends AnyWordSpec with Matchers {

  "NrsMetadata" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testMetadata)

      (json \ "businessId").as[String] shouldBe "stc"
      (json \ "notableEvent").as[String] shouldBe "stc-submission"
      (json \ "payloadContentType").as[String] shouldBe "application/json"
      (json \ "payloadSha256Checksum").as[String] shouldBe "abc123def456"
      (json \ "userSubmissionTimestamp").as[String] shouldBe testTimestamp
      (json \ "userAuthToken").as[String] shouldBe "Bearer token123"
      (json \ "headerData" \ "Host").as[String] shouldBe "localhost"
      (json \ "searchKeys" \ "submissionId").as[String] shouldBe "sub-123"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "businessId" -> "stc",
        "notableEvent" -> "stc-submission",
        "payloadContentType" -> "application/json",
        "payloadSha256Checksum" -> "abc123def456",
        "userSubmissionTimestamp" -> testTimestamp,
        "identityData" -> Json.toJson(testIdentityData),
        "userAuthToken" -> "Bearer token123",
        "headerData" -> Json.obj("Host" -> "localhost"),
        "searchKeys" -> Json.obj("submissionId" -> "sub-123")
      )

      val result = json.validate[NrsMetadata]
      result shouldBe a[JsSuccess[_]]

      val metadata = result.get
      metadata.businessId shouldBe "stc"
      metadata.notableEvent shouldBe "stc-submission"
      metadata.payloadContentType shouldBe "application/json"
      metadata.payloadSha256Checksum shouldBe "abc123def456"
      metadata.userSubmissionTimestamp shouldBe testTimestamp
      metadata.userAuthToken shouldBe "Bearer token123"
      metadata.headerData("Host") shouldBe "localhost"
      metadata.searchKeys("submissionId") shouldBe "sub-123"
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(testMetadata)
      val result = json.validate[NrsMetadata]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testMetadata
    }

    "handle metadata with minimal fields" in {
      val json = Json.toJson(minimalMetadata)
      val result = json.validate[NrsMetadata]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe minimalMetadata
    }

    "handle metadata with multiple header data entries" in {
      val metadataWithHeaders = testMetadata.copy(
        headerData = Map(
          "Host" -> "localhost",
          "User-Agent" -> "test-agent",
          "Content-Type" -> "application/json",
          "Authorization" -> "Bearer token"
        )
      )

      val json = Json.toJson(metadataWithHeaders)
      val result = json.validate[NrsMetadata]

      result shouldBe a[JsSuccess[_]]
      result.get.headerData should have size 4
      result.get.headerData("Content-Type") shouldBe "application/json"
    }

    "handle metadata with multiple search keys" in {
      val metadataWithSearchKeys = testMetadata.copy(
        searchKeys = Map(
          "submissionId" -> "sub-123",
          "userId" -> "user-456",
          "transactionId" -> "txn-789"
        )
      )

      val json = Json.toJson(metadataWithSearchKeys)
      val result = json.validate[NrsMetadata]

      result shouldBe a[JsSuccess[_]]
      result.get.searchKeys should have size 3
      result.get.searchKeys("transactionId") shouldBe "txn-789"
    }
  }
}
