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

package uk.gov.hmrc.securitiestransferchargesubmissions.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsError, JsValue, Json}
import play.api.mvc.AnyContentAsText
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{StcChargeFailure, StcChargeSuccess}
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{BuyerTaxRate, DeclarationRole, SubmissionBatchPayload, TransferType}
import uk.gov.hmrc.securitiestransferchargesubmissions.services.ErrorMessages

import java.time.LocalDate
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SubmissionControllerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  private given ActorSystem = ActorSystem("SubmissionControllerSpec")
  private given Materializer = Materializer.matFromSystem
  private val controllerComponents = Helpers.stubControllerComponents()

  private val successConnector = new SubmissionConnector:

    override def submitTransfers(
      stcId: String,
      submissionId: String,
      correlationId: String,
      declaration: SingleTransferDeclaration,
      transfers: Seq[SingleTransferRequest]
    )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
      Future.successful(Seq(
        StcChargeSuccess(1, "utrn-1", "Charge", "ref-1", "STF", BigDecimal(10), "2026-04-30")
      ))

  private val failingConnector = new SubmissionConnector:

    override def submitTransfers(
      stcId: String,
      submissionId: String,
      correlationId: String,
      declaration: SingleTransferDeclaration,
      transfers: Seq[SingleTransferRequest]
    )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
      Future.successful(Seq(StcChargeFailure(1, "400", "bad input")))

  private val controller = new SubmissionController(controllerComponents, successConnector)
  private val controllerWithFailure = new SubmissionController(controllerComponents, failingConnector)

  private def singleRequest(recordId: Int): SingleTransferRequest =
    SingleTransferRequest(
      recordId = recordId,
      transactionDetails = SingleTransferTransactionDetails(
        transactionType = TransferType.STF,
        reasonForPurchase = None,
        typeOfSecurity = "Ordinary shares",
        numberOfShares = 10,
        nominalValue = None,
        marketValue = Some(BigDecimal(2000)),
        qualifyAsTreasuryShares = None,
        maxPricePaid = None,
        minPricePaid = None,
        originalChargingPoint = LocalDate.parse("2026-03-30"),
        considerationActual = BigDecimal(1500),
        isConnectedPartiesTransactions = false,
        companyName = "Buyer Ltd",
        companyRegistrationNumber = Some("CRN123"),
        reliefClaimedName = None,
        reliefPercentage = None
      ),
      contingentDetails = None,
      mainSellerDetails = SingleTransferSellerDetails(
        sellerName = "Seller Ltd",
        addr1 = "seller line 1",
        addr2 = Some("seller line 2"),
        addr3 = None,
        addr4 = None,
        postcode = "ZZ11ZZ",
        country = "GB"
      ),
      otherSellers = None,
      mainBuyerDetails = SingleTransferBuyerDetails(
        buyerName = "Buyer Ltd",
        addr1 = "buyer line 1",
        addr2 = Some("buyer line 2"),
        addr3 = None,
        addr4 = None,
        postcode = "AA11AA",
        country = "GB",
        email = "buyer@test.com",
        uniqueId = None,
        taxRate = BuyerTaxRate.HalfPercent,
        isPLC = None
      ),
      otherBuyers = None,
      agentDetails = None
    )

  private val declaration = SingleTransferDeclaration(
    role1 = Some(DeclarationRole.Director),
    role2 = None,
    name = "Seller Ltd",
    addr1 = "seller line 1",
    addr2 = Some("seller line 2"),
    addr3 = None,
    addr4 = None,
    postcode = "ZZ11ZZ",
    country = "GB",
    selfDeclarationAgent = None,
    isCorrectInfo = true
  )

  private def payload(transfers: Seq[SingleTransferRequest]): SubmissionBatchPayload =
    SubmissionBatchPayload(declaration = declaration, transfers = transfers)

  "SubmissionController.submitBatchAction" should:
    "return 200 for a valid single-record request list" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(1)))))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe OK
      contentAsString(result) should include("\"recordId\":1")
      contentAsString(result) should include("\"utrn\":\"utrn-1\"")

    "return 400 when required headers are missing" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(1)))))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseJson = contentAsJson(result)
      val details = (responseJson \ "details").as[JsArray]
      details.value.length should be >= 1
      details.value.map(d => (d \ "message").as[String]) should contain (ErrorMessages.MissingRequiredHeaders)

    "return 400 when required headers are blank" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "   ", "subscription-id" -> "")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(1)))))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseJson = contentAsJson(result)
      val details = (responseJson \ "details").as[JsArray]
      details.value.length should be >= 1
      details.value.map(d => (d \ "message").as[String]) should contain (ErrorMessages.MissingRequiredHeaders)

    "return 400 for invalid JSON schema" in:
      val invalidJson = Json.obj("unexpected" -> "shape")
      val expectedDetails = invalidJson.validate[SubmissionBatchPayload] match
        case JsError(errors) => JsError.toJson(errors)
        case _ => fail("expected invalid schema to fail validation")

      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
        .withJsonBody(invalidJson)

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseJson = contentAsJson(result)
      val details = (responseJson \ "details").as[JsArray]
      details.value should contain (expectedDetails)

    "return 400 for malformed JSON" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders(
          "correlation-id" -> "corr-1",
          "subscription-id" -> "stc-123",
          "Content-Type" -> "application/json"
        )
        .withBody(AnyContentAsText("{not-valid-json"))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseJson = contentAsJson(result)
      val details = (responseJson \ "details").as[JsArray]
      details.value.length should be >= 1
      details.value.map(d => (d \ "message").as[String]) should contain (ErrorMessages.MalformedJsonBody)

    "return 400 for an empty request array" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
        .withJsonBody(Json.toJson(payload(Seq.empty)))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseJson = contentAsJson(result)
      val details = (responseJson \ "details").as[JsArray]
      details.value.length should be >= 1
      details.value.map(d => (d \ "message").as[String]) should contain (ErrorMessages.EmptyTransferBatch)

    "accept payload records without submissionId because submissionId is path-scoped" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(1)))))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe OK

    "return 400 when recordIds are duplicated" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(7), singleRequest(7)))))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseJson = contentAsJson(result)
      val details = (responseJson \ "details").as[JsArray]
      details.value.length should be >= 1
      details.value.map(d => (d \ "message").as[String]) should contain (ErrorMessages.DuplicateRecordIds)

    "return connector failures when downstream returns a failure charge" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(1)))))

      val result = controllerWithFailure.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe OK
      val responseBody = contentAsString(result)
      responseBody should include("\"recordId\":1")
      responseBody should include("\"errorCode\":\"400\"")
      responseBody should include("bad input")

    "fail fast on missing headers and not also validate the body" in:
      // Headers absent + wrong JSON shape: only the header error should appear, not schema errors.
      val request = FakeRequest("POST", "/submission/sub-123")
        .withJsonBody(Json.obj("unexpected" -> "shape"))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseJson = contentAsJson(result)
      val details = (responseJson \ "details").as[JsArray]
      details.value.length shouldBe 1
      val messages = details.value.flatMap(d => (d \ "message").asOpt[String]).toSeq
      messages should contain only ErrorMessages.MissingRequiredHeaders

    "fail fast on missing headers before checking payload constraints" in:
      // Headers absent + duplicate recordIds: only the header error should appear.
      val request = FakeRequest("POST", "/submission/sub-123")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(7), singleRequest(7)))))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseJson = contentAsJson(result)
      val details = (responseJson \ "details").as[JsArray]
      details.value.length shouldBe 1
      val messages = details.value.flatMap(d => (d \ "message").asOpt[String]).toSeq
      messages should contain only ErrorMessages.MissingRequiredHeaders


  override def afterAll(): Unit =
    Await.result(summon[ActorSystem].terminate(), 5.seconds)
    super.afterAll()
