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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{StcChargeFailure, StcChargeSuccess}
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.*
import uk.gov.hmrc.securitiestransferchargesubmissions.services.ErrorMessages

import java.time.LocalDate
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SubmissionControllerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  private given ActorSystem = ActorSystem("SubmissionControllerSpec")
  private given Materializer = Materializer.matFromSystem
  private val controllerComponents = Helpers.stubControllerComponents()

  private val successConnector = new SubmissionConnector:

    override def submitTransfers(
      subscriptionId: String,
      submissionId: String,
      correlationId: String,
      declaration: SingleTransferDeclaration,
      transfers: Seq[SingleTransferRequest]
    )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
      Future.successful(Seq(
        StcChargeSuccess(1, "utrn-1", "Charge", "ref-1", "STF", BigDecimal(10), "2026-04-30")
      ))

    override def getTransferDetails(subscriptionId: String, correlationId: String, utrn: String)(
      using hc: HeaderCarrier
    ): Future[EtmpTransferDetails] = Future.failed(new NotImplementedError())

  private val failingConnector = new SubmissionConnector:

    override def submitTransfers(
      subscriptionId: String,
      submissionId: String,
      correlationId: String,
      declaration: SingleTransferDeclaration,
      transfers: Seq[SingleTransferRequest]
    )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
      Future.successful(Seq(StcChargeFailure(1, "400", "bad input")))

    override def getTransferDetails(subscriptionId: String, correlationId: String, utrn: String)(
      using hc: HeaderCarrier
    ): Future[EtmpTransferDetails] = Future.failed(new NotImplementedError())

  // Connector for the getTransferDetails tests: returns `outcome` and records the arguments it was called with.
  private class TransferDetailsConnector(outcome: Future[EtmpTransferDetails]) extends SubmissionConnector:
    @volatile var calledWith: Option[(String, String, String)] = None

    override def submitTransfers(
      subscriptionId: String,
      submissionId: String,
      correlationId: String,
      declaration: SingleTransferDeclaration,
      transfers: Seq[SingleTransferRequest]
    )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
      Future.failed(new NotImplementedError())

    override def getTransferDetails(subscriptionId: String, correlationId: String, utrn: String)(
      using hc: HeaderCarrier
    ): Future[EtmpTransferDetails] =
      calledWith = Some((subscriptionId, correlationId, utrn))
      outcome

  private def controllerReturning(outcome: Future[EtmpTransferDetails]): (SubmissionController, TransferDetailsConnector) =
    val connector = new TransferDetailsConnector(outcome)
    (new SubmissionController(controllerComponents, connector, stubAuthConnector), connector)

  private val transferDetails = StcTransactionViewSuccess(
    agentDetails      = None,
    charges           = Seq(ChargeDetailsView(BigDecimal(10), "2026-04-30", "XA123", "STF", "Charge Type")),
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
  )

  private def businessError(code: String, text: String): TransferDetailsFailure =
    TransferDetailsFailure(StcTransactionViewError(code = code, processingDate = "2026-03-31T12:00:00Z", text = text))

  private val getTransferRequest =
    FakeRequest("GET", "/transfer/900459020010")
      .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")

  private val stubAuthConnector = new AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] = Future.successful(().asInstanceOf[A])
  }

  private val controller = new SubmissionController(controllerComponents, successConnector, stubAuthConnector)
  private val controllerWithFailure = new SubmissionController(controllerComponents, failingConnector, stubAuthConnector)

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
      mainSellerDetails = Some(SingleTransferSellerDetails(
        sellerName = "Seller Ltd",
        addr1 = "seller line 1",
        addr2 = Some("seller line 2"),
        addr3 = None,
        addr4 = None,
        postcode = "ZZ11ZZ",
        country = "GB"
      )),
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
    "return 201 for a valid single-record request list" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(1)))))

      val result = controller.submitBatchAction("sub-123").apply(request)

      status(result) shouldBe CREATED
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

      status(result) shouldBe CREATED

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

      status(result) shouldBe CREATED
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

  "SubmissionController.getTransferDetails" should:

    "return 200 with the transfer details when ETMP returns them" in:
      val (ctrl, _) = controllerReturning(Future.successful(TransferDetailsSuccess(transferDetails)))

      val result = ctrl.getTransferDetails("900459020010").apply(getTransferRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(transferDetails)

    "pass the subscription id, correlation id and UTRN through to the connector" in:
      val (ctrl, connector) = controllerReturning(Future.successful(TransferDetailsSuccess(transferDetails)))

      status(ctrl.getTransferDetails("900459020010").apply(getTransferRequest)) shouldBe OK

      connector.calledWith shouldBe Some(("stc-123", "corr-1", "900459020010"))

    "return 404 when ETMP reports the UTRN was not found (code 005)" in:
      val (ctrl, _) = controllerReturning(Future.successful(businessError("005", "UTRN not found")))

      val result = ctrl.getTransferDetails("900459020010").apply(getTransferRequest)

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.TransferNotFound

    "return 500 when ETMP reports the STC ID does not exist (code 002)" in:
      val (ctrl, _) = controllerReturning(Future.successful(businessError("002", "STC_ID does not exist")))

      val result = ctrl.getTransferDetails("900459020010").apply(getTransferRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.TransferRetrievalFailed

    "return 502, not 404, when ETMP returns a 404 with no business error" in:
      val (ctrl, _) = controllerReturning(Future.failed(UpstreamErrorResponse("ETMP returned HTTP 404 for request Unknown.", 404, 404)))

      val result = ctrl.getTransferDetails("900459020010").apply(getTransferRequest)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.TransferRetrievalFailed

    "propagate other upstream failures to the error handler" in:
      val upstream = UpstreamErrorResponse("ETMP returned HTTP 503 for request Unknown.", 503, 503)
      val (ctrl, _) = controllerReturning(Future.failed(upstream))

      val thrown = the[UpstreamErrorResponse] thrownBy Await.result(
        ctrl.getTransferDetails("900459020010").apply(getTransferRequest),
        5.seconds
      )

      thrown shouldBe upstream

    "return 400 without calling ETMP when required headers are missing" in:
      val (ctrl, connector) = controllerReturning(Future.successful(TransferDetailsSuccess(transferDetails)))

      val result = ctrl.getTransferDetails("900459020010").apply(FakeRequest("GET", "/transfer/900459020010"))

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.MissingRequiredHeaders
      connector.calledWith shouldBe None

    "return 400 without calling ETMP when required headers are blank" in:
      val (ctrl, connector) = controllerReturning(Future.successful(TransferDetailsSuccess(transferDetails)))

      val result = ctrl.getTransferDetails("900459020010").apply(
        FakeRequest("GET", "/transfer/900459020010").withHeaders("correlation-id" -> "  ", "subscription-id" -> "")
      )

      status(result) shouldBe BAD_REQUEST
      connector.calledWith shouldBe None


  override def afterAll(): Unit =
    Await.result(summon[ActorSystem].terminate(), 5.seconds)
    super.afterAll()
