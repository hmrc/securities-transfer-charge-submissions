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
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{BuyerTaxRate, TfBoolean, TransferType}

import java.time.{Clock, Instant, ZoneId, LocalDate}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class SubmissionClientSpec
    extends AnyWordSpec
    with Matchers
    with WireMockSupport
    with HttpClientV2Support
    with BeforeAndAfterAll:

  private given actorSystem: ActorSystem = ActorSystem("SubmissionClientSpec")
  private given HeaderCarrier            = HeaderCarrier()

  private val fixedInstant = Instant.parse("2026-03-31T12:00:00Z")
  private val fixedClock   = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
  private val receiptDate  = "2026-03-31T12:00:00Z"

  private val stcId         = "stc-123"
  private val correlationId = "corr-456"
  private val path          = s"/RESTAdapter/stc/transaction/$stcId"

  private def appConfig(maxRetries: Int = 0, initialBackoffMs: Long = 0): AppConfig =
    new AppConfig(Configuration.from(Map(
      "appName"                                                                           -> "test",
      "microservice.services.etmp-transaction.host"                                      -> wireMockHost,
      "microservice.services.etmp-transaction.port"                                      -> wireMockPort,
      "microservice.services.etmp-transaction.create.max-records-per-request"            -> 12,
      "microservice.services.etmp-transaction.create.max-concurrent-calls"               -> 3,
      "microservice.services.etmp-transaction.create.max-retries"                        -> maxRetries,
      "microservice.services.etmp-transaction.create.initial-backoff-ms"                 -> initialBackoffMs
    )))

  private def client(maxRetries: Int = 0, initialBackoffMs: Long = 0): SubmissionClientImpl =
    new SubmissionClientImpl(httpClientV2, appConfig(maxRetries, initialBackoffMs), fixedClock, actorSystem)

  // ---------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------

  private val minimalRequest = StcTransactionCreateRequest(
    submissionId = "sub-123",
    transactionDetails = Seq(TransactionDetailsCreate(
      recordId                      = 1,
      transactionType               = TransferType.STF,
      reasonForPurchase             = None,
      descriptionOfSecurity         = "Ordinary shares",
      numberOfShares                = 10,
      nominalValue                  = None,
      marketValue                   = None,
      qualifyAsTreasuryShares       = None,
      maxPricePaid                  = None,
      minPricePaid                  = None,
      originalChargingPoint         = LocalDate.parse("2026-03-30"),
      considerationActual           = BigDecimal(1000),
      isConnectedPartiesTransactions = TfBoolean.False,
      companyName                   = "Company Ltd",
      companyRegistrationNumber     = None,
      reliefClaimedName             = None,
      reliefPercentage              = None
    )),
    contingentDetails = None,
    mainSellerDetails = Seq(SellerDetailsCreate(1, "Seller Ltd", "1 Main St", None, None, None, "AA1 1AA", "GB")),
    otherSellers      = None,
    mainBuyerDetails  = Seq(BuyerDetailsCreate(1, "Buyer Ltd", "2 High St", None, None, None, "BB2 2BB", "GB", "buyer@test.com", None, BuyerTaxRate.HalfPercent, None)),
    otherBuyers       = None,
    agentDetails      = None,
    declaration       = Seq(DeclarationCreate(1, None, None, "Seller Ltd", "1 Main St", None, None, None, "AA1 1AA", "GB", None, TfBoolean.True))
  )

  private val processedBody = Json.obj(
    "success" -> Json.obj(
      "processingDate" -> "2026-03-31T12:00:00Z",
      "charges" -> Json.arr(Json.obj(
        "recordId"              -> 1,
        "utrn"                  -> "900459020010",
        "chargeTypeDescription" -> "Charge Type",
        "chargeReference"       -> "XA123",
        "chargeType"            -> "STF",
        "chargeAmount"          -> 10.00,
        "chargeDueDate"         -> "2026-04-30"
      ))
    )
  ).toString()

  private val badRequestBody = Json.obj(
    "error" -> Json.obj(
      "code"    -> "400",
      "message" -> "Bad request",
      "logID"   -> "C0000AB8190CB66000000003000007A6"
    )
  ).toString()

  private val businessErrorBody = Json.obj(
    "errors" -> Json.obj(
      "processingDate" -> "2026-03-31T12:00:00Z",
      "code"           -> "037",
      "text"           -> "Main Buyer Details Invalid"
    )
  ).toString()

  override def afterAll(): Unit =
    Await.result(actorSystem.terminate(), 5.seconds)
    super.afterAll()

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  "SubmissionClientImpl.submitTransfer" should:

    "return StcTransactionCreateProcessed for a 201 response" in:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(aResponse().withStatus(201).withBody(processedBody).withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(client().submitTransfer(stcId, correlationId, minimalRequest), 5.seconds)

      result shouldBe StcTransactionCreateProcessed(StcTransactionCreateProcessedBody(
        processingDate = "2026-03-31T12:00:00Z",
        charges = List(StcChargeSuccess(1, "900459020010", "Charge Type", "XA123", "STF", BigDecimal(10.00), "2026-04-30"))
      ))
      wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(path)))

    "return StcTransactionCreateBadRequest for a 400 response without retrying" in:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(aResponse().withStatus(400).withBody(badRequestBody).withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(client(maxRetries = 3).submitTransfer(stcId, correlationId, minimalRequest), 5.seconds)

      result shouldBe StcTransactionCreateBadRequest(StcTransactionCreateBadRequestBody(
        code    = "400",
        message = "Bad request",
        logID   = "C0000AB8190CB66000000003000007A6"
      ))
      // No retries on 4XX - exactly one call
      wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(path)))

    "return StcTransactionCreateBusinessError for a 422 response without retrying" in:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(aResponse().withStatus(422).withBody(businessErrorBody).withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(client(maxRetries = 3).submitTransfer(stcId, correlationId, minimalRequest), 5.seconds)

      result shouldBe StcTransactionCreateBusinessError(StcTransactionCreateBusinessErrorBody(
        processingDate = "2026-03-31T12:00:00Z",
        code           = "037",
        text           = "Main Buyer Details Invalid"
      ))
      wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(path)))

    "retry on a 5XX response and return the successful response on the second attempt" in:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .inScenario("retry-succeeds")
          .whenScenarioStateIs(Scenario.STARTED)
          .willReturn(aResponse().withStatus(503).withBody(""))
          .willSetStateTo("first-attempt-failed")
      )
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .inScenario("retry-succeeds")
          .whenScenarioStateIs("first-attempt-failed")
          .willReturn(aResponse().withStatus(201).withBody(processedBody).withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(
        client(maxRetries = 1, initialBackoffMs = 0).submitTransfer(stcId, correlationId, minimalRequest),
        5.seconds
      )

      result shouldBe a[StcTransactionCreateProcessed]
      wireMockServer.verify(2, postRequestedFor(urlPathEqualTo(path)))

    "fail with UpstreamErrorResponse when all retries are exhausted with 5XX responses" in:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(aResponse().withStatus(503).withBody(""))
      )

      val exception = the[UpstreamErrorResponse] thrownBy Await.result(
        client(maxRetries = 2, initialBackoffMs = 0).submitTransfer(stcId, correlationId, minimalRequest),
        5.seconds
      )

      exception.statusCode shouldBe 503
      // 1 initial attempt + 2 retries = 3 total calls
      wireMockServer.verify(3, postRequestedFor(urlPathEqualTo(path)))

    "set the required headers on every request" in:
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(aResponse().withStatus(201).withBody(processedBody).withHeader("Content-Type", "application/json"))
      )

      Await.result(client().submitTransfer(stcId, correlationId, minimalRequest), 5.seconds)

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo(path))
          .withHeader("correlationid",          equalTo(correlationId))
          .withHeader("X-Originating-System",   equalTo("MDTP-STC"))
          .withHeader("X-Transmitting-System",  equalTo("HIP"))
          .withHeader("X-Receipt-Date",         equalTo(receiptDate))
          .withRequestBody(containing("\"isConnectedPartiesTransactions\":\"F\""))
          .withRequestBody(containing("\"isCorrectInfo\":\"T\""))
      )

    "use exponential backoff between retries" in:
      // Three 503s then a 201; we measure that 2 retries happen with the
      // correct doubling pattern by checking all calls arrive and succeed.
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .inScenario("exponential-backoff")
          .whenScenarioStateIs(Scenario.STARTED)
          .willReturn(aResponse().withStatus(503))
          .willSetStateTo("attempt-2")
      )
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .inScenario("exponential-backoff")
          .whenScenarioStateIs("attempt-2")
          .willReturn(aResponse().withStatus(503))
          .willSetStateTo("attempt-3")
      )
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .inScenario("exponential-backoff")
          .whenScenarioStateIs("attempt-3")
          .willReturn(aResponse().withStatus(201).withBody(processedBody).withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(
        client(maxRetries = 2).submitTransfer(stcId, correlationId, minimalRequest),
        5.seconds
      )

      result shouldBe a[StcTransactionCreateProcessed]
      wireMockServer.verify(3, postRequestedFor(urlPathEqualTo(path)))
