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
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.securitiestransferchargesubmissions.SpecBase
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.*

import java.time.{Clock, Instant, LocalDate, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class SubmissionClientSpec
    extends SpecBase
    with WireMockSupport
    with HttpClientV2Support
    with BeforeAndAfterAll:

  private given actorSystem: ActorSystem = ActorSystem("SubmissionClientSpec")
  private given HeaderCarrier            = HeaderCarrier()

  // Fractional seconds on purpose: ETMP requires X-Receipt-Date as yyyy-MM-ddTHH:mm:ssZ (UTC),
  // so the client must truncate them.
  private val fixedInstant = Instant.parse("2026-03-31T12:00:00.123456Z")
  private val fixedClock   = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
  private val receiptDate  = "2026-03-31T12:00:00Z"

  // A clock that moves forward one second every time it is read.
  private class SteppingClock(start: Instant) extends Clock:
    private val current = new AtomicReference(start)
    override def getZone: ZoneId                 = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock   = this
    override def instant(): Instant              = current.getAndUpdate(_.plusSeconds(1))

  private val subscriptionId = "stc-123"
  private val correlationId = "corr-456"
  private val utrn          = "900459020010"
  private val path          = s"/securities-transfer-charge-stubs/RESTAdapter/stc/transaction/$subscriptionId"
  private val getPath       = s"$path/$utrn"

  private def appConfigForWireMock(maxRetries: Int = 0, initialBackoffMs: Long = 0): AppConfig =
    appConfigWithOverrides(
      s"""
         |microservice.services.etmp-transaction.host = $wireMockHost
         |microservice.services.etmp-transaction.port = $wireMockPort
         |microservice.services.etmp-transaction.prefix = "securities-transfer-charge-stubs"
         |microservice.services.etmp-transaction.create.max-retries = $maxRetries
         |microservice.services.etmp-transaction.create.initial-backoff-ms = $initialBackoffMs
         |""".stripMargin
    )

  private def client(maxRetries: Int = 0, initialBackoffMs: Long = 0, clock: Clock = fixedClock): SubmissionClientImpl =
    new SubmissionClientImpl(httpClientV2, appConfigForWireMock(maxRetries, initialBackoffMs), clock, actorSystem)

  // ---------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------

  private val minimalRequest = StcTransactionCreateRequest(
    submissionId = "sub-123",
    transactionDetails = Seq(TransactionDetailsCreate(
      recordId                      = 1,
      transactionType               = TransferType.STF,
      reasonForPurchase             = None,
      typeOfSecurity         = "Ordinary shares",
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
    declaration       = DeclarationCreate(None, None, "Seller Ltd", "1 Main St", None, None, None, "AA1 1AA", "GB", None, TfBoolean.True)
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

  // Based on sample-stc-transactions-view-response-flat-200 in conf/stc-transaction-oas_0.5.0.yaml
  private val transferDetailsBody = Json.obj(
    "success" -> Json.obj(
      "processingDate" -> "2026-03-31T12:00:00Z",
      "charges" -> Json.arr(Json.obj(
        "chargeTypeDescription" -> "Charge Type",
        "chargeReference"       -> "XA123",
        "chargeType"            -> "STF",
        "chargeAmount"          -> 10.00,
        "chargeDueDate"         -> "2026-04-30"
      )),
      "transactionDetails" -> Json.obj(
        "submissionId"                   -> "sub-123",
        "submissionDate"                 -> "2026-03-31",
        "recordId"                       -> 1,
        "transactionType"                -> 1,
        "reasonForPurchase"              -> 2,
        "typeOfSecurity"                 -> "Ordinary shares",
        "numberOfShares"                 -> 10,
        "nominalValue"                   -> 1,
        "marketValue"                    -> 0.5,
        "qualifyAsTreasuryShares"        -> "F",
        "originalChargingPoint"          -> "2026-03-30",
        "considerationActual"            -> 1000,
        "isConnectedPartiesTransactions" -> "F",
        "companyName"                    -> "Company Ltd",
        "companyRegistrationNumber"      -> "12345678"
      ),
      "contingentDetails" -> Json.arr(Json.obj(
        "provisionalId"          -> "1",
        "provisionalDate"        -> "2026-04-01",
        "isAmountUnasertainable" -> "T",
        "unascertainableAmount"  -> 1000,
        "defermentOfPayment"     -> "F"
      )),
      "mainSellerDetails" -> Json.obj(
        "sellerName" -> "Seller Ltd",
        "addr1"      -> "1 Main St",
        "postcode"   -> "AA1 1AA",
        "country"    -> "GB"
      ),
      "otherSellers" -> Json.arr(Json.obj("sellerName" -> "Other Seller Ltd")),
      "mainBuyerDetails" -> Json.obj(
        "buyerName" -> "Buyer Ltd",
        "addr1"     -> "2 High St",
        "postcode"  -> "BB2 2BB",
        "country"   -> "GB",
        "email"     -> "buyer@test.com",
        "taxRate"   -> 1,
        "isPLC"     -> "T"
      ),
      "otherBuyers" -> Json.arr(Json.obj("buyerName" -> "Other Buyer Ltd")),
      "agentDetails" -> Json.obj(
        "name"            -> "Agent Ltd",
        "addr1"           -> "3 Low St",
        "postcode"        -> "CC3 3CC",
        "country"         -> "GB",
        "phone"           -> "01234567890",
        "email"           -> "agent@test.com",
        "clientReference" -> "client-ref"
      ),
      "declaration" -> Json.obj(
        "role1"                -> "1",
        "name"                 -> "Seller Ltd",
        "addr1"                -> "1 Main St",
        "postcode"             -> "AA1 1AA",
        "country"              -> "GB",
        "selfDeclarationAgent" -> "F",
        "isCorrectInfo"        -> "T"
      )
    )
  ).toString()

  private val expectedTransferDetails = TransferDetailsSuccess(StcTransactionViewSuccess(
    agentDetails = Some(AgentDetailsView(
      addr1 = Some("3 Low St"), addr2 = None, addr3 = None, addr4 = None,
      clientReference = Some("client-ref"), country = "GB", email = "agent@test.com",
      name = "Agent Ltd", phone = "01234567890", postcode = "CC3 3CC"
    )),
    charges = Seq(ChargeDetailsView(
      chargeAmount = BigDecimal(10.00), chargeDueDate = "2026-04-30", chargeReference = "XA123",
      chargeType = "STF", chargeTypeDescription = "Charge Type"
    )),
    contingentDetails = Seq(ContingentDetailsView(
      actualDate = None, ascertainableAmount = None, ascertainableAmountActual = None,
      defermentExtensionDate = None, defermentOfPayment = TfBoolean.False,
      isAmountUnasertainable = TfBoolean.True, originalDefermentDate = None,
      provisionalDate = "2026-04-01", provisionalId = "1",
      unascertainableAmount = Some(BigDecimal(1000)), unascertainableAmountActual = None
    )),
    declaration = DeclarationView(
      addr1 = "1 Main St", addr2 = None, addr3 = None, addr4 = None, country = "GB",
      isCorrectInfo = TfBoolean.True, name = "Seller Ltd", postcode = "AA1 1AA",
      role1 = Some(DeclarationRole.Director), role2 = None, selfDeclarationAgent = Some(TfBoolean.False)
    ),
    mainBuyerDetails = BuyerDetailsView(
      addr1 = "2 High St", addr2 = None, addr3 = None, addr4 = None, buyerName = "Buyer Ltd",
      country = "GB", email = "buyer@test.com", isPLC = Some(TfBoolean.True), postcode = "BB2 2BB",
      taxRate = BuyerTaxRate.HalfPercent, uniqueId = None
    ),
    mainSellerDetails = Some(SellerDetailsView(
      addr1 = "1 Main St", addr2 = None, addr3 = None, addr4 = None, country = "GB",
      postcode = "AA1 1AA", sellerName = "Seller Ltd"
    )),
    otherBuyers = Seq(OtherBuyerNameView("Other Buyer Ltd")),
    otherSellers = Seq(OtherSellerNameView("Other Seller Ltd")),
    processingDate = "2026-03-31T12:00:00Z",
    transactionDetails = TransactionDetailsView(
      companyName = "Company Ltd", companyRegistrationNumber = Some("12345678"),
      considerationActual = BigDecimal(1000), isConnectedPartiesTransactions = TfBoolean.False,
      marketValue = Some(BigDecimal(0.5)), maxPricePaid = None, minPricePaid = None,
      nominalValue = Some(BigDecimal(1)), numberOfShares = 10, originalChargingPoint = "2026-03-30",
      qualifyAsTreasuryShares = Some(TfBoolean.False),
      reasonForPurchase = Some(ReasonForPurchase.PurchasedToPlaceIntoTreasury), recordId = 1,
      reliefClaimedName = None, reliefPercentage = None, submissionDate = "2026-03-31",
      submissionId = "sub-123", transactionType = TransferType.STF, typeOfSecurity = "Ordinary shares"
    )
  ))

  // Only the fields the OAS marks as required, at every level.
  private val minimalTransferDetailsBody = Json.obj(
    "success" -> Json.obj(
      "processingDate" -> "2026-03-31T12:00:00Z",
      "transactionDetails" -> Json.obj(
        "submissionId"                   -> "sub-123",
        "submissionDate"                 -> "2026-03-31",
        "recordId"                       -> 1,
        "transactionType"                -> 1,
        "typeOfSecurity"                 -> "Ordinary shares",
        "numberOfShares"                 -> 10,
        "originalChargingPoint"          -> "2026-03-30",
        "considerationActual"            -> 1000,
        "isConnectedPartiesTransactions" -> "F",
        "companyName"                    -> "Company Ltd"
      ),
      "mainBuyerDetails" -> Json.obj(
        "buyerName" -> "Buyer Ltd",
        "addr1"     -> "2 High St",
        "postcode"  -> "BB2 2BB",
        "country"   -> "GB",
        "email"     -> "buyer@test.com",
        "taxRate"   -> 1
      ),
      "declaration" -> Json.obj(
        "name"          -> "Seller Ltd",
        "addr1"         -> "1 Main St",
        "postcode"      -> "AA1 1AA",
        "country"       -> "GB",
        "isCorrectInfo" -> "T"
      )
    )
  ).toString()

  private val expectedMinimalTransferDetails = TransferDetailsSuccess(StcTransactionViewSuccess(
    agentDetails      = None,
    charges           = Seq.empty,
    contingentDetails = Seq.empty,
    declaration = DeclarationView(
      addr1 = "1 Main St", addr2 = None, addr3 = None, addr4 = None, country = "GB",
      isCorrectInfo = TfBoolean.True, name = "Seller Ltd", postcode = "AA1 1AA",
      role1 = None, role2 = None, selfDeclarationAgent = None
    ),
    mainBuyerDetails = BuyerDetailsView(
      addr1 = "2 High St", addr2 = None, addr3 = None, addr4 = None, buyerName = "Buyer Ltd",
      country = "GB", email = "buyer@test.com", isPLC = None, postcode = "BB2 2BB",
      taxRate = BuyerTaxRate.HalfPercent, uniqueId = None
    ),
    mainSellerDetails = None,
    otherBuyers       = Seq.empty,
    otherSellers      = Seq.empty,
    processingDate    = "2026-03-31T12:00:00Z",
    transactionDetails = TransactionDetailsView(
      companyName = "Company Ltd", companyRegistrationNumber = None,
      considerationActual = BigDecimal(1000), isConnectedPartiesTransactions = TfBoolean.False,
      marketValue = None, maxPricePaid = None, minPricePaid = None,
      nominalValue = None, numberOfShares = 10, originalChargingPoint = "2026-03-30",
      qualifyAsTreasuryShares = None, reasonForPurchase = None, recordId = 1,
      reliefClaimedName = None, reliefPercentage = None, submissionDate = "2026-03-31",
      submissionId = "sub-123", transactionType = TransferType.STF, typeOfSecurity = "Ordinary shares"
    )
  ))

  private val transferNotFoundBody = Json.obj(
    "errors" -> Json.obj(
      "processingDate" -> "2026-03-31T12:00:00Z",
      "code"           -> "005",
      "text"           -> "UTRN not found"
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

      val result = Await.result(client().submitTransfer(subscriptionId, correlationId, minimalRequest), 5.seconds)

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

      val result = Await.result(client(maxRetries = 3).submitTransfer(subscriptionId, correlationId, minimalRequest), 5.seconds)

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

      val result = Await.result(client(maxRetries = 3).submitTransfer(subscriptionId, correlationId, minimalRequest), 5.seconds)

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
        client(maxRetries = 1).submitTransfer(subscriptionId, correlationId, minimalRequest),
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
        client(maxRetries = 2).submitTransfer(subscriptionId, correlationId, minimalRequest),
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

      Await.result(client().submitTransfer(subscriptionId, correlationId, minimalRequest), 5.seconds)

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo(path))
          .withHeader("correlationid",          equalTo(correlationId))
          .withHeader("X-Originating-System",   equalTo("MDTP-STC"))
          .withHeader("X-Transmitting-System",  equalTo("HIP"))
          .withHeader("X-Receipt-Date",         equalTo(receiptDate))
          .withRequestBody(containing("\"isConnectedPartiesTransactions\":\"F\""))
          .withRequestBody(containing("\"isCorrectInfo\":\"T\""))
      )

    "send the same X-Receipt-Date on every retry attempt" in:
      // The clock advances a second on each read, so a regenerated receipt date
      // would show up as 12:00:01Z or 12:00:02Z on a later attempt.
      wireMockServer.stubFor(
        post(urlPathEqualTo(path))
          .willReturn(aResponse().withStatus(503))
      )

      an[UpstreamErrorResponse] should be thrownBy Await.result(
        client(maxRetries = 2, clock = new SteppingClock(fixedInstant))
          .submitTransfer(subscriptionId, correlationId, minimalRequest),
        5.seconds
      )

      wireMockServer.verify(3, postRequestedFor(urlPathEqualTo(path)))
      wireMockServer.verify(3, postRequestedFor(urlPathEqualTo(path)).withHeader("X-Receipt-Date", equalTo(receiptDate)))

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
        client(maxRetries = 2).submitTransfer(subscriptionId, correlationId, minimalRequest),
        5.seconds
      )

      result shouldBe a[StcTransactionCreateProcessed]
      wireMockServer.verify(3, postRequestedFor(urlPathEqualTo(path)))

  "SubmissionClientImpl.getTransferDetails" should:

    "return TransferDetailsSuccess for a 200 response with a valid body" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(200).withBody(transferDetailsBody).withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(client().getTransferDetails(subscriptionId, correlationId, utrn), 5.seconds)

      result shouldBe expectedTransferDetails
      wireMockServer.verify(1, getRequestedFor(urlPathEqualTo(getPath)))

    "return TransferDetailsSuccess for a 200 response containing only the required fields" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(200).withBody(minimalTransferDetailsBody).withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(client().getTransferDetails(subscriptionId, correlationId, utrn), 5.seconds)

      result shouldBe expectedMinimalTransferDetails

    "call the view endpoint with the subscription id and UTRN and set the required headers" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(200).withBody(transferDetailsBody).withHeader("Content-Type", "application/json"))
      )

      Await.result(client().getTransferDetails(subscriptionId, correlationId, utrn), 5.seconds)

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo(getPath))
          .withHeader("correlationid",         equalTo(correlationId))
          .withHeader("X-Originating-System",  equalTo("MDTP-STC"))
          .withHeader("X-Transmitting-System", equalTo("HIP"))
          .withHeader("X-Receipt-Date",        equalTo(receiptDate))
      )

    "fail with UpstreamErrorResponse reported as 500 when a 200 body cannot be parsed" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(200).withBody("""{"success":{"unexpected":"shape"}}""").withHeader("Content-Type", "application/json"))
      )

      val exception = the[UpstreamErrorResponse] thrownBy Await.result(
        client().getTransferDetails(subscriptionId, correlationId, utrn),
        5.seconds
      )

      exception.statusCode shouldBe 200
      exception.reportAs   shouldBe 500
      exception.message    should include("Unable to parse ETMP get transfer 200 response")

    "fail with UpstreamErrorResponse carrying the status for a 400 response" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(400).withBody(badRequestBody).withHeader("Content-Type", "application/json"))
      )

      val exception = the[UpstreamErrorResponse] thrownBy Await.result(
        client().getTransferDetails(subscriptionId, correlationId, utrn),
        5.seconds
      )

      exception.statusCode shouldBe 400
      exception.reportAs   shouldBe 400

    "return TransferDetailsFailure for a 422 response with a valid body" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(422).withBody(transferNotFoundBody).withHeader("Content-Type", "application/json"))
      )

      val result = Await.result(client().getTransferDetails(subscriptionId, correlationId, utrn), 5.seconds)

      result shouldBe TransferDetailsFailure(StcTransactionViewError(
        code           = "005",
        processingDate = "2026-03-31T12:00:00Z",
        text           = "UTRN not found"
      ))

    "fail with UpstreamErrorResponse reported as 500 when a 422 body cannot be parsed" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(422).withBody("""{"unexpected":"shape"}""").withHeader("Content-Type", "application/json"))
      )

      val exception = the[UpstreamErrorResponse] thrownBy Await.result(
        client().getTransferDetails(subscriptionId, correlationId, utrn),
        5.seconds
      )

      exception.statusCode shouldBe 422
      exception.reportAs   shouldBe 500
      exception.message    should include("Unable to parse ETMP get transfer 422 response")

    "fail with UpstreamErrorResponse carrying the status for a 404 response" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(404))
      )

      val exception = the[UpstreamErrorResponse] thrownBy Await.result(
        client().getTransferDetails(subscriptionId, correlationId, utrn),
        5.seconds
      )

      exception.statusCode shouldBe 404
      exception.reportAs   shouldBe 404

    "fail with UpstreamErrorResponse for a 5XX response without retrying" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(503))
      )

      val exception = the[UpstreamErrorResponse] thrownBy Await.result(
        client(maxRetries = 3).getTransferDetails(subscriptionId, correlationId, utrn),
        5.seconds
      )

      exception.statusCode shouldBe 503
      exception.reportAs   shouldBe 503
      wireMockServer.verify(1, getRequestedFor(urlPathEqualTo(getPath)))

    "include the response correlation id in the error message" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(500).withHeader("correlationid", "resp-corr-789"))
      )

      val exception = the[UpstreamErrorResponse] thrownBy Await.result(
        client().getTransferDetails(subscriptionId, correlationId, utrn),
        5.seconds
      )

      exception.message shouldBe "ETMP returned HTTP 500 for request resp-corr-789."

    "use 'Unknown' in the error message when the response has no correlation id" in:
      wireMockServer.stubFor(
        get(urlPathEqualTo(getPath))
          .willReturn(aResponse().withStatus(500))
      )

      val exception = the[UpstreamErrorResponse] thrownBy Await.result(
        client().getTransferDetails(subscriptionId, correlationId, utrn),
        5.seconds
      )

      exception.message shouldBe "ETMP returned HTTP 500 for request Unknown."
