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
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.NrsTestData._

class IdentityDataSpec extends AnyWordSpec with Matchers {

  "IdentityData" should {

    "serialize and deserialize correctly with all fields populated" in {
      val json = Json.toJson(fullIdentityData)
      val result = json.validate[IdentityData]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe fullIdentityData
    }

    "serialize and deserialize correctly with minimal fields" in {
      val json = Json.toJson(minimalIdentityData)
      val result = json.validate[IdentityData]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe minimalIdentityData
      result.get.confidenceLevel shouldBe 200
    }

    "serialize and deserialize Credentials correctly" in {
      val json = Json.toJson(testCredentials)
      val result = json.validate[Credentials]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testCredentials
    }

    "serialize and deserialize Name correctly" in {
      val json = Json.toJson(testName)
      val result = json.validate[Name]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testName
    }

    "serialize and deserialize Name with only lastName" in {
      val name = Name(
        name = None,
        lastName = Some("Doe")
      )

      val json = Json.toJson(name)
      val result = json.validate[Name]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe name
    }

    "serialize and deserialize LoginTimes correctly" in {
      val json = Json.toJson(testLoginTimesWithPrevious)
      val result = json.validate[LoginTimes]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testLoginTimesWithPrevious
    }

    "serialize and deserialize LoginTimes without previousLogin" in {
      val json = Json.toJson(testLoginTimes)
      val result = json.validate[LoginTimes]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testLoginTimes
    }

    "serialize and deserialize ItmpName correctly" in {
      val json = Json.toJson(testItmpName)
      val result = json.validate[ItmpName]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testItmpName
    }

    "serialize and deserialize ItmpAddress correctly" in {
      val json = Json.toJson(fullItmpAddress)
      val result = json.validate[ItmpAddress]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe fullItmpAddress
    }

    "serialize and deserialize ItmpAddress with minimal fields" in {
      val json = Json.toJson(testItmpAddress)
      val result = json.validate[ItmpAddress]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testItmpAddress
    }

    "serialize and deserialize AgentInformation correctly" in {
      val json = Json.toJson(testAgentInformation)
      val result = json.validate[AgentInformation]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testAgentInformation
    }

    "serialize and deserialize AgentInformation with partial fields" in {
      val json = Json.toJson(testAgentInformation)
      val result = json.validate[AgentInformation]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testAgentInformation
    }

    "serialize and deserialize MdtpInformation correctly" in {
      val json = Json.toJson(testMdtpInformation)
      val result = json.validate[MdtpInformation]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testMdtpInformation
    }

    "handle IdentityData for Individual affinity group" in {
      val json = Json.toJson(testIdentityData)
      val result = json.validate[IdentityData]

      result shouldBe a[JsSuccess[_]]
      result.get.affinityGroup shouldBe Some("Individual")
      result.get.nino shouldBe Some("AB123456C")
    }

    "handle IdentityData for Agent affinity group" in {
      val json = Json.toJson(agentIdentityData)
      val result = json.validate[IdentityData]

      result shouldBe a[JsSuccess[_]]
      result.get.affinityGroup shouldBe Some("Agent")
      result.get.agentCode shouldBe Some("AGENT001")
      result.get.agentInformation shouldBe defined
    }

    "round-trip serialize and deserialize correctly" in {
      val json = Json.toJson(testIdentityData)
      val result = json.validate[IdentityData]

      result shouldBe a[JsSuccess[_]]
      result.get shouldBe testIdentityData
    }
  }
}
