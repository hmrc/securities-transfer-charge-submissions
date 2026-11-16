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

package uk.gov.hmrc.securitiestransferchargesubmissions.models

import play.api.libs.json.{Json, Reads, Writes}

sealed trait EtmpTransferDetails

final case class TransferDetailsSuccess(success: StcTransactionViewSuccess) extends EtmpTransferDetails
object TransferDetailsSuccess:
  given Reads[TransferDetailsSuccess] = Json.reads[TransferDetailsSuccess]
  given Writes[TransferDetailsSuccess] = Json.writes[TransferDetailsSuccess]

final case class TransferDetailsFailure(errors: StcTransactionViewError) extends EtmpTransferDetails
object TransferDetailsFailure:
  given Reads[TransferDetailsFailure] = Json.reads[TransferDetailsFailure]
  given Writes[TransferDetailsFailure] = Json.writes[TransferDetailsFailure]

// ----------------------
// 200 – success payload
// ----------------------

// The OAS (schema-stc-transactions-view-response-200) only requires processingDate,
// transactionDetails, mainBuyerDetails and declaration. Optional objects are Options;
// optional arrays default to empty when ETMP omits them.
final case class StcTransactionViewSuccess(
  agentDetails: Option[AgentDetailsView],
  charges: Seq[ChargeDetailsView] = Seq.empty,
  contingentDetails: Seq[ContingentDetailsView] = Seq.empty,
  declaration: DeclarationView,
  mainBuyerDetails: BuyerDetailsView,
  mainSellerDetails: Option[SellerDetailsView],
  otherBuyers: Seq[OtherBuyerNameView] = Seq.empty,
  otherSellers: Seq[OtherSellerNameView] = Seq.empty,
  processingDate: String,                 // e.g. 2026-05-10T12:23:12Z
  transactionDetails: TransactionDetailsView
)
object StcTransactionViewSuccess:
  given Reads[StcTransactionViewSuccess] = Json.using[Json.WithDefaultValues].reads[StcTransactionViewSuccess]
  given Writes[StcTransactionViewSuccess] = Json.writes[StcTransactionViewSuccess]

final case class AgentDetailsView(
  addr1: Option[String],                  // not required by the OAS for agents
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  clientReference: Option[String],
  country: String,
  email: String,
  name: String,
  phone: String,
  postcode: String
)
object AgentDetailsView:
  given Reads[AgentDetailsView] = Json.reads[AgentDetailsView]
  given Writes[AgentDetailsView] = Json.writes[AgentDetailsView]

final case class ChargeDetailsView(
  chargeAmount: BigDecimal,
  chargeDueDate: String, // ISO Standard e.g. 2026-06-21
  chargeReference: String,
  chargeType: String,
  chargeTypeDescription: String
)
object ChargeDetailsView:
  given Reads[ChargeDetailsView] = Json.reads[ChargeDetailsView]
  given Writes[ChargeDetailsView] = Json.writes[ChargeDetailsView]

final case class ContingentDetailsView(
  actualDate: Option[String], // ISO Standard e.g. 2026-06-21
  ascertainableAmount: Option[BigDecimal],
  ascertainableAmountActual: Option[BigDecimal],
  defermentExtensionDate: Option[String], // ISO Standard e.g. 2026-06-21
  defermentOfPayment: TfBoolean,
  isAmountUnasertainable: TfBoolean,
  originalDefermentDate: Option[String], // ISO Standard e.g. 2026-06-21
  provisionalDate: String,              // ISO Standard e.g. 2026-06-21
  provisionalId: String,                // "1"–"5" - not sure what they denote
  unascertainableAmount: Option[BigDecimal],
  unascertainableAmountActual: Option[BigDecimal]
)
object ContingentDetailsView:
  given Reads[ContingentDetailsView] = Json.reads[ContingentDetailsView]
  given Writes[ContingentDetailsView] = Json.writes[ContingentDetailsView]

final case class DeclarationView(
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  country: String,
  isCorrectInfo: TfBoolean,
  name: String,
  postcode: String,
  role1: Option[DeclarationRole],
  role2: Option[String],
  selfDeclarationAgent: Option[TfBoolean]
)
object DeclarationView:
  given Reads[DeclarationView] = Json.reads[DeclarationView]
  given Writes[DeclarationView] = Json.writes[DeclarationView]

final case class BuyerDetailsView(
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  buyerName: String,
  country: String,
  email: String,
  isPLC: Option[TfBoolean],
  postcode: String,
  taxRate: BuyerTaxRate,
  uniqueId: Option[String]
)
object BuyerDetailsView:
  given Reads[BuyerDetailsView] = Json.reads[BuyerDetailsView]
  given Writes[BuyerDetailsView] = Json.writes[BuyerDetailsView]

final case class SellerDetailsView(
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  country: String,
  postcode: String,
  sellerName: String
)
object SellerDetailsView:
  given Reads[SellerDetailsView] = Json.reads[SellerDetailsView]
  given Writes[SellerDetailsView] = Json.writes[SellerDetailsView]

final case class OtherBuyerNameView(buyerName: String)
object OtherBuyerNameView:
  given Reads[OtherBuyerNameView] = Json.reads[OtherBuyerNameView]
  given Writes[OtherBuyerNameView] = Json.writes[OtherBuyerNameView]

final case class OtherSellerNameView(sellerName: String)
object OtherSellerNameView:
  given Reads[OtherSellerNameView] = Json.reads[OtherSellerNameView]
  given Writes[OtherSellerNameView] = Json.writes[OtherSellerNameView]

final case class TransactionDetailsView(
  companyName: String,
  companyRegistrationNumber: Option[String],
  considerationActual: BigDecimal,
  isConnectedPartiesTransactions: TfBoolean,
  marketValue: Option[BigDecimal],
  maxPricePaid: Option[BigDecimal],
  minPricePaid: Option[BigDecimal],
  nominalValue: Option[BigDecimal],
  numberOfShares: Int,
  originalChargingPoint: String,          // ISO Standard e.g. 2026-06-21
  qualifyAsTreasuryShares: Option[TfBoolean],
  reasonForPurchase: Option[ReasonForPurchase],
  recordId: Int,
  reliefClaimedName: Option[String],
  reliefPercentage: Option[Int],
  submissionDate: String,                 // ISO Standard e.g. 2026-06-21
  submissionId: String,
  transactionType: TransferType,
  typeOfSecurity: String
)
object TransactionDetailsView:
  given Reads[TransactionDetailsView] = Json.reads[TransactionDetailsView]
  given Writes[TransactionDetailsView] = Json.writes[TransactionDetailsView]

// ----------------------
// 422 – business error
// ----------------------

final case class StcTransactionViewError(
  code: String,                          // Error code - "002" | "005"
  processingDate: String,                // e.g. 2026-05-10T12:23:12Z
  text: String                           // Error message
)
object StcTransactionViewError:
  given Reads[StcTransactionViewError] = Json.reads[StcTransactionViewError]
  given Writes[StcTransactionViewError] = Json.writes[StcTransactionViewError]
