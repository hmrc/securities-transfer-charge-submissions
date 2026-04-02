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

import play.api.libs.json.Reads
import uk.gov.hmrc.securitiestransferchargesubmissions.models.TransferItem

import java.time.LocalDate

enum Pages[A](val path: String):
  case AmountPaidForSecuritiesPage              extends Pages[BigDecimal]("amountPaidForSecurities")
  case ApplyingForReliefPage                    extends Pages[Boolean]("applyingForRelief")
  case ChargingPointPage                        extends Pages[LocalDate]("chargingPoint")
  case ConfirmAddressPage                       extends Pages[ConfirmableAddress]("confirmedAddress")
  case ConnectedPersonsPage                     extends Pages[Boolean]("connectedPersons")
  case DetailsOfThisTransferPage                extends Pages[DetailsOfThisTransfer]("detailsOfThisTransfer")
  case HowToNotifyAboutSecuritiesTransferPage   extends Pages[HowToNotifyAboutSecuritiesTransfer]("howToNotifyAboutSecuritiesTransfer")
  case NameOfSellerPage                         extends Pages[String]("nameOfSeller")
  case OtherSecuritiesTypePage                  extends Pages[String]("otherSecuritiesType")
  case SecuritiesTargetPage                     extends Pages[SecuritiesTarget]("securitiesTarget")
  case StfBuyersAddressPage                     extends Pages[AlfConfirmedAddress]("buyerAddress")
  case StfSellerAddressPage                     extends Pages[AlfConfirmedAddress]("sellerAddress")
  case TaxRatePage                              extends Pages[TaxRate]("taxRate")
  case TotalMarketValuePage                     extends Pages[BigDecimal]("totalMarketValuePage")
  case WhatReliefAreYouApplyingForPage          extends Pages[String]("whatReliefAreYouApplyingFor")
  case WhatTypeOfSecuritiesPage                 extends Pages[WhatTypeOfSecurities]("whatTypeOfSecurities")

object Pages:
  def getData[A: Reads]: Pages[A] => TransferItem => A =
    page => d => (d.data \ page.path).as[A]
