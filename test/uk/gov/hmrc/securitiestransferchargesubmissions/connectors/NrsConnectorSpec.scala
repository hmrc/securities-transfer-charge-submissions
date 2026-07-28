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

package uk.gov.hmrc.securitiestransferchargesubmissions.connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import play.api.http.Status.{ACCEPTED, BAD_REQUEST, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE}
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.securitiestransferchargesubmissions.SpecBase
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.NrsTestData.*

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

class NrsConnectorSpec extends SpecBase with WireMockSupport with HttpClientV2Support with BeforeAndAfterAll {

  private given actorSystem: ActorSystem = ActorSystem("test-system")
  private given HeaderCarrier = HeaderCarrier()

  private val nrsSubmissionPath = "/nrs-orchestrator/submission"

  private def appConfigForWireMock(): AppConfig =
    appConfigWithOverrides(
      s"""
         |microservice.services.nrs.host = $wireMockHost
         |microservice.services.nrs.port = $wireMockPort
         |microservice.services.nrs.protocol = "http"
         |microservice.services.nrs.api-key = "test-api-key"
         |nrs.retries = ["100ms", "200ms", "400ms"]
         |""".stripMargin
    )

  private def connector(): NrsConnector = new NrsConnector(httpClientV2, appConfigForWireMock(), actorSystem)

  private val expectedMaxAttempts: Int = 1 + appConfigForWireMock().nrsRetryDelays.size
  private val expectedRetryAttempts: Int = appConfigForWireMock().nrsRetryDelays.size

  override def afterAll(): Unit = {
    Await.result(actorSystem.terminate(), 5.seconds)
    super.afterAll()
  }

  "NrsConnector" should {
    "successfully submit to NRS and return response on first attempt" in {
      val expectedResponse = testNrsResponse
      
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .willReturn(aResponse()
            .withStatus(ACCEPTED)
            .withBody(Json.toJson(expectedResponse).toString())
            .withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(connector().submitToNrs(testSubmission), 5.seconds)

      result shouldBe Some(expectedResponse)
      wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(nrsSubmissionPath)))
    }

    "return None when NRS returns 4xx error and NOT retry" in {
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .willReturn(aResponse().withStatus(BAD_REQUEST).withBody("Bad Request"))
      )

      val result = Await.result(connector().submitToNrs(testSubmission), 5.seconds)

      result shouldBe None
      wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(nrsSubmissionPath)))
    }

    "retry on 5xx error and eventually succeed" in {
      val expectedResponse = testNrsResponse
      
      // Fail for all retry attempts except the last one
      var scenarioState = "Started"
      for (i <- 0 until expectedRetryAttempts) {
        val nextState = if (i == expectedRetryAttempts - 1) "final-retry" else s"retry-$i"
        wireMockServer.stubFor(
          post(urlPathEqualTo(nrsSubmissionPath))
            .inScenario("retry-scenario")
            .whenScenarioStateIs(scenarioState)
            .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
            .willSetStateTo(nextState)
        )
        scenarioState = nextState
      }
      
      // Succeed on the final retry
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .inScenario("retry-scenario")
          .whenScenarioStateIs(scenarioState)
          .willReturn(aResponse()
            .withStatus(ACCEPTED)
            .withBody(Json.toJson(expectedResponse).toString())
            .withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(connector().submitToNrs(testSubmission), 10.seconds)

      result shouldBe Some(expectedResponse)
      wireMockServer.verify(expectedMaxAttempts, postRequestedFor(urlPathEqualTo(nrsSubmissionPath)))
    }

    "retry on 5xx error up to max attempts then return None" in {
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE).withBody("Service Unavailable"))
      )

      val result = Await.result(connector().submitToNrs(testSubmission), 10.seconds)

      result shouldBe None
      wireMockServer.verify(expectedMaxAttempts, postRequestedFor(urlPathEqualTo(nrsSubmissionPath)))
    }

    "retry on network exception and eventually succeed" in {
      val expectedResponse = testNrsResponse
      
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .inScenario("network-retry")
          .whenScenarioStateIs("Started")
          .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE))
          .willSetStateTo("retried")
      )
      
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .inScenario("network-retry")
          .whenScenarioStateIs("retried")
          .willReturn(aResponse()
            .withStatus(ACCEPTED)
            .withBody(Json.toJson(expectedResponse).toString())
            .withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(connector().submitToNrs(testSubmission), 10.seconds)

      result shouldBe Some(expectedResponse)
      wireMockServer.verify(2, postRequestedFor(urlPathEqualTo(nrsSubmissionPath)))
    }

    "return None when all retry attempts fail with exceptions" in {
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE))
      )

      val result = Await.result(connector().submitToNrs(testSubmission), 10.seconds)

      result shouldBe None
      wireMockServer.verify(expectedMaxAttempts, postRequestedFor(urlPathEqualTo(nrsSubmissionPath)))
    }

    "return None when response JSON is invalid" in {
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .willReturn(aResponse()
            .withStatus(ACCEPTED)
            .withBody("invalid json")
            .withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(connector().submitToNrs(testSubmission), 5.seconds)

      result shouldBe None
    }

    "not retry on UpstreamErrorResponse with 4xx status" in {
      wireMockServer.stubFor(
        post(urlPathEqualTo(nrsSubmissionPath))
          .willReturn(aResponse().withStatus(BAD_REQUEST).withBody("Bad Request"))
      )

      val result = Await.result(connector().submitToNrs(testSubmission), 5.seconds)

      result shouldBe None
      wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(nrsSubmissionPath)))
    }
  }
}
