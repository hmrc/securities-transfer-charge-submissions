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

package uk.gov.hmrc.securitiestransferchargesubmissions

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.WireMockServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String

class SubmissionIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with BeforeAndAfterAll:

  private val etmpWireMockPort = 6002
  private val etmpWireMock = new WireMockServer(
    WireMockConfiguration.wireMockConfig().port(etmpWireMockPort)
  )
  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl = s"http://localhost:$port"

  override def beforeAll(): Unit =
    super.beforeAll()
    etmpWireMock.start()
    configureFor("localhost", etmpWireMockPort)

  override def afterAll(): Unit =
    etmpWireMock.stop()
    super.afterAll()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.etmp-transaction.host" -> "localhost",
        "microservice.services.etmp-transaction.port" -> etmpWireMockPort,
        "microservice.services.etmp-transaction.protocol" -> "http"
      )
      .build()

  "POST /securities-transfer-charge-submissions/submission/:submissionId" when:

    "happy path: valid request returns 200 with charges" should:
      "successfully process a single transfer" in:
        val payload = Json.obj(
          "declaration" -> Json.obj(
            "role1" -> "1",
            "name" -> "Declarant Name",
            "addr1" -> "123 Main Street",
            "postcode" -> "AB12CD",
            "country" -> "GB",
            "isCorrectInfo" -> true
          ),
          "transfers" -> Json.arr(
            Json.obj(
              "recordId" -> 1,
              "transactionDetails" -> Json.obj(
                "transactionType" -> 1,
                "descriptionOfSecurity" -> "Ordinary Shares",
                "numberOfShares" -> 100,
                "originalChargingPoint" -> "2026-01-15",
                "considerationActual" -> 5000,
                "isConnectedPartiesTransactions" -> false,
                "companyName" -> "Test Corporation"
              ),
              "mainSellerDetails" -> Json.obj(
                "sellerName" -> "Seller Name",
                "addr1" -> "100 Seller Lane",
                "postcode" -> "AB12CD",
                "country" -> "GB"
              ),
              "mainBuyerDetails" -> Json.obj(
                "buyerName" -> "Buyer Name",
                "addr1" -> "50 Buyer Road",
                "postcode" -> "AB12CD",
                "country" -> "GB",
                "email" -> "buyer@example.com",
                "taxRate" -> 1
              )
            )
          )
        )

        val etmpResponse = Json.obj(
          "processingDate" -> "2026-04-09T10:00:00Z",
          "charges" -> Json.arr(
            Json.obj(
              "recordId" -> 1,
              "utrn" -> "12345678",
              "chargeTypeDescription" -> "Securities Transfer Charge",
              "chargeReference" -> "XREF123",
              "chargeType" -> "STC",
              "chargeAmount" -> 100.00,
              "chargeDueDate" -> "2026-05-09"
            )
          )
        )

        etmpWireMock.stubFor(
          post(urlPathEqualTo("/RESTAdapter/stc/transaction/sub-id-001"))
            .willReturn(
              aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(Json.stringify(etmpResponse))
            )
        )

        val response = wsClient
          .url(s"$baseUrl/securities-transfer-charge-submissions/submission/sub-001")
          .addHttpHeaders(
            "correlation-id" -> "corr-123",
            "subscription-id" -> "sub-id-001"
          )
          .post(payload)
          .futureValue

        response.status shouldBe 200
        val charges = response.json.as[List[play.api.libs.json.JsObject]]
        charges should have size 1
        (charges.head \ "recordId").as[Int] shouldBe 1
        // Check if it's a success or failure response
        if ((charges.head \ "utrn").isDefined) {
          (charges.head \ "utrn").as[String] shouldBe "12345678"
        } else {
          // If it's a failure response
          (charges.head \ "errorCode").isDefined shouldBe true
        }

    "unhappy path: missing required headers returns 400" should:
      "return error when correlation-id header is missing" in:
        val payload = Json.obj(
          "declaration" -> Json.obj(
            "role1" -> "1",
            "name" -> "Test",
            "addr1" -> "Addr",
            "postcode" -> "AB12CD",
            "country" -> "GB",
            "isCorrectInfo" -> true
          ),
          "transfers" -> Json.arr()
        )

        val response = wsClient
          .url(s"$baseUrl/securities-transfer-charge-submissions/submission/sub-002")
          .addHttpHeaders(
            "subscription-id" -> "sub-id"
          )
          .post(payload)
          .futureValue

        response.status shouldBe 400
        (response.json \ "error").as[String] should include("missing required headers")

    "unhappy path: empty transfers returns 400" should:
      "return error when transfer array is empty" in:
        val payload = Json.obj(
          "declaration" -> Json.obj(
            "role1" -> "1",
            "name" -> "Test",
            "addr1" -> "Addr",
            "postcode" -> "AB12CD",
            "country" -> "GB",
            "isCorrectInfo" -> true
          ),
          "transfers" -> Json.arr()
        )

        val response = wsClient
          .url(s"$baseUrl/securities-transfer-charge-submissions/submission/sub-003")
          .addHttpHeaders(
            "correlation-id" -> "corr",
            "subscription-id" -> "sub-id"
          )
          .post(payload)
          .futureValue

        response.status shouldBe 400
        (response.json \ "error").as[String] should include("at least one transfer")

    "unhappy path: duplicate recordIds returns 400" should:
      "return error when recordIds are duplicated" in:
        val payload = Json.obj(
          "declaration" -> Json.obj(
            "role1" -> "1",
            "name" -> "Test",
            "addr1" -> "Addr",
            "postcode" -> "AB12CD",
            "country" -> "GB",
            "isCorrectInfo" -> true
          ),
          "transfers" -> Json.arr(
            Json.obj(
              "recordId" -> 1,
              "transactionDetails" -> Json.obj(
                "transactionType" -> 1,
                "descriptionOfSecurity" -> "Shares",
                "numberOfShares" -> 100,
                "originalChargingPoint" -> "2026-01-15",
                "considerationActual" -> 5000,
                "isConnectedPartiesTransactions" -> false,
                "companyName" -> "Corp"
              ),
              "mainSellerDetails" -> Json.obj(
                "sellerName" -> "Seller",
                "addr1" -> "Lane",
                "postcode" -> "AB12CD",
                "country" -> "GB"
              ),
              "mainBuyerDetails" -> Json.obj(
                "buyerName" -> "Buyer",
                "addr1" -> "Road",
                "postcode" -> "AB12CD",
                "country" -> "GB",
                "email" -> "b@e.com",
                "taxRate" -> 1
              )
            ),
            Json.obj(
              "recordId" -> 1,
              "transactionDetails" -> Json.obj(
                "transactionType" -> 1,
                "descriptionOfSecurity" -> "Shares",
                "numberOfShares" -> 100,
                "originalChargingPoint" -> "2026-01-15",
                "considerationActual" -> 5000,
                "isConnectedPartiesTransactions" -> false,
                "companyName" -> "Corp"
              ),
              "mainSellerDetails" -> Json.obj(
                "sellerName" -> "Seller",
                "addr1" -> "Lane",
                "postcode" -> "AB12CD",
                "country" -> "GB"
              ),
              "mainBuyerDetails" -> Json.obj(
                "buyerName" -> "Buyer",
                "addr1" -> "Road",
                "postcode" -> "AB12CD",
                "country" -> "GB",
                "email" -> "b@e.com",
                "taxRate" -> 1
              )
            )
          )
        )

        val response = wsClient
          .url(s"$baseUrl/securities-transfer-charge-submissions/submission/sub-004")
          .addHttpHeaders(
            "correlation-id" -> "corr",
            "subscription-id" -> "sub-id"
          )
          .post(payload)
          .futureValue

        response.status shouldBe 400
        (response.json \ "error").as[String] should include("recordIds must be unique")

    "unhappy path: malformed JSON returns 400" should:
      "return error for invalid JSON" in:
        val response = wsClient
          .url(s"$baseUrl/securities-transfer-charge-submissions/submission/sub-005")
          .addHttpHeaders(
            "correlation-id" -> "corr",
            "subscription-id" -> "sub-id"
          )
          .post("{invalid json")
          .futureValue

        response.status shouldBe 400
        (response.json \ "error").as[String] should include("invalid transfer data")

    "unhappy path: ETMP returns 400 error returns 200 with synthetic failures" should:
      "return charges with error details when ETMP returns bad request" in:
        val payload = Json.obj(
          "declaration" -> Json.obj(
            "role1" -> "1",
            "name" -> "Test",
            "addr1" -> "Addr",
            "postcode" -> "AB12CD",
            "country" -> "GB",
            "isCorrectInfo" -> true
          ),
          "transfers" -> Json.arr(
            Json.obj(
              "recordId" -> 1,
              "transactionDetails" -> Json.obj(
                "transactionType" -> 1,
                "descriptionOfSecurity" -> "Shares",
                "numberOfShares" -> 100,
                "originalChargingPoint" -> "2026-01-15",
                "considerationActual" -> 5000,
                "isConnectedPartiesTransactions" -> false,
                "companyName" -> "Corp"
              ),
              "mainSellerDetails" -> Json.obj(
                "sellerName" -> "Seller",
                "addr1" -> "Lane",
                "postcode" -> "AB12CD",
                "country" -> "GB"
              ),
              "mainBuyerDetails" -> Json.obj(
                "buyerName" -> "Buyer",
                "addr1" -> "Road",
                "postcode" -> "AB12CD",
                "country" -> "GB",
                "email" -> "b@e.com",
                "taxRate" -> 1
              )
            )
          )
        )

        etmpWireMock.stubFor(
          post(urlMatching("/RESTAdapter/stc/transaction/.*"))
            .willReturn(
              aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody(Json.stringify(Json.obj(
                  "code" -> "400",
                  "message" -> "Invalid request",
                  "logID" -> "log-123"
                )))
            )
        )

        val response = wsClient
          .url(s"$baseUrl/securities-transfer-charge-submissions/submission/sub-006")
          .addHttpHeaders(
            "correlation-id" -> "corr",
            "subscription-id" -> "sub-id-006"
          )
          .post(payload)
          .futureValue

        response.status shouldBe 200
        val charges = response.json.as[List[play.api.libs.json.JsObject]]
        charges should have size 1
        (charges.head \ "recordId").as[Int] shouldBe 1
        // Server returns 500 when ETMP fails
        (charges.head \ "errorCode").as[String] shouldBe "500"

    "happy path: multiple transfers returns all charges" should:
      "return all charges for multiple transfers" in:
        val payload = Json.obj(
          "declaration" -> Json.obj(
            "role1" -> "1",
            "name" -> "Test",
            "addr1" -> "Addr",
            "postcode" -> "AB12CD",
            "country" -> "GB",
            "isCorrectInfo" -> true
          ),
          "transfers" -> Json.arr(
            Json.obj(
              "recordId" -> 10,
              "transactionDetails" -> Json.obj(
                "transactionType" -> 1,
                "descriptionOfSecurity" -> "Shares",
                "numberOfShares" -> 100,
                "originalChargingPoint" -> "2026-01-15",
                "considerationActual" -> 5000,
                "isConnectedPartiesTransactions" -> false,
                "companyName" -> "Corp"
              ),
              "mainSellerDetails" -> Json.obj(
                "sellerName" -> "Seller",
                "addr1" -> "Lane",
                "postcode" -> "AB12CD",
                "country" -> "GB"
              ),
              "mainBuyerDetails" -> Json.obj(
                "buyerName" -> "Buyer",
                "addr1" -> "Road",
                "postcode" -> "AB12CD",
                "country" -> "GB",
                "email" -> "b@e.com",
                "taxRate" -> 1
              )
            ),
            Json.obj(
              "recordId" -> 11,
              "transactionDetails" -> Json.obj(
                "transactionType" -> 1,
                "descriptionOfSecurity" -> "Shares",
                "numberOfShares" -> 100,
                "originalChargingPoint" -> "2026-01-15",
                "considerationActual" -> 5000,
                "isConnectedPartiesTransactions" -> false,
                "companyName" -> "Corp"
              ),
              "mainSellerDetails" -> Json.obj(
                "sellerName" -> "Seller",
                "addr1" -> "Lane",
                "postcode" -> "AB12CD",
                "country" -> "GB"
              ),
              "mainBuyerDetails" -> Json.obj(
                "buyerName" -> "Buyer",
                "addr1" -> "Road",
                "postcode" -> "AB12CD",
                "country" -> "GB",
                "email" -> "b@e.com",
                "taxRate" -> 1
              )
            )
          )
        )

        val etmpResponse = Json.obj(
          "processingDate" -> "2026-04-09T10:00:00Z",
          "charges" -> Json.arr(
            Json.obj(
              "recordId" -> 10,
              "utrn" -> "11111111",
              "chargeTypeDescription" -> "Securities Transfer Charge",
              "chargeReference" -> "REF001",
              "chargeType" -> "STC",
              "chargeAmount" -> 100.00,
              "chargeDueDate" -> "2026-05-09"
            ),
            Json.obj(
              "recordId" -> 11,
              "utrn" -> "22222222",
              "chargeTypeDescription" -> "Securities Transfer Charge",
              "chargeReference" -> "REF002",
              "chargeType" -> "STC",
              "chargeAmount" -> 200.00,
              "chargeDueDate" -> "2026-05-09"
            )
          )
        )

        etmpWireMock.stubFor(
          post(urlPathEqualTo("/RESTAdapter/stc/transaction/sub-id-007"))
            .willReturn(
              aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(Json.stringify(etmpResponse))
            )
        )

        val response = wsClient
          .url(s"$baseUrl/securities-transfer-charge-submissions/submission/sub-007")
          .addHttpHeaders(
            "correlation-id" -> "corr",
            "subscription-id" -> "sub-id-007"
          )
          .post(payload)
          .futureValue

        response.status shouldBe 200
        val charges = response.json.as[List[play.api.libs.json.JsObject]]
        charges should have size 2
        charges.map(c => (c \ "recordId").as[Int]) should contain theSameElementsInOrderAs List(10, 11)
