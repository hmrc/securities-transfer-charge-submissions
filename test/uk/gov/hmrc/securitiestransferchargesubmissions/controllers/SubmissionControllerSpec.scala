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
import play.api.libs.json.{JsArray, JsError, JsResultException, JsValue, Json}
import play.api.mvc.AnyContentAsText
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{StcChargeFailure, StcChargeSuccess}
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{BuyerTaxRate, DeclarationRole, SubmissionBatchPayload, TransferType}
import uk.gov.hmrc.securitiestransferchargesubmissions.services.ErrorMessages
import uk.gov.hmrc.securitiestransferchargesubmissions.{StubSubmissionConnector, ViewTransactionFixtures}

import java.time.LocalDate
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SubmissionControllerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  private given ActorSystem = ActorSystem("SubmissionControllerSpec")
  private given Materializer = Materializer.matFromSystem
  private val controllerComponents = Helpers.stubControllerComponents()

  private val successConnector = new StubSubmissionConnector:

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

  private val failingConnector = new StubSubmissionConnector:

    override def submitTransfers(
      subscriptionId: String,
      submissionId: String,
      correlationId: String,
      declaration: SingleTransferDeclaration,
      transfers: Seq[SingleTransferRequest]
    )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
      Future.successful(Seq(StcChargeFailure(1, "400", "bad input")))

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

  private val rejectingAuthConnector = new AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      Future.failed(new MissingBearerToken)
  }

  private def validSubmitRequest =
    FakeRequest("POST", "/submission/sub-123")
      .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
      .withJsonBody(Json.toJson(payload(Seq(singleRequest(1)))))

  private def controllerWithConnectorFailure(failure: Throwable) =
    val connector = new StubSubmissionConnector:
      override def submitTransfers(
        subscriptionId: String,
        submissionId: String,
        correlationId: String,
        declaration: SingleTransferDeclaration,
        transfers: Seq[SingleTransferRequest]
      )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
        Future.failed(failure)
    new SubmissionController(controllerComponents, connector, stubAuthConnector)

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

    "return the charges as JSON, with a processingDate" in:
      val request = FakeRequest("POST", "/submission/sub-123")
        .withHeaders("correlation-id" -> "corr-1", "subscription-id" -> "stc-123")
        .withJsonBody(Json.toJson(payload(Seq(singleRequest(1)))))

      val json = contentAsJson(controller.submitBatchAction("sub-123").apply(request))

      (json \ "success" \ "charges").as[JsArray].value shouldBe Seq(
        Json.obj(
          "recordId"              -> 1,
          "utrn"                  -> "utrn-1",
          "chargeTypeDescription" -> "Charge",
          "chargeReference"       -> "ref-1",
          "chargeType"            -> "STF",
          "chargeAmount"          -> 10,
          "chargeDueDate"         -> "2026-04-30"
        )
      )
      (json \ "success" \ "processingDate").as[String] should fullyMatch regex """\d{4}-\d{2}-\d{2}"""

    "pass the path submissionId, trimmed headers, declaration and transfers to the connector" in:
      val captured = new AtomicReference[Option[(String, String, String, SingleTransferDeclaration, Seq[SingleTransferRequest])]](None)
      val capturingConnector = new StubSubmissionConnector:
        override def submitTransfers(
          subscriptionId: String,
          submissionId: String,
          correlationId: String,
          declaration: SingleTransferDeclaration,
          transfers: Seq[SingleTransferRequest]
        )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
          captured.set(Some((subscriptionId, submissionId, correlationId, declaration, transfers)))
          Future.successful(Seq(StcChargeFailure(1, "400", "bad input")))

      val transfers = Seq(singleRequest(1), singleRequest(2))
      val request = FakeRequest("POST", "/submission/path-sub")
        .withHeaders("correlation-id" -> "  corr-1  ", "subscription-id" -> " stc-123 ")
        .withJsonBody(Json.toJson(payload(transfers)))

      val result = new SubmissionController(controllerComponents, capturingConnector, stubAuthConnector)
        .submitBatchAction("path-sub")(request)

      status(result) shouldBe CREATED
      captured.get shouldBe Some(("stc-123", "path-sub", "corr-1", declaration, transfers))

    "return 400 with the field errors when the connector fails to map a transfer" in:
      val jsError = Json.obj("recordId" -> "x").validate[SingleTransferRequest] match
        case JsError(errors) => errors
        case _               => fail("expected validation to fail")
      val result = controllerWithConnectorFailure(new JsResultException(jsError))
        .submitBatchAction("sub-123")(validSubmitRequest)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.InvalidTransferData
      (contentAsJson(result) \ "details").isDefined shouldBe true

    "return 400 without details when the connector rejects the transfers" in:
      val result = controllerWithConnectorFailure(new IllegalArgumentException("transfers must not be empty"))
        .submitBatchAction("sub-123")(validSubmitRequest)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.InvalidTransferData
      (contentAsJson(result) \ "details").isDefined shouldBe false

    "not handle unexpected connector failures" in:
      val result = controllerWithConnectorFailure(new RuntimeException("boom"))
        .submitBatchAction("sub-123")(validSubmitRequest)

      Await.result(result.failed, 5.seconds) shouldBe a[RuntimeException]

    "fail without calling the connector when the caller is not authorised" in:
      val called = new AtomicBoolean(false)
      val connector = new StubSubmissionConnector:
        override def submitTransfers(
          subscriptionId: String,
          submissionId: String,
          correlationId: String,
          declaration: SingleTransferDeclaration,
          transfers: Seq[SingleTransferRequest]
        )(using hc: HeaderCarrier): Future[Seq[SingleTransferResponse]] =
          called.set(true)
          Future.successful(Seq.empty)

      val result = new SubmissionController(controllerComponents, connector, rejectingAuthConnector)
        .submitBatchAction("sub-123")(validSubmitRequest)

      Await.result(result.failed, 5.seconds) shouldBe a[MissingBearerToken]
      called.get() shouldBe false

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


  private def viewController(response: => Future[ViewTransactionResult]) =
    val connector = new StubSubmissionConnector:
      override def viewTransaction(subscriptionId: String, utrn: String, correlationId: String)(using
        HeaderCarrier
      ) = response
    new SubmissionController(controllerComponents, connector, stubAuthConnector)

  private val viewCorrelationId = "f0bd1f32-de51-45cc-9b18-0520d6e3ab1a"

  private def viewRequestWith(headers: (String, String)*) = FakeRequest("GET", "/submission/utrn-1").withHeaders(headers*)

  private val viewRequest = viewRequestWith("correlation-id" -> viewCorrelationId, "subscription-id" -> "stc-123")

  private def viewDetailMessages(result: Future[play.api.mvc.Result]): Seq[String] =
    (contentAsJson(result) \ "details").as[JsArray].value.map(d => (d \ "message").as[String]).toSeq

  private val unreachableViewConnector = Future.failed[ViewTransactionResult](new AssertionError("ETMP must not be called"))

  "SubmissionController.viewTransactionAction" should:

    "return 200 with the transaction when it is found" in:
      val result = viewController(Future.successful(ViewTransactionResult.Found(ViewTransactionFixtures.expectedResponse)))
        .viewTransactionAction("utrn-1")(viewRequest)

      status(result) shouldBe OK
      contentAsJson(result).as[ViewTransferResponse] shouldBe ViewTransactionFixtures.expectedResponse

    "return booleans and no ETMP envelope in the 200 body" in:
      val result = viewController(Future.successful(ViewTransactionResult.Found(ViewTransactionFixtures.expectedResponse)))
        .viewTransactionAction("utrn-1")(viewRequest)

      val json = contentAsJson(result)
      (json \ "success").isDefined shouldBe false
      (json \ "transactionDetails" \ "isConnectedPartiesTransactions").as[Boolean] shouldBe false
      (json \ "declaration" \ "isCorrectInfo").as[Boolean] shouldBe true
      (json \ "contingentDetails" \ 0 \ "isAmountUnasertainable").as[Boolean] shouldBe true

    "return 404 with a UTRN message when the UTRN cannot be found" in:
      val result = viewController(Future.successful(ViewTransactionResult.UtrnNotFound)).viewTransactionAction("utrn-1")(viewRequest)

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.UtrnNotFound
      (contentAsJson(result) \ "code").as[String] shouldBe "UTRN_NOT_FOUND"

    "return 404 with an STC ID message when the STC ID does not exist" in:
      val result = viewController(Future.successful(ViewTransactionResult.StcIdNotFound)).viewTransactionAction("utrn-1")(viewRequest)

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.StcIdNotFound
      (contentAsJson(result) \ "code").as[String] shouldBe "STC_ID_NOT_FOUND"

    "return 500 when retrieving the transaction fails" in:
      val result = viewController(Future.failed(ViewTransactionFailed("boom"))).viewTransactionAction("utrn-1")(viewRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "error").as[String] shouldBe ErrorMessages.ViewTransactionFailed

    "return 500 for unexpected exceptions" in:
      val result = viewController(Future.failed(new RuntimeException("boom"))).viewTransactionAction("utrn-1")(viewRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR

    "return 400 when required headers are missing" in:
      val result = viewController(Future.successful(ViewTransactionResult.UtrnNotFound))
        .viewTransactionAction("utrn-1")(viewRequestWith("correlation-id" -> viewCorrelationId))

      status(result) shouldBe BAD_REQUEST
      val details = (contentAsJson(result) \ "details").as[JsArray]
      details.value.map(d => (d \ "message").as[String]) should contain (ErrorMessages.MissingRequiredHeaders)


    "return 400 without calling ETMP when the correlation-id is not a UUID" in:
      val result = viewController(unreachableViewConnector)
        .viewTransactionAction("utrn-1")(viewRequestWith("correlation-id" -> "corr-1", "subscription-id" -> "stc-123"))

      status(result) shouldBe BAD_REQUEST
      viewDetailMessages(result) should contain only ErrorMessages.InvalidCorrelationId

    "return 400 without calling ETMP when the STC ID is longer than 15 characters" in:
      val result = viewController(unreachableViewConnector)
        .viewTransactionAction("utrn-1")(viewRequestWith("correlation-id" -> viewCorrelationId, "subscription-id" -> "X" * 16))

      status(result) shouldBe BAD_REQUEST
      viewDetailMessages(result) should contain only ErrorMessages.InvalidStcId

    "return 400 without calling ETMP when the UTRN is longer than 12 characters" in:
      val result = viewController(unreachableViewConnector).viewTransactionAction("1234567890123")(viewRequest)

      status(result) shouldBe BAD_REQUEST
      viewDetailMessages(result) should contain only ErrorMessages.InvalidUtrn

    "report every invalid input at once" in:
      val result = viewController(unreachableViewConnector)
        .viewTransactionAction("1234567890123")(viewRequestWith("correlation-id" -> "corr-1", "subscription-id" -> "X" * 16))

      status(result) shouldBe BAD_REQUEST
      viewDetailMessages(result) should contain theSameElementsAs Seq(
        ErrorMessages.InvalidCorrelationId, ErrorMessages.InvalidStcId, ErrorMessages.InvalidUtrn
      )

    "return 400 without calling ETMP when the UTRN is blank" in:
      val result = viewController(unreachableViewConnector).viewTransactionAction("   ")(viewRequest)

      status(result) shouldBe BAD_REQUEST
      viewDetailMessages(result) should contain only ErrorMessages.InvalidUtrn

    "pass the UTRN and headers through to the connector" in:
      val captured = new AtomicReference[Option[(String, String, String)]](None)
      val connector = new StubSubmissionConnector:
        override def viewTransaction(subscriptionId: String, utrn: String, correlationId: String)(using
          HeaderCarrier
        ) =
          captured.set(Some((subscriptionId, utrn, correlationId)))
          Future.successful(ViewTransactionResult.UtrnNotFound)

      val result = new SubmissionController(controllerComponents, connector, stubAuthConnector)
        .viewTransactionAction("utrn-9")(
          viewRequestWith("correlation-id" -> s" $viewCorrelationId ", "subscription-id" -> " stc-9 ")
        )

      status(result) shouldBe NOT_FOUND
      captured.get shouldBe Some(("stc-9", "utrn-9", viewCorrelationId))

    "fail without calling the connector when the caller is not authorised" in:
      val result = new SubmissionController(controllerComponents, new StubSubmissionConnector {}, rejectingAuthConnector)
        .viewTransactionAction("utrn-1")(viewRequest)

      Await.result(result.failed, 5.seconds) shouldBe a[MissingBearerToken]

    "accept inputs at the maximum allowed lengths" in:
      val result = viewController(Future.successful(ViewTransactionResult.UtrnNotFound))
        .viewTransactionAction("1" * 12)(viewRequestWith("correlation-id" -> viewCorrelationId, "subscription-id" -> "X" * 15))

      status(result) shouldBe NOT_FOUND

  override def afterAll(): Unit =
    Await.result(summon[ActorSystem].terminate(), 5.seconds)
    super.afterAll()
