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

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.BuyerTaxRate
import uk.gov.hmrc.securitiestransferchargesubmissions.models.DeclarationRole
import uk.gov.hmrc.securitiestransferchargesubmissions.models.ReasonForPurchase
import uk.gov.hmrc.securitiestransferchargesubmissions.models.TransferType
import uk.gov.hmrc.securitiestransferchargesubmissions.models.TfBoolean

import java.time.LocalDate

final case class StcTransactionCreateRequest(
  submissionId: String,
  transactionDetails: Seq[TransactionDetailsCreate],
  contingentDetails: Option[Seq[ContingentDetailsCreate]],
  mainSellerDetails: Seq[SellerDetailsCreate],
  otherSellers: Option[Seq[OtherSellerCreateName]],
  mainBuyerDetails: Seq[BuyerDetailsCreate],
  otherBuyers: Option[Seq[OtherBuyerCreateName]],
  agentDetails: Option[AgentDetailsCreate],
  declaration: DeclarationCreate
)

final case class TransactionDetailsCreate(
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

final case class ContingentDetailsCreate(
  recordId: Int,
  provisionalDate: LocalDate,
  isAmountUnasertainable: TfBoolean,
  unascertainableAmount: Option[BigDecimal],
  ascertainableAmount: Option[BigDecimal],
  defermentOfPayment: TfBoolean,
  originalDefermentDate: Option[LocalDate]
)

final case class SellerDetailsCreate(
  recordId: Int,
  sellerName: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String
)

final case class OtherSellerCreateName(
  recordId: Int,
  sellerName: String
)

final case class BuyerDetailsCreate(
  recordId: Int,
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

final case class OtherBuyerCreateName(
  recordId: Int,
  buyerName: String
)

final case class AgentDetailsCreate(
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

final case class DeclarationCreate(
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

object StcTransactionCreateRequest:
  given Writes[DeclarationCreate] = Json.writes[DeclarationCreate]
  given Writes[AgentDetailsCreate] = Json.writes[AgentDetailsCreate]
  given Writes[OtherBuyerCreateName] = Json.writes[OtherBuyerCreateName]
  given Writes[BuyerDetailsCreate] = Json.writes[BuyerDetailsCreate]
  given Writes[OtherSellerCreateName] = Json.writes[OtherSellerCreateName]
  given Writes[SellerDetailsCreate] = Json.writes[SellerDetailsCreate]
  given Writes[ContingentDetailsCreate] = Json.writes[ContingentDetailsCreate]
  given Writes[TransactionDetailsCreate] = Json.writes[TransactionDetailsCreate]
  given Writes[StcTransactionCreateRequest] = Json.writes[StcTransactionCreateRequest]
