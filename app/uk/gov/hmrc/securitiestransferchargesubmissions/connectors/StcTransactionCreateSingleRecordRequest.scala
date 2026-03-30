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

import play.api.libs.json.{Json, Writes}

import java.time.LocalDate

final case class StcTransactionCreateSingleRecordRequest(
  recordId: Int,
  submissionId: String,
  transactionDetails: TransactionDetailsCreateSingleRecord,
  contingentDetails: Option[Seq[ContingentDetailsCreateSingleRecord]],
  mainSellerDetails: SellerDetailsCreateSingleRecord,
  otherSellers: Option[Seq[OtherSellerCreateNameSingleRecord]],
  mainBuyerDetails: BuyerDetailsCreateSingleRecord,
  otherBuyers: Option[Seq[OtherBuyerCreateNameSingleRecord]],
  agentDetails: Option[Seq[AgentDetailsCreateSingleRecord]],
  declaration: DeclarationCreateSingleRecord
)

final case class TransactionDetailsCreateSingleRecord(
  transactionType: Int,
  reasonForPurchase: Option[Int],
  descriptionOfSecurity: String,
  numberOfShares: Int,
  nominalValue: Option[BigDecimal],
  marketValue: Option[BigDecimal],
  qualifyAsTreasuryShares: Option[String],
  maxPricePaid: Option[BigDecimal],
  minPricePaid: Option[BigDecimal],
  originalChargingPoint: LocalDate,
  considerationActual: BigDecimal,
  isConnectedPartiesTransactions: String,
  companyName: String,
  companyRegistrationNumber: Option[String],
  reliefClaimedName: Option[String],
  reliefPercentage: Option[Int]
)

final case class ContingentDetailsCreateSingleRecord(
  provisionalDate: LocalDate,
  isAmountUnasertainable: String,
  unascertainableAmount: Option[BigDecimal],
  ascertainableAmount: Option[BigDecimal],
  defermentOfPayment: String,
  originalDefermentDate: Option[LocalDate]
)

final case class SellerDetailsCreateSingleRecord(
  sellerName: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String
)

final case class OtherSellerCreateNameSingleRecord(
  sellerName: String
)

final case class BuyerDetailsCreateSingleRecord(
  buyerName: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String,
  email: String,
  uniqueId: Option[String],
  taxRate: Int,
  isPLC: Option[String]
)

final case class OtherBuyerCreateNameSingleRecord(
  buyerName: String
)

final case class AgentDetailsCreateSingleRecord(
  name: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String,
  phone: String,
  email: String,
  clientReference: String
)

final case class DeclarationCreateSingleRecord(
  role1: Option[String],
  role2: Option[String],
  name: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String,
  selfDeclarationAgent: Option[String],
  isCorrectInfo: String
)

object StcTransactionCreateSingleRecordRequest:
  given Writes[DeclarationCreateSingleRecord] = Json.writes[DeclarationCreateSingleRecord]
  given Writes[AgentDetailsCreateSingleRecord] = Json.writes[AgentDetailsCreateSingleRecord]
  given Writes[OtherBuyerCreateNameSingleRecord] = Json.writes[OtherBuyerCreateNameSingleRecord]
  given Writes[BuyerDetailsCreateSingleRecord] = Json.writes[BuyerDetailsCreateSingleRecord]
  given Writes[OtherSellerCreateNameSingleRecord] = Json.writes[OtherSellerCreateNameSingleRecord]
  given Writes[SellerDetailsCreateSingleRecord] = Json.writes[SellerDetailsCreateSingleRecord]
  given Writes[ContingentDetailsCreateSingleRecord] = Json.writes[ContingentDetailsCreateSingleRecord]
  given Writes[TransactionDetailsCreateSingleRecord] = Json.writes[TransactionDetailsCreateSingleRecord]
  given Writes[StcTransactionCreateSingleRecordRequest] = Json.writes[StcTransactionCreateSingleRecordRequest]
