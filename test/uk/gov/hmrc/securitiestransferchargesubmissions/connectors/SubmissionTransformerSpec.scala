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

package uk.gov.hmrc.securitiestransferchargesubmissions.connectors

import uk.gov.hmrc.securitiestransferchargesubmissions.{SpecBase, ViewTransactionFixtures}
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{BuyerTaxRate, DeclarationRole, ReasonForPurchase, TfBoolean, TransferType}

import java.time.LocalDate

class SubmissionTransformerSpec extends SpecBase:

  private val transformer = new SubmissionTransformer(appConfig)

  private val appConfigMax3 = appConfigWithOverrides(
    """
      |microservice.services.etmp-transaction.create.max-records-per-request = 3
      |microservice.services.etmp-transaction.create.max-concurrent-calls = 3
      |""".stripMargin
  )

  private val transformerMax3 = new SubmissionTransformer(appConfigMax3)

  private val submissionId = "submission-123"

  private val declaration = SingleTransferDeclaration(
    role1 = Some(DeclarationRole.Director),
    role2 = None,
    name = "Declarer",
    addr1 = "addr1",
    addr2 = None,
    addr3 = None,
    addr4 = None,
    postcode = "AA11AA",
    country = "GB",
    selfDeclarationAgent = None,
    isCorrectInfo = true
  )

  private def singleRecordRequest(recordId: Int): SingleTransferRequest =
    SingleTransferRequest(
      recordId = recordId,
      transactionDetails = SingleTransferTransactionDetails(
        transactionType = TransferType.STF,
        reasonForPurchase = None,
        typeOfSecurity = s"security-$recordId",
        numberOfShares = 1,
        nominalValue = None,
        marketValue = None,
        qualifyAsTreasuryShares = None,
        maxPricePaid = None,
        minPricePaid = None,
        originalChargingPoint = LocalDate.parse("2026-03-30"),
        considerationActual = BigDecimal(100),
        isConnectedPartiesTransactions = false,
        companyName = "company",
        companyRegistrationNumber = None,
        reliefClaimedName = None,
        reliefPercentage = None
      ),
      contingentDetails = None,
      mainSellerDetails = Some(SingleTransferSellerDetails("seller", "addr1", None, None, None, "AA11AA", "GB")),
      otherSellers = None,
      mainBuyerDetails = SingleTransferBuyerDetails("buyer", "addr1", None, None, None, "AA11AA", "GB", "buyer@test.com", None, BuyerTaxRate.HalfPercent, None),
      otherBuyers = None,
      agentDetails = None
    )

  private def requestWithRecordIds(recordIds: Int*): StcTransactionCreateRequest =
    StcTransactionCreateRequest(
      submissionId = "submission-123",
      transactionDetails = recordIds.map { recordId =>
        TransactionDetailsCreate(
          recordId = recordId,
          transactionType = TransferType.STF,
          reasonForPurchase = None,
          typeOfSecurity = s"security-$recordId",
          numberOfShares = 1,
          nominalValue = None,
          marketValue = None,
          qualifyAsTreasuryShares = None,
          maxPricePaid = None,
          minPricePaid = None,
          originalChargingPoint = LocalDate.parse("2026-03-30"),
          considerationActual = BigDecimal(100),
          isConnectedPartiesTransactions = TfBoolean.False,
          companyName = "company",
          companyRegistrationNumber = None,
          reliefClaimedName = None,
          reliefPercentage = None
        )
      },
      contingentDetails = None,
      mainSellerDetails = recordIds.map { recordId =>
        SellerDetailsCreate(recordId, s"seller-$recordId", "addr1", None, None, None, "AA11AA", "GB")
      },
      otherSellers = None,
      mainBuyerDetails = recordIds.map { recordId =>
        BuyerDetailsCreate(recordId, s"buyer-$recordId", "addr1", None, None, None, "AA11AA", "GB", "a@test.com", None, BuyerTaxRate.HalfPercent, None)
      },
      otherBuyers = None,
      agentDetails = None,
      declaration = DeclarationCreate(
        None, None, "name", "addr1", None, None, None, "AA11AA", "GB", None, TfBoolean.True
      )
    )

  "SubmissionTransformer.toSingleTransferResponses" should:
    "return all processed responses for matching recordIds in request order" in:
      val request = requestWithRecordIds(1, 2)
      val response = StcTransactionCreateProcessed(
        StcTransactionCreateProcessedBody(
          processingDate = "2026-03-30T12:00:00Z",
          charges = List(
            StcChargeSuccess(2, "utrn-2", "desc", "ref-2", "type", BigDecimal(20), "2026-04-30"),
            StcChargeSuccess(1, "utrn-1", "desc", "ref-1", "type", BigDecimal(10), "2026-04-30")
          )
        )
      )

      transformer.toSingleTransferResponses(request, response) shouldBe Seq(
        StcChargeSuccess(1, "utrn-1", "desc", "ref-1", "type", BigDecimal(10), "2026-04-30"),
        StcChargeSuccess(2, "utrn-2", "desc", "ref-2", "type", BigDecimal(20), "2026-04-30")
      )

    "return all processed charges for a recordId when ETMP returns more than one" in:
      val request = requestWithRecordIds(1, 2)
      val response = StcTransactionCreateProcessed(
        StcTransactionCreateProcessedBody(
          processingDate = "2026-03-30T12:00:00Z",
          charges = List(
            StcChargeSuccess(2, "utrn-2", "desc", "ref-2", "type", BigDecimal(20), "2026-04-30"),
            StcChargeSuccess(1, "utrn-1a", "desc", "ref-1a", "type", BigDecimal(10), "2026-04-30"),
            StcChargeSuccess(1, "utrn-1b", "desc", "ref-1b", "type", BigDecimal(15), "2026-05-30")
          )
        )
      )

      transformer.toSingleTransferResponses(request, response) shouldBe Seq(
        StcChargeSuccess(1, "utrn-1a", "desc", "ref-1a", "type", BigDecimal(10), "2026-04-30"),
        StcChargeSuccess(1, "utrn-1b", "desc", "ref-1b", "type", BigDecimal(15), "2026-05-30"),
        StcChargeSuccess(2, "utrn-2", "desc", "ref-2", "type", BigDecimal(20), "2026-04-30")
      )

    "ignore extra processed response recordIds and still return all matching processed responses" in:
      val request = requestWithRecordIds(1, 2)
      val response = StcTransactionCreateProcessed(
        StcTransactionCreateProcessedBody(
          processingDate = "2026-03-30T12:00:00Z",
          charges = List(
            StcChargeSuccess(99, "utrn-99", "desc", "ref-99", "type", BigDecimal(99), "2026-04-30"),
            StcChargeSuccess(1, "utrn-1", "desc", "ref-1", "type", BigDecimal(10), "2026-04-30"),
            StcChargeSuccess(2, "utrn-2", "desc", "ref-2", "type", BigDecimal(20), "2026-04-30")
          )
        )
      )

      transformer.toSingleTransferResponses(request, response) shouldBe Seq(
        StcChargeSuccess(1, "utrn-1", "desc", "ref-1", "type", BigDecimal(10), "2026-04-30"),
        StcChargeSuccess(2, "utrn-2", "desc", "ref-2", "type", BigDecimal(20), "2026-04-30")
      )

    "return a server-error failure for any missing processed response recordId" in:
      val request = requestWithRecordIds(1, 2)
      val response = StcTransactionCreateProcessed(
        StcTransactionCreateProcessedBody(
          processingDate = "2026-03-30T12:00:00Z",
          charges = List(
            StcChargeSuccess(1, "utrn-1", "desc", "ref-1", "type", BigDecimal(10), "2026-04-30")
          )
        )
      )

      transformer.toSingleTransferResponses(request, response) shouldBe Seq(
        StcChargeSuccess(1, "utrn-1", "desc", "ref-1", "type", BigDecimal(10), "2026-04-30"),
        StcChargeFailure(2, "INTERNAL_SERVER_ERROR", "ETMP did not return a processed response for this record")
      )

    "map a bad-request response to one failure per input record" in:
      val request = requestWithRecordIds(1, 2)
      val response = StcTransactionCreateBadRequest(
        StcTransactionCreateBadRequestBody("400", "Bad request", "log-id")
      )

      transformer.toSingleTransferResponses(request, response) shouldBe Seq(
        StcChargeFailure(1, "400", "Bad request"),
        StcChargeFailure(2, "400", "Bad request")
      )

    "map a business-error response to one failure per input record" in:
      val request = requestWithRecordIds(1, 2)
      val response = StcTransactionCreateBusinessError(
        StcTransactionCreateBusinessErrorBody("2026-03-30T12:00:00Z", "037", "Main Buyer Details Invalid")
      )

      transformer.toSingleTransferResponses(request, response) shouldBe Seq(
        StcChargeFailure(1, "037", "Main Buyer Details Invalid"),
        StcChargeFailure(2, "037", "Main Buyer Details Invalid")
      )

  "SubmissionTransformer.toViewTransferResponse" should:
    "map an ETMP view body to the API response model" in:
      transformer.toViewTransferResponse(ViewTransactionFixtures.successBody) shouldBe ViewTransactionFixtures.expectedResponse

    "convert every ETMP T/F flag to a boolean and keep amend-only contingent fields" in:
      val body = ViewTransactionFixtures.successBody
      val etmp = body.copy(
        transactionDetails = body.transactionDetails.copy(
          qualifyAsTreasuryShares = Some(TfBoolean.True),
          isConnectedPartiesTransactions = TfBoolean.True
        ),
        contingentDetails = List(body.contingentDetails.head.copy(
          isAmountUnasertainable = TfBoolean.False,
          defermentOfPayment = TfBoolean.True,
          actualDate = Some(LocalDate.parse("2026-05-01")),
          ascertainableAmountActual = Some(BigDecimal(1200)),
          defermentExtensionDate = Some(LocalDate.parse("2026-06-01"))
        )),
        mainBuyerDetails = body.mainBuyerDetails.copy(isPLC = Some(TfBoolean.False)),
        agentDetails = Some(AgentDetailsView("Agent", None, None, None, None, "CC3 3CC", "GB", "0123", "a@test.com", Some("ref"))),
        declaration = body.declaration.copy(selfDeclarationAgent = Some(TfBoolean.True), isCorrectInfo = TfBoolean.False)
      )

      val result = transformer.toViewTransferResponse(etmp)

      result.transactionDetails.qualifyAsTreasuryShares shouldBe Some(true)
      result.transactionDetails.isConnectedPartiesTransactions shouldBe true
      result.contingentDetails.head.isAmountUnasertainable shouldBe false
      result.contingentDetails.head.defermentOfPayment shouldBe true
      result.contingentDetails.head.actualDate shouldBe Some(LocalDate.parse("2026-05-01"))
      result.contingentDetails.head.ascertainableAmountActual shouldBe Some(BigDecimal(1200))
      result.contingentDetails.head.defermentExtensionDate shouldBe Some(LocalDate.parse("2026-06-01"))
      result.mainBuyerDetails.isPLC shouldBe Some(false)
      result.agentDetails shouldBe Some(SingleTransferAgentDetails("Agent", None, None, None, None, "CC3 3CC", "GB", "0123", "a@test.com", Some("ref")))
      result.declaration.selfDeclarationAgent shouldBe Some(true)
      result.declaration.isCorrectInfo shouldBe false

  "SubmissionTransformer.toRequests" should:
    "return one request when given one single-record request" in:
      val result = transformerMax3.toRequests(Seq(singleRecordRequest(1)), declaration, submissionId)

      result.size shouldBe 1
      result.head.transactionDetails.map(_.recordId) shouldBe Seq(1)
      result.head.transactionDetails.map(_.isConnectedPartiesTransactions) shouldBe Seq(TfBoolean.False)
      result.head.mainSellerDetails.map(_.recordId) shouldBe Seq(1)
      result.head.mainBuyerDetails.map(_.recordId) shouldBe Seq(1)
      result.head.declaration.isCorrectInfo shouldBe TfBoolean.True

    "return one request for n singles where n is less than or equal to max records per request" in:
      val result = transformerMax3.toRequests(Seq(singleRecordRequest(1), singleRecordRequest(2), singleRecordRequest(3)), declaration, submissionId)

      result.size shouldBe 1
      result.head.transactionDetails.map(_.recordId) shouldBe Seq(1, 2, 3)

    "map ReasonForPurchase values to ETMP integer codes" in:
      val first = singleRecordRequest(1).copy(
        transactionDetails = singleRecordRequest(1).transactionDetails.copy(
          reasonForPurchase = Some(ReasonForPurchase.PurchasedForCancellation)
        )
      )
      val second = singleRecordRequest(2).copy(
        transactionDetails = singleRecordRequest(2).transactionDetails.copy(
          reasonForPurchase = Some(ReasonForPurchase.PurchasedToPlaceIntoTreasury)
        )
      )
      val third = singleRecordRequest(3).copy(
        transactionDetails = singleRecordRequest(3).transactionDetails.copy(
          reasonForPurchase = Some(ReasonForPurchase.Both)
        )
      )

      val result = transformerMax3.toRequests(Seq(first, second, third), declaration, submissionId)

      result should have size 1
      result.head.transactionDetails.map(_.reasonForPurchase) shouldBe Seq(
        Some(ReasonForPurchase.PurchasedForCancellation),
        Some(ReasonForPurchase.PurchasedToPlaceIntoTreasury),
        Some(ReasonForPurchase.Both)
      )

    "return multiple requests for m singles where m is greater than max records per request" in:
      val result = transformerMax3.toRequests((1 to 8).map(singleRecordRequest), declaration, submissionId)

      result.size shouldBe 3
      result.map(_.transactionDetails.map(_.recordId)) shouldBe Seq(
        Seq(1, 2, 3),
        Seq(4, 5, 6),
        Seq(7, 8)
      )

    "carry the submissionId and declaration onto every request" in:
      val result = transformerMax3.toRequests((1 to 4).map(singleRecordRequest), declaration.copy(selfDeclarationAgent = Some(false)), submissionId)

      result.map(_.submissionId) shouldBe Seq(submissionId, submissionId)
      result.map(_.declaration.selfDeclarationAgent).distinct shouldBe Seq(Some(TfBoolean.False))
      result.map(_.declaration.name).distinct shouldBe Seq("Declarer")

    "map every optional section, converting booleans to T/F and tagging each row with its recordId" in:
      val base = singleRecordRequest(1)
      val full = base.copy(
        transactionDetails = base.transactionDetails.copy(
          qualifyAsTreasuryShares = Some(true),
          isConnectedPartiesTransactions = true
        ),
        contingentDetails = Some(Seq(
          SingleTransferContingentDetails(
            provisionalDate = LocalDate.parse("2026-04-01"),
            isAmountUnasertainable = true,
            unascertainableAmount = Some(BigDecimal(5)),
            ascertainableAmount = None,
            defermentOfPayment = false,
            originalDefermentDate = Some(LocalDate.parse("2026-05-01"))
          )
        )),
        otherSellers = Some(Seq(SingleTransferOtherSellerName("seller 2"), SingleTransferOtherSellerName("seller 3"))),
        mainBuyerDetails = base.mainBuyerDetails.copy(isPLC = Some(true)),
        otherBuyers = Some(Seq(SingleTransferOtherBuyerName("buyer 2"))),
        agentDetails = Some(SingleTransferAgentDetails("Agent", Some("a1"), None, None, None, "CC3 3CC", "GB", "0123", "a@test.com", Some("ref")))
      )
      val plain = singleRecordRequest(2)

      val result = transformerMax3.toRequests(Seq(full, plain), declaration, submissionId).head

      result.transactionDetails.map(_.qualifyAsTreasuryShares) shouldBe Seq(Some(TfBoolean.True), None)
      result.transactionDetails.map(_.isConnectedPartiesTransactions) shouldBe Seq(TfBoolean.True, TfBoolean.False)
      result.contingentDetails shouldBe Some(Seq(
        ContingentDetailsCreate(
          recordId = 1,
          provisionalDate = LocalDate.parse("2026-04-01"),
          isAmountUnasertainable = TfBoolean.True,
          unascertainableAmount = Some(BigDecimal(5)),
          ascertainableAmount = None,
          defermentOfPayment = TfBoolean.False,
          originalDefermentDate = Some(LocalDate.parse("2026-05-01"))
        )
      ))
      result.otherSellers shouldBe Some(Seq(OtherSellerCreateName(1, "seller 2"), OtherSellerCreateName(1, "seller 3")))
      result.otherBuyers shouldBe Some(Seq(OtherBuyerCreateName(1, "buyer 2")))
      result.mainBuyerDetails.map(_.isPLC) shouldBe Seq(Some(TfBoolean.True), None)
      result.mainSellerDetails.map(_.recordId) shouldBe Seq(1, 2)
      result.agentDetails shouldBe Some(
        AgentDetailsCreate("Agent", Some("a1"), None, None, None, "CC3 3CC", "GB", "0123", "a@test.com", Some("ref"))
      )

    "leave optional sections empty when no record supplies them" in:
      val result = transformerMax3.toRequests(Seq(singleRecordRequest(1), singleRecordRequest(2)), declaration, submissionId).head

      result.contingentDetails shouldBe None
      result.otherSellers shouldBe None
      result.otherBuyers shouldBe None
      result.agentDetails shouldBe None

    "omit the seller details of records that have none" in:
      val noSeller = singleRecordRequest(2).copy(mainSellerDetails = None)

      val result = transformerMax3.toRequests(Seq(singleRecordRequest(1), noSeller), declaration, submissionId).head

      result.mainSellerDetails.map(_.recordId) shouldBe Seq(1)
      result.mainBuyerDetails.map(_.recordId) shouldBe Seq(1, 2)

    "send a single agent for the batch, taking the first record's" in:
      def withAgent(recordId: Int, name: String) = singleRecordRequest(recordId).copy(
        agentDetails = Some(SingleTransferAgentDetails(name, None, None, None, None, "CC3 3CC", "GB", "0123", "a@test.com", None))
      )

      val result = transformerMax3.toRequests(Seq(withAgent(1, "first"), withAgent(2, "second")), declaration, submissionId).head

      result.agentDetails.map(_.name) shouldBe Some("first")
