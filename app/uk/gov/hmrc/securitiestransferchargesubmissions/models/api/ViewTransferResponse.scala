/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.securitiestransferchargesubmissions.models.api

import play.api.libs.json.{Json, OWrites, Reads}

import java.time.LocalDate

/**
 * Response body for GET /submission/:utrn.
 *
 * Mirrors the shape accepted by POST /submission/:submissionId ([[SingleTransferRequest]] plus its
 * [[SingleTransferDeclaration]]), so callers read back data in the same format they submitted it.
 */
final case class ViewTransferResponse(
  processingDate: String,
  submissionId: String,
  submissionDate: LocalDate,
  recordId: Int,
  transactionDetails: SingleTransferTransactionDetails,
  contingentDetails: Seq[ViewTransferContingentDetails],
  mainSellerDetails: Option[SingleTransferSellerDetails],
  otherSellers: Seq[SingleTransferOtherSellerName],
  mainBuyerDetails: SingleTransferBuyerDetails,
  otherBuyers: Seq[SingleTransferOtherBuyerName],
  agentDetails: Option[SingleTransferAgentDetails],
  declaration: SingleTransferDeclaration,
  charges: Seq[ViewTransferCharge]
)

/**
 * The fields of [[SingleTransferContingentDetails]] plus those only set by a later amend:
 * `provisionalId` identifies the contingent record, and the `actual*` / extension fields hold
 * the amended values.
 */
final case class ViewTransferContingentDetails(
  provisionalId: String,
  provisionalDate: LocalDate,
  isAmountUnasertainable: Boolean,
  unascertainableAmount: Option[BigDecimal],
  ascertainableAmount: Option[BigDecimal],
  defermentOfPayment: Boolean,
  originalDefermentDate: Option[LocalDate],
  actualDate: Option[LocalDate],
  unascertainableAmountActual: Option[BigDecimal],
  ascertainableAmountActual: Option[BigDecimal],
  defermentExtensionDate: Option[LocalDate]
)

final case class ViewTransferCharge(
  chargeTypeDescription: String,
  chargeReference: String,
  chargeType: String,
  chargeAmount: BigDecimal,
  chargeDueDate: String
)

object ViewTransferResponse:
  import SingleTransferRequest.given

  given OWrites[ViewTransferContingentDetails] = Json.writes[ViewTransferContingentDetails]
  given Reads[ViewTransferContingentDetails] = Json.reads[ViewTransferContingentDetails]
  given OWrites[ViewTransferCharge] = Json.writes[ViewTransferCharge]
  given Reads[ViewTransferCharge] = Json.reads[ViewTransferCharge]
  given OWrites[ViewTransferResponse] = Json.writes[ViewTransferResponse]
  given Reads[ViewTransferResponse] = Json.reads[ViewTransferResponse]
