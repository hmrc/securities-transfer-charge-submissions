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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{BuyerTaxRate, DeclarationRole, ReasonForPurchase, TransferType, YnBoolean}

import java.time.LocalDate

class SubmissionTransformerSpec extends AnyWordSpec with Matchers:

  private val appConfig = new AppConfig(
    Configuration.from(
      Map(
        "appName" -> "test",
        "microservice.services.etmp-transaction.host" -> "localhost",
        "microservice.services.etmp-transaction.port" -> 123,
        "microservice.services.etmp-transaction.create.max-records-per-request" -> 12,
        "microservice.services.etmp-transaction.create.max-concurrent-calls" -> 3
      )
    )
  )

  private val transformer = new SubmissionTransformer(appConfig)

  private val appConfigMax3 = new AppConfig(
    Configuration.from(
      Map(
        "appName" -> "test",
        "microservice.services.etmp-transaction.host" -> "localhost",
        "microservice.services.etmp-transaction.port" -> 123,
        "microservice.services.etmp-transaction.create.max-records-per-request" -> 3,
        "microservice.services.etmp-transaction.create.max-concurrent-calls" -> 3
      )
    )
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
        descriptionOfSecurity = s"security-$recordId",
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
      mainSellerDetails = SingleTransferSellerDetails("seller", "addr1", None, None, None, "AA11AA", "GB"),
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
          descriptionOfSecurity = s"security-$recordId",
          numberOfShares = 1,
          nominalValue = None,
          marketValue = None,
          qualifyAsTreasuryShares = None,
          maxPricePaid = None,
          minPricePaid = None,
          originalChargingPoint = LocalDate.parse("2026-03-30"),
          considerationActual = BigDecimal(100),
          isConnectedPartiesTransactions = YnBoolean.No,
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
      declaration = recordIds.map { recordId =>
        DeclarationCreate(recordId, None, None, s"name-$recordId", "addr1", None, None, None, "AA11AA", "GB", None, YnBoolean.Yes)
      }
    )

  "SubmissionTransformer.toSingleRecordResponses" should:
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

      transformer.toSingleRecordResponses(request, response) shouldBe Seq(
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

      transformer.toSingleRecordResponses(request, response) shouldBe Seq(
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

      transformer.toSingleRecordResponses(request, response) shouldBe Seq(
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

      transformer.toSingleRecordResponses(request, response) shouldBe Seq(
        StcChargeSuccess(1, "utrn-1", "desc", "ref-1", "type", BigDecimal(10), "2026-04-30"),
        StcChargeFailure(2, "INTERNAL_SERVER_ERROR", "ETMP did not return a processed response for this record")
      )

    "map a bad-request response to one failure per input record" in:
      val request = requestWithRecordIds(1, 2)
      val response = StcTransactionCreateBadRequest(
        StcTransactionCreateBadRequestBody("400", "Bad request", "log-id")
      )

      transformer.toSingleRecordResponses(request, response) shouldBe Seq(
        StcChargeFailure(1, "400", "Bad request"),
        StcChargeFailure(2, "400", "Bad request")
      )

    "map a business-error response to one failure per input record" in:
      val request = requestWithRecordIds(1, 2)
      val response = StcTransactionCreateBusinessError(
        StcTransactionCreateBusinessErrorBody("2026-03-30T12:00:00Z", "037", "Main Buyer Details Invalid")
      )

      transformer.toSingleRecordResponses(request, response) shouldBe Seq(
        StcChargeFailure(1, "037", "Main Buyer Details Invalid"),
        StcChargeFailure(2, "037", "Main Buyer Details Invalid")
      )

  "SubmissionTransformer.toRequests" should:
    "return one request when given one single-record request" in:
      val result = transformerMax3.toRequests(Seq(singleRecordRequest(1)), declaration, submissionId)

      result.size shouldBe 1
      result.head.transactionDetails.map(_.recordId) shouldBe Seq(1)
      result.head.transactionDetails.map(_.isConnectedPartiesTransactions) shouldBe Seq(YnBoolean.No)
      result.head.mainSellerDetails.map(_.recordId) shouldBe Seq(1)
      result.head.mainBuyerDetails.map(_.recordId) shouldBe Seq(1)
      result.head.declaration.map(_.recordId) shouldBe Seq(1)
      result.head.declaration.map(_.isCorrectInfo) shouldBe Seq(YnBoolean.Yes)

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
