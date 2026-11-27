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

package uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp

import com.github.tomakehurst.wiremock.client.WireMock.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.securitiestransferchargesubmissions.SpecBase
import uk.gov.hmrc.securitiestransferchargesubmissions.models.Relief

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global

class ReliefClientSpec extends SpecBase with WireMockSupport with HttpClientV2Support:

  private given HeaderCarrier = HeaderCarrier()

  private val fixedClock = Clock.fixed(Instant.parse("2026-03-31T12:00:00Z"), ZoneId.of("UTC"))
  private val path       = "/securities-transfer-charge-stubs/RESTAdapter/stc/reliefs"

  private def client: ReliefClientImpl =
    new ReliefClientImpl(
      httpClientV2,
      appConfigWithOverrides(
        s"""
           |microservice.services.etmp-transaction.host = $wireMockHost
           |microservice.services.etmp-transaction.port = $wireMockPort
           |""".stripMargin
      ),
      fixedClock
    )

  "ReliefClientImpl.getReliefs" should {

    "return the reliefs from the ETMP response" in {
      stubFor(
        get(urlEqualTo(path)).willReturn(
          okJson(
            """{"success": {"processingDate": "2026-03-31T12:00:00Z", "reliefs": [
              |  {"reliefName": "Charities Relief", "reliefPercentage": 50},
              |  {"reliefName": "Demutualisation of Insurance Companies", "reliefPercentage": 100}
              |]}}""".stripMargin
          )
        )
      )

      client.getReliefs().futureValue shouldBe List(
        Relief("Charities Relief", 50),
        Relief("Demutualisation of Insurance Companies", 100)
      )
    }

    "send the ETMP headers" in {
      stubFor(get(urlEqualTo(path)).willReturn(okJson("""{"success": {"reliefs": []}}""")))

      client.getReliefs().futureValue shouldBe Nil

      verify(
        getRequestedFor(urlEqualTo(path))
          .withHeader("X-Originating-System", equalTo("MDTP-STC"))
          .withHeader("X-Transmitting-System", equalTo("HIP"))
          .withHeader("X-Receipt-Date", equalTo("2026-03-31T12:00:00Z"))
          .withHeader("correlationid", matching(".+"))
      )
    }

    "fail when ETMP returns a non-200 status" in {
      stubFor(get(urlEqualTo(path)).willReturn(serverError()))

      client.getReliefs().failed.futureValue shouldBe a[ReliefClientException]
    }

    "fail when the response body is not in the expected shape" in {
      stubFor(get(urlEqualTo(path)).willReturn(okJson("""{"success": {"reliefs": [{"reliefName": "No percentage"}]}}""")))

      client.getReliefs().failed.futureValue shouldBe a[ReliefClientException]
    }
  }
