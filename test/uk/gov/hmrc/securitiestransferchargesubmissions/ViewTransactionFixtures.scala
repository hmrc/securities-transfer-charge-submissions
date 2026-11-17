/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcTransactionViewBody
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcTransactionViewResponse.given
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{BuyerTaxRate, DeclarationRole, TransferType}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.*

import java.time.LocalDate

/** ETMP STC Transaction View payloads, shaped per the OAS (stc-transaction-oas_0.5.0.yaml). */
object ViewTransactionFixtures:

  val successBodyJson: JsObject = Json.obj(
    "processingDate" -> "2026-03-31T12:00:00Z",
    "charges" -> Json.arr(Json.obj(
      "chargeTypeDescription" -> "Securities Transfer Charge",
      "chargeReference"       -> "XA123",
      "chargeType"            -> "STF",
      "chargeAmount"          -> 10.00,
      "chargeDueDate"         -> "2026-04-30"
    )),
    "transactionDetails" -> Json.obj(
      "submissionId"                   -> "sub-123",
      "submissionDate"                 -> "2026-03-30",
      "recordId"                       -> 1,
      "transactionType"                -> 1,
      "typeOfSecurity"                 -> "Ordinary shares",
      "numberOfShares"                 -> 10,
      "originalChargingPoint"          -> "2026-03-30",
      "considerationActual"            -> 1000,
      "isConnectedPartiesTransactions" -> "F",
      "companyName"                    -> "Company Ltd"
    ),
    "contingentDetails" -> Json.arr(Json.obj(
      "provisionalId"          -> "1",
      "provisionalDate"        -> "2026-03-30",
      "isAmountUnasertainable" -> "T",
      "defermentOfPayment"     -> "F"
    )),
    "mainSellerDetails" -> Json.obj(
      "sellerName" -> "Seller Ltd",
      "addr1"      -> "1 Main St",
      "postcode"   -> "AA1 1AA",
      "country"    -> "GB"
    ),
    "otherSellers" -> Json.arr(Json.obj("sellerName" -> "Other Seller")),
    "mainBuyerDetails" -> Json.obj(
      "buyerName" -> "Buyer Ltd",
      "addr1"     -> "2 High St",
      "postcode"  -> "BB2 2BB",
      "country"   -> "GB",
      "email"     -> "buyer@test.com",
      "taxRate"   -> 1
    ),
    "declaration" -> Json.obj(
      "role1"         -> "1",
      "name"          -> "Declarer",
      "addr1"         -> "1 Main St",
      "postcode"      -> "AA1 1AA",
      "country"       -> "GB",
      "isCorrectInfo" -> "T"
    )
  )

  val successJson: JsObject = Json.obj("success" -> successBodyJson)

  val successBody: StcTransactionViewBody = successBodyJson.as[StcTransactionViewBody]

  /** What GET /submission/:utrn should return for [[successBodyJson]]. */
  val expectedResponse: ViewTransferResponse = ViewTransferResponse(
    processingDate = "2026-03-31T12:00:00Z",
    submissionId   = "sub-123",
    submissionDate = LocalDate.parse("2026-03-30"),
    recordId       = 1,
    transactionDetails = SingleTransferTransactionDetails(
      transactionType                = TransferType.STF,
      reasonForPurchase              = None,
      typeOfSecurity                 = "Ordinary shares",
      numberOfShares                 = 10,
      nominalValue                   = None,
      marketValue                    = None,
      qualifyAsTreasuryShares        = None,
      maxPricePaid                   = None,
      minPricePaid                   = None,
      originalChargingPoint          = LocalDate.parse("2026-03-30"),
      considerationActual            = BigDecimal(1000),
      isConnectedPartiesTransactions = false,
      companyName                    = "Company Ltd",
      companyRegistrationNumber      = None,
      reliefClaimedName              = None,
      reliefPercentage               = None
    ),
    contingentDetails = Seq(ViewTransferContingentDetails(
      provisionalId               = "1",
      provisionalDate             = LocalDate.parse("2026-03-30"),
      isAmountUnasertainable      = true,
      unascertainableAmount       = None,
      ascertainableAmount         = None,
      defermentOfPayment          = false,
      originalDefermentDate       = None,
      actualDate                  = None,
      unascertainableAmountActual = None,
      ascertainableAmountActual   = None,
      defermentExtensionDate      = None
    )),
    mainSellerDetails = Some(SingleTransferSellerDetails("Seller Ltd", "1 Main St", None, None, None, "AA1 1AA", "GB")),
    otherSellers      = Seq(SingleTransferOtherSellerName("Other Seller")),
    mainBuyerDetails  = SingleTransferBuyerDetails(
      "Buyer Ltd", "2 High St", None, None, None, "BB2 2BB", "GB", "buyer@test.com", None, BuyerTaxRate.HalfPercent, None
    ),
    otherBuyers  = Nil,
    agentDetails = None,
    declaration  = SingleTransferDeclaration(
      Some(DeclarationRole.Director), None, "Declarer", "1 Main St", None, None, None, "AA1 1AA", "GB", None, isCorrectInfo = true
    ),
    charges = Seq(ViewTransferCharge("Securities Transfer Charge", "XA123", "STF", BigDecimal(10), "2026-04-30"))
  )

  def businessErrorJson(code: String, text: String): JsObject = Json.obj(
    "errors" -> Json.obj("processingDate" -> "2026-03-31T12:00:00Z", "code" -> code, "text" -> text)
  )

  val badRequestJson: JsObject = Json.obj(
    "error" -> Json.obj("code" -> "400", "message" -> "Bad request", "logID" -> "C0000AB8190CB66000000003000007A6")
  )
