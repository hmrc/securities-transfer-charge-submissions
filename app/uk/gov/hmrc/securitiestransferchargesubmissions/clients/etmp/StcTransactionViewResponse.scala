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

import play.api.libs.json.*
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{BuyerTaxRate, DeclarationRole, ReasonForPurchase, TfBoolean, TransferType}

import java.time.LocalDate

// Models for the ETMP STC Transaction View operation (EPID1686):
// GET /RESTAdapter/stc/transaction/{stcId}/{utrn}

sealed trait StcTransactionViewResponse

final case class StcTransactionViewFound(success: StcTransactionViewBody)
  extends StcTransactionViewResponse

final case class StcTransactionViewBadRequest(error: StcTransactionCreateBadRequestBody)
  extends StcTransactionViewResponse

final case class StcTransactionViewBusinessError(errors: StcTransactionCreateBusinessErrorBody)
  extends StcTransactionViewResponse

final case class StcTransactionViewBody(
  processingDate: String,
  charges: List[StcViewCharge] = Nil,
  transactionDetails: TransactionDetailsView,
  contingentDetails: List[ContingentDetailsView] = Nil,
  mainSellerDetails: Option[SellerDetailsView],
  otherSellers: List[OtherSellerNameView] = Nil,
  mainBuyerDetails: BuyerDetailsView,
  otherBuyers: List[OtherBuyerNameView] = Nil,
  agentDetails: Option[AgentDetailsView],
  declaration: DeclarationView
)

final case class StcViewCharge(
  chargeTypeDescription: String,
  chargeReference: String,
  chargeType: String,
  chargeAmount: BigDecimal,
  chargeDueDate: String
)

final case class TransactionDetailsView(
  submissionId: String,
  submissionDate: LocalDate,
  recordId: Int,
  transactionType: TransferType,
  reasonForPurchase: Option[ReasonForPurchase],
  typeOfSecurity: String,
  numberOfShares: Int,
  nominalValue: Option[BigDecimal],
  marketValue: Option[BigDecimal],
  qualifyAsTreasuryShares: Option[TfBoolean],
  maxPricePaid: Option[BigDecimal],
  minPricePaid: Option[BigDecimal],
  originalChargingPoint: LocalDate,
  considerationActual: BigDecimal,
  isConnectedPartiesTransactions: TfBoolean,
  companyName: String,
  companyRegistrationNumber: Option[String],
  reliefClaimedName: Option[String],
  reliefPercentage: Option[Int]
)

final case class ContingentDetailsView(
  provisionalId: String,
  provisionalDate: LocalDate,
  actualDate: Option[LocalDate],
  isAmountUnasertainable: TfBoolean,
  unascertainableAmount: Option[BigDecimal],
  ascertainableAmount: Option[BigDecimal],
  unascertainableAmountActual: Option[BigDecimal],
  ascertainableAmountActual: Option[BigDecimal],
  defermentOfPayment: TfBoolean,
  originalDefermentDate: Option[LocalDate],
  defermentExtensionDate: Option[LocalDate]
)

final case class SellerDetailsView(
  sellerName: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String
)

final case class OtherSellerNameView(sellerName: String)

final case class BuyerDetailsView(
  buyerName: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String,
  email: String,
  uniqueId: Option[String],
  taxRate: BuyerTaxRate,
  isPLC: Option[TfBoolean]
)

final case class OtherBuyerNameView(buyerName: String)

final case class AgentDetailsView(
  name: String,
  addr1: Option[String],
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String,
  phone: String,
  email: String,
  clientReference: Option[String]
)

final case class DeclarationView(
  role1: Option[DeclarationRole],
  role2: Option[String],
  name: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String,
  selfDeclarationAgent: Option[TfBoolean],
  isCorrectInfo: TfBoolean
)

object StcTransactionViewResponse:

  import StcTransactionCreateResponse.given

  given Reads[StcViewCharge] = Json.reads[StcViewCharge]
  given Reads[TransactionDetailsView] = Json.reads[TransactionDetailsView]
  given Reads[ContingentDetailsView] = Json.reads[ContingentDetailsView]
  given Reads[SellerDetailsView] = Json.reads[SellerDetailsView]
  given Reads[OtherSellerNameView] = Json.reads[OtherSellerNameView]
  given Reads[BuyerDetailsView] = Json.reads[BuyerDetailsView]
  given Reads[OtherBuyerNameView] = Json.reads[OtherBuyerNameView]
  given Reads[AgentDetailsView] = Json.reads[AgentDetailsView]
  given Reads[DeclarationView] = Json.reads[DeclarationView]
  // Arrays are optional in the OAS; default them to empty when ETMP omits them.
  given Reads[StcTransactionViewBody] = Json.using[Json.WithDefaultValues].reads[StcTransactionViewBody]
  given Reads[StcTransactionViewFound] = Json.reads[StcTransactionViewFound]

  given Reads[StcTransactionViewBadRequest] = Json.reads[StcTransactionViewBadRequest]
  given Reads[StcTransactionViewBusinessError] = Json.reads[StcTransactionViewBusinessError]

  def fromHttpResponse(response: HttpResponse): StcTransactionViewResponse =
    response.status match
      case 200 => parse[StcTransactionViewFound](response)
      case 400 => parse[StcTransactionViewBadRequest](response)
      case 422 => parse[StcTransactionViewBusinessError](response)
      case status =>
        throw UpstreamErrorResponse(
          s"Unexpected ETMP view response status: $status",
          status,
          status,
          response.headers
        )

  private def parse[A <: StcTransactionViewResponse](response: HttpResponse)(using Reads[A]): A =
    response.json.validate[A] match
      case JsSuccess(value, _) => value
      case JsError(errors) =>
        throw UpstreamErrorResponse(
          s"Unable to parse ETMP view ${response.status} response: $errors",
          502,
          502,
          response.headers
        )
