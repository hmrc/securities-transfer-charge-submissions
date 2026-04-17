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
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.TfBoolean
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.{SingleTransferDeclaration, SingleTransferRequest}

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
    singles: Seq[SingleTransferRequest],
    declaration: SingleTransferDeclaration,
    submissionId: String
  ): Seq[StcTransactionCreateRequest] =
    toRequestsInternal(singles, declaration = declaration, submissionId = submissionId)

  private def toRequestsInternal(
    singles: Seq[SingleTransferRequest],
    declaration: SingleTransferDeclaration,
    submissionId: String
  ): Seq[StcTransactionCreateRequest] =
    val batchSize = math.max(1, appConfig.etmpCreateMaxRecordsPerRequest)
    singles
      .grouped(batchSize)
      .toSeq
      .map(toBatchRequest(_, declaration, submissionId))

  /**
   * Converts a [[StcTransactionCreateResponse]] back into a per-record
   * sequence of [[SingleTransferResponse]].
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
  def toSingleTransferResponses(
    request: StcTransactionCreateRequest,
    response: StcTransactionCreateResponse
  ): Seq[SingleTransferResponse] =
    response match
      case StcTransactionCreateProcessed(body) =>
        reconcileProcessedResponse(request, body.charges)
      case StcTransactionCreateBadRequest(error) =>
        request.transactionDetails.map(td => StcChargeFailure(td.recordId, error.code, error.message))
      case StcTransactionCreateBusinessError(errors) =>
        request.transactionDetails.map(td => StcChargeFailure(td.recordId, errors.code, errors.text))


  private def toBatchRequest(
    batch: Seq[SingleTransferRequest],
    declaration: SingleTransferDeclaration,
    submissionId: String
  ): StcTransactionCreateRequest =
    require(batch.nonEmpty, "batch must not be empty")

    StcTransactionCreateRequest(
      submissionId = submissionId,
      transactionDetails = batch.map { r =>
        val td = r.transactionDetails
        TransactionDetailsCreate(
          recordId                      = r.recordId,
          transactionType               = td.transactionType,
          reasonForPurchase             = td.reasonForPurchase,
          typeOfSecurity                = td.typeOfSecurity,
          numberOfShares                = td.numberOfShares,
          nominalValue                  = td.nominalValue,
          marketValue                   = td.marketValue,
          qualifyAsTreasuryShares       = td.qualifyAsTreasuryShares.map(TfBoolean.fromBoolean),
          maxPricePaid                  = td.maxPricePaid,
          minPricePaid                  = td.minPricePaid,
          originalChargingPoint         = td.originalChargingPoint,
          considerationActual           = td.considerationActual,
          isConnectedPartiesTransactions = TfBoolean.fromBoolean(td.isConnectedPartiesTransactions),
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
              isAmountUnasertainable = TfBoolean.fromBoolean(cd.isAmountUnasertainable),
              unascertainableAmount  = cd.unascertainableAmount,
              ascertainableAmount    = cd.ascertainableAmount,
              defermentOfPayment     = TfBoolean.fromBoolean(cd.defermentOfPayment),
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
          isPLC      = bd.isPLC.map(TfBoolean.fromBoolean)
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
        all.headOption
      },
      declaration = {
        val d = declaration
        DeclarationCreate(
          role1                 = d.role1,
          role2                 = d.role2,
          name                  = d.name,
          addr1                 = d.addr1,
          addr2                 = d.addr2,
          addr3                 = d.addr3,
          addr4                 = d.addr4,
          postcode              = d.postcode,
          country               = d.country,
          selfDeclarationAgent  = d.selfDeclarationAgent.map(TfBoolean.fromBoolean),
          isCorrectInfo         = TfBoolean.fromBoolean(d.isCorrectInfo)
        )
      }
    )


  private def reconcileProcessedResponse(
    request: StcTransactionCreateRequest,
    charges: Seq[StcCharge]
  ): Seq[SingleTransferResponse] =
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
