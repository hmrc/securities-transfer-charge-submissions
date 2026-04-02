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

package uk.gov.hmrc.securitiestransferchargesubmissions.validation

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{TransferBatchContext, TransferData, TransferType}

class TransferTransformationValidatorSpec extends AnyWordSpec with Matchers:

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

  private val context =
    TransferBatchContext(
      transferType = TransferType.STF,
      subscriptionId = "stc-123",
      submissionId = "sub-1",
      submitterAffinity = AffinityGroup.Individual
    )

  private def transferData: TransferData = TransferData(Json.obj())

  "TransferTransformationValidatorImpl.validate" should:
    "return valid with transformed requests when all items pass" in:
      val transformer = new SubmissionTransformer(appConfig):
        override def toSingleRecordRequest(
          recordId: Int,
          context: TransferBatchContext,
          data: TransferData
        ): StcTransactionCreateSingleRecordRequest =
          singleRecordRequest(recordId)

      val validator = new TransferTransformationValidatorImpl(transformer)

      val outcome = validator.validate(context, Seq(transferData, transferData, transferData))

      val TransformationValidationOutcome.Valid(requests) = outcome: @unchecked
      requests.map(_.recordId) shouldBe Seq(1, 2, 3)

    "return invalid with all errors and request indexes" in:
      val transformer = new SubmissionTransformer(appConfig):
        override def toSingleRecordRequest(
          recordId: Int,
          context: TransferBatchContext,
          data: TransferData
        ): StcTransactionCreateSingleRecordRequest =
          throw new IllegalArgumentException(s"invalid-transfer-$recordId")

      val validator = new TransferTransformationValidatorImpl(transformer)

      val outcome = validator.validate(context, Seq(transferData, transferData))

      val TransformationValidationOutcome.Invalid(errors) = outcome: @unchecked
      errors.map(_.recordId) shouldBe Seq(1, 2)
      errors.map(_.requestIndex) shouldBe Seq(0, 1)
      errors.map(_.errorCode) shouldBe Seq("INVALID_REQUEST", "INVALID_REQUEST")
      errors.map(_.errorText) shouldBe Seq("invalid-transfer-1", "invalid-transfer-2")

    "return invalid when one transfer fails transformation" in:
      val transformer = new SubmissionTransformer(appConfig):
        override def toSingleRecordRequest(
          recordId: Int,
          context: TransferBatchContext,
          data: TransferData
        ): StcTransactionCreateSingleRecordRequest =
          if recordId == 2 then throw new IllegalArgumentException("invalid-transfer-2")
          else singleRecordRequest(recordId)

      val validator = new TransferTransformationValidatorImpl(transformer)

      val outcome = validator.validate(context, Seq(transferData, transferData, transferData))

      val TransformationValidationOutcome.Invalid(errors) = outcome: @unchecked
      errors.map(_.recordId) shouldBe Seq(2)
      errors.map(_.requestIndex) shouldBe Seq(1)
      errors.map(_.errorText) shouldBe Seq("invalid-transfer-2")

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
