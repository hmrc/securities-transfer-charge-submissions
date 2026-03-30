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
import play.api.libs.json.{JsNull, JsObject, JsResultException, Json}
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.controllers.{TransferData, TransferType}

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

  private def transferData(
    affinity: AffinityGroup,
    data: JsObject
  ): TransferData =
    TransferData(
      transferType = TransferType.STF,
      subscriptionId = "sub-123",
      submissionId = "submission-123",
      submitterAffinity = affinity,
      data = data
    )

  private val baseSellerAddress = Json.obj(
    "auditRef" -> "audit-ref",
    "id" -> "alf-id",
    "address" -> Json.obj(
      "lines" -> Json.arr("seller line 1", "seller line 2"),
      "postcode" -> "ZZ11ZZ",
      "country" -> Json.obj("name" -> "United Kingdom", "code" -> "GB")
    )
  )

  private val baseBuyerAlfAddress = Json.obj(
    "auditRef" -> "buyer-audit-ref",
    "id" -> "buyer-alf-id",
    "address" -> Json.obj(
      "lines" -> Json.arr("buyer line 1", "buyer line 2"),
      "postcode" -> "AA11AA",
      "country" -> Json.obj("name" -> "United Kingdom", "code" -> "GB")
    )
  )

  private val baseConfirmableAddress = Json.obj(
    "lines" -> Json.arr("confirmed buyer line 1", "confirmed buyer line 2"),
    "postcode" -> "BB22BB",
    "country" -> Json.obj("name" -> "United Kingdom", "code" -> "GB")
  )

  private val commonPages = Json.obj(
    "chargingPoint" -> "2026-03-30",
    "connectedPersons" -> false,
    "nameOfSeller" -> "Seller Ltd",
    "sellerAddress" -> baseSellerAddress,
    "securitiesTarget" -> Json.obj("businessName" -> "Buyer Ltd", "crn" -> "CRN123"),
    "applyingForRelief" -> false,
    "taxRate" -> "half"
  )

  private def requestWithRecordIds(recordIds: Int*): StcTransactionCreateRequest =
    StcTransactionCreateRequest(
      submissionId = "submission-123",
      transactionDetails = recordIds.map { recordId =>
        TransactionDetailsCreate(
          recordId = recordId,
          transactionType = 1,
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
          isConnectedPartiesTransactions = "N",
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
        BuyerDetailsCreate(recordId, s"buyer-$recordId", "addr1", None, None, None, "AA11AA", "GB", "a@test.com", None, 1, None)
      },
      otherBuyers = None,
      agentDetails = None,
      declaration = recordIds.map { recordId =>
        DeclarationCreate(recordId, None, None, s"name-$recordId", "addr1", None, None, None, "AA11AA", "GB", None, "Y")
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

  "SubmissionTransformer.toSingleRecordRequest" should:
    "map a shares journey using DetailsOfThisTransfer and buyerAddress" in:
      val data = transferData(
        affinity = AffinityGroup.Organisation,
        data = commonPages ++ Json.obj(
          "buyerAddress" -> baseBuyerAlfAddress,
          "whatTypeOfSecurities" -> "shares",
          "detailsOfThisTransfer" -> Json.obj(
            "numberOfShares" -> "10",
            "typeOfShares" -> "Ordinary shares",
            "amountPaid" -> BigDecimal(1500),
            "marketValue" -> BigDecimal(2000)
          )
        )
      )

      val result = transformer.toSingleRecordRequest(recordId = 7, data)

      result.recordId shouldBe 7
      result.transactionDetails.descriptionOfSecurity shouldBe "Ordinary shares"
      result.transactionDetails.numberOfShares shouldBe 10
      result.transactionDetails.considerationActual shouldBe BigDecimal(1500)
      result.transactionDetails.marketValue shouldBe Some(BigDecimal(2000))
      result.mainBuyerDetails.addr1 shouldBe "buyer line 1"

    "map an other-securities connected-persons journey and require totalMarketValue" in:
      val data = transferData(
        affinity = AffinityGroup.Organisation,
        data = commonPages ++ Json.obj(
          "buyerAddress" -> baseBuyerAlfAddress,
          "connectedPersons" -> true,
          "taxRate" -> "oneAndHalf",
          "whatTypeOfSecurities" -> "other",
          "otherSecuritiesType" -> "Preference units",
          "amountPaidForSecurities" -> BigDecimal(2500),
          "totalMarketValuePage" -> BigDecimal(3000)
        )
      )

      val result = transformer.toSingleRecordRequest(recordId = 8, data)

      result.transactionDetails.descriptionOfSecurity shouldBe "Preference units"
      result.transactionDetails.numberOfShares shouldBe 0
      result.transactionDetails.considerationActual shouldBe BigDecimal(2500)
      result.transactionDetails.marketValue shouldBe Some(BigDecimal(3000))
      result.mainBuyerDetails.taxRate shouldBe 2

    "throw a JsResultException when connected persons + other securities is missing totalMarketValuePage" in:
      val data = transferData(
        affinity = AffinityGroup.Organisation,
        data = commonPages ++ Json.obj(
          "buyerAddress" -> baseBuyerAlfAddress,
          "connectedPersons" -> true,
          "whatTypeOfSecurities" -> "other",
          "otherSecuritiesType" -> "Preference units",
          "amountPaidForSecurities" -> BigDecimal(2500)
        )
      )

      val thrown = the[JsResultException] thrownBy transformer.toSingleRecordRequest(recordId = 11, data)
      thrown.errors.toString should include("totalMarketValuePage")

    "fallback to confirmedAddress when buyerAddress is missing" in:
      val data = transferData(
        affinity = AffinityGroup.Individual,
        data = commonPages ++ Json.obj(
          "confirmedAddress" -> baseConfirmableAddress,
          "whatTypeOfSecurities" -> "shares",
          "detailsOfThisTransfer" -> Json.obj(
            "numberOfShares" -> "2",
            "typeOfShares" -> "Class A",
            "amountPaid" -> BigDecimal(99),
            "marketValue" -> JsNull
          )
        )
      )

      val result = transformer.toSingleRecordRequest(recordId = 9, data)

      result.mainBuyerDetails.addr1 shouldBe "confirmed buyer line 1"
      result.mainBuyerDetails.postcode shouldBe "BB22BB"
      result.mainBuyerDetails.country shouldBe "GB"

    "throw an IllegalArgumentException when both buyerAddress and confirmedAddress are missing" in:
      val data = transferData(
        affinity = AffinityGroup.Individual,
        data = commonPages ++ Json.obj(
          "whatTypeOfSecurities" -> "shares",
          "detailsOfThisTransfer" -> Json.obj(
            "numberOfShares" -> "1",
            "typeOfShares" -> "Ordinary",
            "amountPaid" -> BigDecimal(1),
            "marketValue" -> JsNull
          )
        )
      )

      val thrown = the[IllegalArgumentException] thrownBy transformer.toSingleRecordRequest(recordId = 10, data)
      thrown.getMessage should include("buyerAddress or confirmedAddress")
