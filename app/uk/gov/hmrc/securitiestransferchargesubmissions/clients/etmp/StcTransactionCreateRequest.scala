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
import uk.gov.hmrc.securitiestransferchargesubmissions.models.YnBoolean

import java.time.LocalDate

final case class StcTransactionCreateRequest(
  submissionId: String,
  transactionDetails: Seq[TransactionDetailsCreate],
  contingentDetails: Option[Seq[ContingentDetailsCreate]],
  mainSellerDetails: Seq[SellerDetailsCreate],
  otherSellers: Option[Seq[OtherSellerCreateName]],
  mainBuyerDetails: Seq[BuyerDetailsCreate],
  otherBuyers: Option[Seq[OtherBuyerCreateName]],
  agentDetails: Option[Seq[AgentDetailsCreate]],
  declaration: Seq[DeclarationCreate]
)

final case class TransactionDetailsCreate(
  recordId: Int,
  transactionType: TransferType,
  reasonForPurchase: Option[ReasonForPurchase],
  descriptionOfSecurity: String,
  numberOfShares: Int,
  nominalValue: Option[BigDecimal],
  marketValue: Option[BigDecimal],
  qualifyAsTreasuryShares: Option[YnBoolean],
  maxPricePaid: Option[BigDecimal],
  minPricePaid: Option[BigDecimal],
  originalChargingPoint: LocalDate,
  considerationActual: BigDecimal,
  isConnectedPartiesTransactions: YnBoolean,
  companyName: String,
  companyRegistrationNumber: Option[String],
  reliefClaimedName: Option[String],
  reliefPercentage: Option[Int]
)

final case class ContingentDetailsCreate(
  recordId: Int,
  provisionalDate: LocalDate,
  isAmountUnasertainable: YnBoolean,
  unascertainableAmount: Option[BigDecimal],
  ascertainableAmount: Option[BigDecimal],
  defermentOfPayment: YnBoolean,
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
  isPLC: Option[YnBoolean]
)

final case class OtherBuyerCreateName(
  recordId: Int,
  buyerName: String
)

final case class AgentDetailsCreate(
  recordId: Int,
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
  recordId: Int,
  role1: Option[DeclarationRole],
  role2: Option[String],
  name: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: String,
  country: String,
  selfDeclarationAgent: Option[YnBoolean],
  isCorrectInfo: YnBoolean
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
