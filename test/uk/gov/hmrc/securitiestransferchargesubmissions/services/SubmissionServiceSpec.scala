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

package uk.gov.hmrc.securitiestransferchargesubmissions.services

import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcChargeFailure
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{TransferBatchContext, TransferBatchRequest, TransferData, TransferType, TransformationFailure}
import uk.gov.hmrc.securitiestransferchargesubmissions.validation.{TransformationValidationOutcome, TransferTransformationValidator, TransferTransformationValidatorImpl}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmissionServiceSpec extends AnyWordSpec with Matchers with ScalaFutures:

  private given HeaderCarrier = HeaderCarrier()

  private val appConfig = new AppConfig(
    Configuration.from(
      Map(
        "appName" -> "test",
        "microservice.services.etmp-transaction.host" -> "localhost",
        "microservice.services.etmp-transaction.port" -> 123,
        "microservice.services.etmp-transaction.create.max-records-per-request" -> 12,
        "microservice.services.etmp-transaction.create.max-concurrent-calls" -> 3
      )
    )
  )

  private def batchRequest(submissionId: String, size: Int = 2): TransferBatchRequest =
    TransferBatchRequest(
      transferType = TransferType.STF,
      subscriptionId = "stc-123",
      submissionId = submissionId,
      submitterAffinity = AffinityGroup.Individual,
      transfers = List.fill(size)(TransferData(Json.obj()))
    )

  "SubmissionServiceImpl.submitMultipleTransfers" should:
    "submit validated requests to the connector and return submitted responses" in:
      val requests = Seq(singleRecordRequest(1), singleRecordRequest(2))
      val connectorResponses = Seq(StcChargeFailure(1, "X", "ok"), StcChargeFailure(2, "X", "ok"))

      var capturedStcId: Option[String] = None
      var capturedTransfers: Seq[StcTransactionCreateSingleRecordRequest] = Seq.empty
      var capturedContext: Option[TransferBatchContext] = None
      var capturedData: Seq[TransferData] = Seq.empty

      val validator = new TransferTransformationValidator:
        override def validate(context: TransferBatchContext, data: Seq[TransferData]): TransformationValidationOutcome =
          capturedContext = Some(context)
          capturedData = data
          TransformationValidationOutcome.Valid(requests)

      val connector = new SubmissionConnector:
        override def submitTransfers(
          stcId: String,
          correlationId: String,
          transfers: Seq[StcTransactionCreateSingleRecordRequest]
        )(using HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
          capturedStcId = Some(stcId)
          capturedTransfers = transfers
          Future.successful(connectorResponses)

      val service = new SubmissionServiceImpl(connector, validator)

      val request = batchRequest("sub-1")
      val outcome = service.submitMultipleTransfers(request).futureValue

      capturedStcId shouldBe Some("stc-123")
      capturedTransfers shouldBe requests
      capturedContext shouldBe Some(request.context)
      capturedData shouldBe request.transfers
      val SubmissionOutcome.Submitted(responses) = outcome: @unchecked
      responses shouldBe connectorResponses

    "return transformation failures and not call connector" in:
      var connectorCalled = false

      val validator = new TransferTransformationValidator:
        override def validate(context: TransferBatchContext, data: Seq[TransferData]): TransformationValidationOutcome =
          TransformationValidationOutcome.Invalid(
            data.zipWithIndex.map { case (_, idx) =>
              TransformationFailure(
                recordId = idx + 1,
                requestIndex = idx,
                errorCode = ErrorMessages.InvalidRequestCode,
                errorText = s"invalid-transfer-${idx + 1}"
              )
            }
          )

      val connector = new SubmissionConnector:
        override def submitTransfers(
          stcId: String,
          correlationId: String,
          transfers: Seq[StcTransactionCreateSingleRecordRequest]
        )(using HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
          connectorCalled = true
          Future.successful(Seq.empty)

      val service = new SubmissionServiceImpl(connector, validator)

      val outcome = service.submitMultipleTransfers(batchRequest("sub-1", size = 3)).futureValue

      connectorCalled shouldBe false
      val SubmissionOutcome.TransformationFailed(errors) = outcome: @unchecked
      errors.map(_.recordId) shouldBe Seq(1, 2, 3)
      errors.map(_.errorText).distinct shouldBe Seq("invalid-transfer-1", "invalid-transfer-2", "invalid-transfer-3")

    "return all transformation failures and not call the connector" in:
      val transformedRecordIds = ArrayBuffer.empty[Int]
      var connectorCalled = false

      val transformer = new SubmissionTransformer(appConfig):
        override def toSingleRecordRequest(
          recordId: Int,
          context: TransferBatchContext,
          data: TransferData
        ): StcTransactionCreateSingleRecordRequest =
          transformedRecordIds += recordId
          throw new IllegalArgumentException(s"invalid-transfer-$recordId")

      val connector = new SubmissionConnector:
        override def submitTransfers(
          stcId: String,
          correlationId: String,
          transfers: Seq[StcTransactionCreateSingleRecordRequest]
        )(using HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
          connectorCalled = true
          Future.successful(Seq.empty)

      val validator = new TransferTransformationValidatorImpl(transformer)
      val service = new SubmissionServiceImpl(connector, validator)

      val outcome = service.submitMultipleTransfers(batchRequest("sub-1")).futureValue

      transformedRecordIds.toSeq shouldBe Seq(1, 2)
      connectorCalled shouldBe false

      val SubmissionOutcome.TransformationFailed(errors) = outcome: @unchecked
      errors.map(_.recordId) shouldBe Seq(1, 2)
      errors.map(_.requestIndex) shouldBe Seq(0, 1)
      errors.map(_.errorText) shouldBe Seq("invalid-transfer-1", "invalid-transfer-2")

  private def singleRecordRequest(recordId: Int): StcTransactionCreateSingleRecordRequest =
    StcTransactionCreateSingleRecordRequest(
      recordId = recordId,
      submissionId = s"sub-$recordId",
      transactionDetails = TransactionDetailsCreateSingleRecord(
        transactionType = 1,
        reasonForPurchase = None,
        descriptionOfSecurity = s"security-$recordId",
        numberOfShares = 1,
        nominalValue = None,
        marketValue = Some(BigDecimal(1)),
        qualifyAsTreasuryShares = None,
        maxPricePaid = None,
        minPricePaid = None,
        originalChargingPoint = java.time.LocalDate.of(2026, 3, 30),
        considerationActual = BigDecimal(1),
        isConnectedPartiesTransactions = "N",
        companyName = "Company",
        companyRegistrationNumber = None,
        reliefClaimedName = None,
        reliefPercentage = None
      ),
      contingentDetails = None,
      mainSellerDetails = SellerDetailsCreateSingleRecord(
        sellerName = "Seller",
        addr1 = "1 Street",
        addr2 = None,
        addr3 = None,
        addr4 = None,
        postcode = "AA1 1AA",
        country = "GB"
      ),
      otherSellers = None,
      mainBuyerDetails = BuyerDetailsCreateSingleRecord(
        buyerName = "Buyer",
        addr1 = "2 Street",
        addr2 = None,
        addr3 = None,
        addr4 = None,
        postcode = "AA1 1AA",
        country = "GB",
        email = "buyer@example.com",
        uniqueId = None,
        taxRate = 1,
        isPLC = None
      ),
      otherBuyers = None,
      agentDetails = None,
      declaration = DeclarationCreateSingleRecord(
        role1 = Some("Individual"),
        role2 = None,
        name = "Declarant",
        addr1 = "3 Street",
        addr2 = None,
        addr3 = None,
        addr4 = None,
        postcode = "AA1 1AA",
        country = "GB",
        selfDeclarationAgent = None,
        isCorrectInfo = "Y"
      )
    )
