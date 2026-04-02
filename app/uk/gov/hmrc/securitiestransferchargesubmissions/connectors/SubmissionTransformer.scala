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

import play.api.Logging
import play.api.libs.json.Reads
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{TransferBatchContext, TransferData, TransferType}

import javax.inject.{Inject, Singleton}

@Singleton
class SubmissionTransformer @Inject()(appConfig: AppConfig) extends Logging:

  private val MissingProcessedResponseErrorCode = "INTERNAL_SERVER_ERROR"
  private val MissingProcessedResponseErrorText =
    "ETMP did not return a processed response for this record"

  /**
   * Groups incoming single-record requests into batches of at most
   * `etmpCreateMaxRecordsPerRequest` and transforms each batch into a
   * multi-record [[StcTransactionCreateRequest]].
   *
   * All records are expected to share the same submission context from the
   * incoming batch request.
   */
  def toRequests(
    singles: Seq[StcTransactionCreateSingleRecordRequest]
  ): Seq[StcTransactionCreateRequest] =
    singles
      .grouped(appConfig.etmpCreateMaxRecordsPerRequest)
      .toSeq
      .map(toBatchRequest)

  /**
   * Converts a [[StcTransactionCreateResponse]] back into a per-record
   * sequence of [[StcTransactionCreateSingleRecordResponse]].
   *
   * For a successful 2XX response, all charges matching the requested
   * recordIds are returned to the caller in request order. Any extra
   * recordIds returned by ETMP are logged and ignored. Any requested
   * recordIds missing from the ETMP response are represented as synthetic
   * [[StcChargeFailure]] server errors.
   *
   * For a non-2XX response ([[StcTransactionCreateBadRequest]] or
   * [[StcTransactionCreateBusinessError]]) an [[StcChargeFailure]] is
   * synthesised for every record in the originating request so that the
   * caller always receives one response entry per submitted record.
   */
  def toSingleRecordResponses(
    request: StcTransactionCreateRequest,
    response: StcTransactionCreateResponse
  ): Seq[StcTransactionCreateSingleRecordResponse] =
    response match
      case StcTransactionCreateProcessed(body) =>
        reconcileProcessedResponse(request, body.charges)
      case StcTransactionCreateBadRequest(error) =>
        request.transactionDetails.map(td => StcChargeFailure(td.recordId, error.code, error.message))
      case StcTransactionCreateBusinessError(errors) =>
        request.transactionDetails.map(td => StcChargeFailure(td.recordId, errors.code, errors.text))

  /**
   * Converts a single transfer payload plus shared batch context into a
   * [[StcTransactionCreateSingleRecordRequest]].
   */
  def toSingleRecordRequest(
    recordId: Int,
    context: TransferBatchContext,
    data: TransferData
  ): StcTransactionCreateSingleRecordRequest =
    val chargingPoint = required(Pages.ChargingPointPage)(data)
    val connectedPersons = required(Pages.ConnectedPersonsPage)(data)
    val sellerName = required(Pages.NameOfSellerPage)(data)
    val sellerAddress = required(Pages.StfSellerAddressPage)(data)
    val buyerAddress = buyerAddressFor(context, data)
    val securitiesTarget = required(Pages.SecuritiesTargetPage)(data)
    val applyingForRelief = required(Pages.ApplyingForReliefPage)(data)
    val taxRate = required(Pages.TaxRatePage)(data)
    val whatTypeOfSecurities = required(Pages.WhatTypeOfSecuritiesPage)(data)

    val detailsOfTransfer =
      if whatTypeOfSecurities == WhatTypeOfSecurities.Shares then Some(required(Pages.DetailsOfThisTransferPage)(data)) else None

    val otherSecuritiesType =
      if whatTypeOfSecurities == WhatTypeOfSecurities.Other then Some(required(Pages.OtherSecuritiesTypePage)(data)) else None

    val amountPaid = whatTypeOfSecurities match
      case WhatTypeOfSecurities.Shares => detailsOfTransfer.map(_.amountPaid).getOrElse(missing("detailsOfThisTransfer.amountPaid"))
      case WhatTypeOfSecurities.Other  => required(Pages.AmountPaidForSecuritiesPage)(data)

    val totalMarketValue =
      if connectedPersons && whatTypeOfSecurities == WhatTypeOfSecurities.Other then Some(required(Pages.TotalMarketValuePage)(data))
      else optional(Pages.TotalMarketValuePage)(data)

    val reliefName = if applyingForRelief then optional(Pages.WhatReliefAreYouApplyingForPage)(data) else None

    val buyerTaxRate = taxRate match
      case TaxRate.Half       => 1
      case TaxRate.OneAndHalf => 2

    val descriptionOfSecurity = whatTypeOfSecurities match
      case WhatTypeOfSecurities.Other  => otherSecuritiesType.getOrElse(missing("otherSecuritiesType"))
      case WhatTypeOfSecurities.Shares => detailsOfTransfer.map(_.typeOfShares).getOrElse(missing("detailsOfThisTransfer.typeOfShares"))

    StcTransactionCreateSingleRecordRequest(
      recordId = recordId,
      submissionId = context.submissionId,
      transactionDetails = TransactionDetailsCreateSingleRecord(
        transactionType = toTransactionType(context.transferType),
        reasonForPurchase = None,
        descriptionOfSecurity = descriptionOfSecurity,
        numberOfShares = detailsOfTransfer.map(d => parseShareCount(d.numberOfShares)).getOrElse(0),
        nominalValue = None,
        marketValue = detailsOfTransfer.flatMap(_.marketValue).orElse(totalMarketValue),
        qualifyAsTreasuryShares = None,
        maxPricePaid = None,
        minPricePaid = None,
        originalChargingPoint = chargingPoint,
        considerationActual = amountPaid,
        isConnectedPartiesTransactions = yesNo(connectedPersons),
        companyName = securitiesTarget.businessName,
        companyRegistrationNumber = securitiesTarget.crn,
        reliefClaimedName = reliefName,
        reliefPercentage = None
      ),
      contingentDetails = None,
      mainSellerDetails = SellerDetailsCreateSingleRecord(
        sellerName = sellerName,
        addr1 = addressLine(sellerAddress.address.lines, 0),
        addr2 = sellerAddress.address.lines.lift(1),
        addr3 = sellerAddress.address.lines.lift(2),
        addr4 = None,
        postcode = sellerAddress.address.postcode,
        country = sellerAddress.address.country.code
      ),
      otherSellers = None,
      mainBuyerDetails = BuyerDetailsCreateSingleRecord(
        buyerName = securitiesTarget.businessName,
        addr1 = addressLine(buyerAddress.lines, 0),
        addr2 = buyerAddress.lines.lift(1),
        addr3 = buyerAddress.lines.lift(2),
        addr4 = None,
        postcode = buyerAddress.postcode,
        country = buyerAddress.countryCode,
        email = "unknown@example.com",
        uniqueId = None,
        taxRate = buyerTaxRate,
        isPLC = None
      ),
      otherBuyers = None,
      agentDetails = None,
      declaration = DeclarationCreateSingleRecord(
        role1 = Some(context.submitterAffinity.toString),
        role2 = None,
        name = sellerName,
        addr1 = addressLine(sellerAddress.address.lines, 0),
        addr2 = sellerAddress.address.lines.lift(1),
        addr3 = sellerAddress.address.lines.lift(2),
        addr4 = None,
        postcode = sellerAddress.address.postcode,
        country = sellerAddress.address.country.code,
        selfDeclarationAgent = None,
        isCorrectInfo = "Y"
      )
    )

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private def required[A: Reads](page: Pages[A])(data: TransferData): A =
    Pages.getData[A](using summon[Reads[A]])(page)(data)

  private def optional[A: Reads](page: Pages[A])(data: TransferData): Option[A] =
    (data.data \ page.path).asOpt[A]

  private def buyerAddressFor(context: TransferBatchContext, data: TransferData): BuyerAddressData =
    optional(Pages.StfBuyersAddressPage)(data)
      .map(BuyerAddressData.fromAlf)
      .orElse {
        optional(Pages.ConfirmAddressPage)(data).map(BuyerAddressData.fromConfirmable)
      }
      .getOrElse {
        context.submitterAffinity match
          case AffinityGroup.Individual => missing("buyerAddress or confirmedAddress for individual journey")
          case AffinityGroup.Organisation => missing("buyerAddress or confirmedAddress for organisation journey")
          case _ => missing("buyerAddress or confirmedAddress")
      }

  private def missing(path: String): Nothing =
    throw new IllegalArgumentException(s"Missing required data for path [$path]")

  private def parseShareCount(value: String): Int =
    value.toIntOption.getOrElse {
      throw new IllegalArgumentException(s"Unable to parse numberOfShares [$value] as Int")
    }

  private def addressLine(lines: List[String], idx: Int): String =
    lines.lift(idx).getOrElse("")

  private def yesNo(value: Boolean): String =
    if value then "Y" else "N"

  private case class BuyerAddressData(lines: List[String], postcode: String, countryCode: String)

  private object BuyerAddressData:
    def fromAlf(value: AlfConfirmedAddress): BuyerAddressData =
      BuyerAddressData(
        lines = value.address.lines,
        postcode = value.address.postcode,
        countryCode = value.address.country.code
      )

    def fromConfirmable(value: ConfirmableAddress): BuyerAddressData =
      BuyerAddressData(
        lines = value.lines,
        postcode = value.postcode,
        countryCode = value.country.map(_.code).getOrElse("GB")
      )

  private def toTransactionType(transferType: TransferType): Int =
    transferType match
      case TransferType.STF   => 1
      case TransferType.SH03  => 2
      case TransferType.Other => 3

  private def toBatchRequest(
    batch: Seq[StcTransactionCreateSingleRecordRequest]
  ): StcTransactionCreateRequest =
    StcTransactionCreateRequest(
      submissionId = batch.head.submissionId,
      transactionDetails = batch.map { r =>
        val td = r.transactionDetails
        TransactionDetailsCreate(
          recordId                      = r.recordId,
          transactionType               = td.transactionType,
          reasonForPurchase             = td.reasonForPurchase,
          descriptionOfSecurity         = td.descriptionOfSecurity,
          numberOfShares                = td.numberOfShares,
          nominalValue                  = td.nominalValue,
          marketValue                   = td.marketValue,
          qualifyAsTreasuryShares       = td.qualifyAsTreasuryShares,
          maxPricePaid                  = td.maxPricePaid,
          minPricePaid                  = td.minPricePaid,
          originalChargingPoint         = td.originalChargingPoint,
          considerationActual           = td.considerationActual,
          isConnectedPartiesTransactions = td.isConnectedPartiesTransactions,
          companyName                   = td.companyName,
          companyRegistrationNumber     = td.companyRegistrationNumber,
          reliefClaimedName             = td.reliefClaimedName,
          reliefPercentage              = td.reliefPercentage
        )
      },
      contingentDetails = {
        val all = batch.flatMap { r =>
          r.contingentDetails.getOrElse(Seq.empty).map { cd =>
            ContingentDetailsCreate(
              recordId               = r.recordId,
              provisionalDate        = cd.provisionalDate,
              isAmountUnasertainable = cd.isAmountUnasertainable,
              unascertainableAmount  = cd.unascertainableAmount,
              ascertainableAmount    = cd.ascertainableAmount,
              defermentOfPayment     = cd.defermentOfPayment,
              originalDefermentDate  = cd.originalDefermentDate
            )
          }
        }
        if all.isEmpty then None else Some(all)
      },
      mainSellerDetails = batch.map { r =>
        val sd = r.mainSellerDetails
        SellerDetailsCreate(
          recordId   = r.recordId,
          sellerName = sd.sellerName,
          addr1      = sd.addr1,
          addr2      = sd.addr2,
          addr3      = sd.addr3,
          addr4      = sd.addr4,
          postcode   = sd.postcode,
          country    = sd.country
        )
      },
      otherSellers = {
        val all = batch.flatMap { r =>
          r.otherSellers.getOrElse(Seq.empty).map { os =>
            OtherSellerCreateName(recordId = r.recordId, sellerName = os.sellerName)
          }
        }
        if all.isEmpty then None else Some(all)
      },
      mainBuyerDetails = batch.map { r =>
        val bd = r.mainBuyerDetails
        BuyerDetailsCreate(
          recordId   = r.recordId,
          buyerName  = bd.buyerName,
          addr1      = bd.addr1,
          addr2      = bd.addr2,
          addr3      = bd.addr3,
          addr4      = bd.addr4,
          postcode   = bd.postcode,
          country    = bd.country,
          email      = bd.email,
          uniqueId   = bd.uniqueId,
          taxRate    = bd.taxRate,
          isPLC      = bd.isPLC
        )
      },
      otherBuyers = {
        val all = batch.flatMap { r =>
          r.otherBuyers.getOrElse(Seq.empty).map { ob =>
            OtherBuyerCreateName(recordId = r.recordId, buyerName = ob.buyerName)
          }
        }
        if all.isEmpty then None else Some(all)
      },
      agentDetails = {
        val all = batch.flatMap { r =>
          r.agentDetails.getOrElse(Seq.empty).map { ad =>
            AgentDetailsCreate(
              recordId        = r.recordId,
              name            = ad.name,
              addr1           = ad.addr1,
              addr2           = ad.addr2,
              addr3           = ad.addr3,
              addr4           = ad.addr4,
              postcode        = ad.postcode,
              country         = ad.country,
              phone           = ad.phone,
              email           = ad.email,
              clientReference = ad.clientReference
            )
          }
        }
        if all.isEmpty then None else Some(all)
      },
      declaration = batch.map { r =>
        val d = r.declaration
        DeclarationCreate(
          recordId              = r.recordId,
          role1                 = d.role1,
          role2                 = d.role2,
          name                  = d.name,
          addr1                 = d.addr1,
          addr2                 = d.addr2,
          addr3                 = d.addr3,
          addr4                 = d.addr4,
          postcode              = d.postcode,
          country               = d.country,
          selfDeclarationAgent  = d.selfDeclarationAgent,
          isCorrectInfo         = d.isCorrectInfo
        )
      }
    )

  private def reconcileProcessedResponse(
    request: StcTransactionCreateRequest,
    charges: Seq[StcCharge]
  ): Seq[StcTransactionCreateSingleRecordResponse] =
    val expectedRecordIds = request.transactionDetails.map(_.recordId)
    val expectedRecordIdSet = expectedRecordIds.toSet

    val matchingCharges = charges.filter(charge => expectedRecordIdSet.contains(charge.recordId))
    val extraRecordIds = charges.map(_.recordId).filterNot(expectedRecordIdSet.contains).distinct

    if extraRecordIds.nonEmpty then
      logger.warn(
        s"Ignoring ETMP processed response charges with unexpected recordIds " +
          s"[${extraRecordIds.mkString(",")}] for submissionId [${request.submissionId}]"
      )

    val matchingChargesByRecordId = matchingCharges.groupBy(_.recordId)

    expectedRecordIds.flatMap { recordId =>
      matchingChargesByRecordId.getOrElse(
        recordId,
        Seq(
          StcChargeFailure(
            recordId = recordId,
            errorCode = MissingProcessedResponseErrorCode,
            errorText = MissingProcessedResponseErrorText
          )
        )
      )
    }
