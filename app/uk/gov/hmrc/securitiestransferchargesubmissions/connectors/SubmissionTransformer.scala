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

import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.controllers.TransferData

import javax.inject.{Inject, Singleton}

@Singleton
class SubmissionTransformer @Inject()(appConfig: AppConfig):

  /**
   * Groups the incoming single-record requests into batches of at most
   * `etmpCreateMaxRecordsPerRequest` and transforms each batch into a
   * multi-record [[StcTransactionCreateRequest]].
   *
   * Returns pairs of (original batch, combined request) so that the
   * caller can match each response back to the records it covers.
   */
  def toRequests(
    singles: Seq[StcTransactionCreateSingleRecordRequest]
  ): Seq[(Seq[StcTransactionCreateSingleRecordRequest], StcTransactionCreateRequest)] =
    singles
      .grouped(appConfig.etmpCreateMaxRecordsPerRequest)
      .toSeq
      .map(batch => (batch, toBatchRequest(batch)))

  /**
   * Converts a [[StcTransactionCreateResponse]] back into a per-record
   * sequence of [[StcTransactionCreateSingleRecordResponse]].
   *
   * For a successful 2XX response the charges in the body are returned
   * directly — each already carries the correct `recordId`.
   *
   * For a non-2XX response ([[StcTransactionCreateBadRequest]] or
   * [[StcTransactionCreateBusinessError]]) an [[StcChargeFailure]] is
   * synthesised for every record in the originating batch so that the
   * caller always receives one response entry per submitted record.
   */
  def toSingleRecordResponses(
    batch: Seq[StcTransactionCreateSingleRecordRequest],
    response: StcTransactionCreateResponse
  ): Seq[StcTransactionCreateSingleRecordResponse] =
    response match
      case StcTransactionCreateProcessed(body) =>
        body.charges
      case StcTransactionCreateBadRequest(error) =>
        batch.map(r => StcChargeFailure(r.recordId, error.code, error.message))
      case StcTransactionCreateBusinessError(errors) =>
        batch.map(r => StcChargeFailure(r.recordId, errors.code, errors.text))

  /**
   * Converts a single [[TransferData]] into a [[StcTransactionCreateSingleRecordRequest]].
   * TODO: implement field mapping from TransferData to the ETMP request model.
   */
  def toSingleRecordRequest(data: TransferData): StcTransactionCreateSingleRecordRequest = ???

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

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
