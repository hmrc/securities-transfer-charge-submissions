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

package uk.gov.hmrc.securitiestransferchargesubmissions.domain.models.etmp

import java.time.LocalDate
import java.util.UUID

enum TaxRate(val value: Int):
  case ZeroPointFive extends TaxRate(1)
  case OnePointFive  extends TaxRate(2)

trait SubmissionHeaderData:
  def stcId: String
  def transmittingSystem: String = "HIP"
  def originatingSystem: String = "MDTP-STC"
  def receiptDate: LocalDate
  def correlationId: UUID = UUID.randomUUID()
  def messageType: MessageType
  def submissionID: String
  def transactionType: TransactionType

trait StfOptionalData:
  def reasonForPurchase: Option[String] = None
  def nominalValue: Option[BigDecimal] = None
  def marketValue: Option[BigDecimal] = None
  def qualifyAsTreasuryShares: Option[Boolean] = None
  def maxPricePaid: Option[BigDecimal] = None
  def minPricePaid: Option[BigDecimal] = None
  def reliefClaimedName: Option[String] = None
  def reliefPercentage: Option[Int] = None

case class StfTransferData(transactionType: TransactionType = TransactionType.STF,
                           descriptionOfSecurity: String,
                           numberOfShares: Int,
                           originalChargingPoint: LocalDate,
                           considerationActual: BigDecimal,
                           isConnectedPartiesTransactions: Boolean,
                           companyName: String,
                           sellerDetails: SellerDetails,
                           buyerDetails: BuyerDetails
                          ) extends SubmissionHeaderData with StfOptionalData:
  override def stcId: String = ???
  override def receiptDate: LocalDate = LocalDate.now()
  override def messageType: MessageType = MessageType.Create
  override def submissionID: String = ???

case class BuyerDetails(name: Seq[String],
                        addr1: String,
                        addr2: Option[String],
                        addr3: Option[String],
                        addr4: Option[String],
                        postcode: String,
                        country: String,
                        email: String,
                        uniqueId: Option[String],
                        taxRate: TaxRate,
                        isPLC: Boolean
                       )

case class SellerDetails(name: Seq[String],
                         addr1: String,
                         addr2: Option[String],
                         addr3: Option[String],
                         addr4: Option[String],
                         postcode: String,
                         country: String
                        )
