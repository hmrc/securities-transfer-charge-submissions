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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.api.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{BuyerTaxRate, DeclarationRole, TransferType}

import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.collection.mutable.ArrayBuffer

class SubmissionConnectorSpec extends AnyWordSpec with Matchers:

  given HeaderCarrier = HeaderCarrier()

  private val declaration = SingleTransferDeclaration(
    role1 = Some(DeclarationRole.Director),
    role2 = None,
    name = "Declarer",
    addr1 = "addr1",
    addr2 = None,
    addr3 = None,
    addr4 = None,
    postcode = "AA11AA",
    country = "GB",
    selfDeclarationAgent = None,
    isCorrectInfo = true
  )

  private def appConfig(maxRecordsPerRequest: Int, maxConcurrentCalls: Int = 1): AppConfig =
    new AppConfig(
      Configuration.from(
        Map(
          "appName" -> "test",
          "microservice.services.etmp-transaction.host" -> "localhost",
          "microservice.services.etmp-transaction.port" -> 123,
          "microservice.services.etmp-transaction.create.max-records-per-request" -> maxRecordsPerRequest,
          "microservice.services.etmp-transaction.create.max-concurrent-calls" -> maxConcurrentCalls
        )
      )
    )

  private def singleRequest(recordId: Int): SingleTransferRequest =
    SingleTransferRequest(
      recordId = recordId,
      transactionDetails = SingleTransferTransactionDetails(
        transactionType = TransferType.STF,
        reasonForPurchase = None,
        descriptionOfSecurity = s"security-$recordId",
        numberOfShares = 10,
        nominalValue = None,
        marketValue = None,
        qualifyAsTreasuryShares = None,
        maxPricePaid = None,
        minPricePaid = None,
        originalChargingPoint = LocalDate.parse("2026-03-30"),
        considerationActual = BigDecimal(100),
        isConnectedPartiesTransactions = false,
        companyName = "Buyer Ltd",
        companyRegistrationNumber = None,
        reliefClaimedName = None,
        reliefPercentage = None
      ),
      contingentDetails = None,
      mainSellerDetails = SingleTransferSellerDetails("Seller Ltd", "addr1", None, None, None, "AA11AA", "GB"),
      otherSellers = None,
      mainBuyerDetails = SingleTransferBuyerDetails("Buyer Ltd", "addr1", None, None, None, "BB11BB", "GB", "buyer@test.com", None, BuyerTaxRate.HalfPercent, None),
      otherBuyers = None,
      agentDetails = None
    )

  private def connectorWithStubClient(maxRecordsPerRequest: Int): (SubmissionConnectorImpl, AtomicInteger, ArrayBuffer[Int]) =
    val cfg = appConfig(maxRecordsPerRequest)
    val callCount = new AtomicInteger(0)
    val batchSizes = ArrayBuffer.empty[Int]

    val submissionClient = new SubmissionClient:
      override def submitTransfer(
        stcId: String,
        correlationId: String,
        request: StcTransactionCreateRequest
      )(using uk.gov.hmrc.http.HeaderCarrier): Future[StcTransactionCreateResponse] =
        callCount.incrementAndGet()
        batchSizes.synchronized {
          batchSizes += request.transactionDetails.size
        }
        Future.successful(
          StcTransactionCreateProcessed(
            StcTransactionCreateProcessedBody(
              processingDate = "2026-03-30T12:00:00Z",
              charges = request.transactionDetails.map { td =>
                StcChargeSuccess(
                  recordId = td.recordId,
                  utrn = s"utrn-${td.recordId}",
                  chargeTypeDescription = "Charge",
                  chargeReference = s"ref-${td.recordId}",
                  chargeType = "STF",
                  chargeAmount = BigDecimal(10),
                  chargeDueDate = "2026-04-30"
                )
              }.toList
            )
          )
        )

    val transformer = new SubmissionTransformer(cfg)

    (new SubmissionConnectorImpl(submissionClient, transformer, cfg), callCount, batchSizes)

  private def connectorWithConcurrencyTracking(
    maxRecordsPerRequest: Int,
    maxConcurrentCalls: Int,
    delayMs: Long
  ): (SubmissionConnectorImpl, AtomicInteger) =
    val cfg = appConfig(maxRecordsPerRequest, maxConcurrentCalls)
    val inFlight = new AtomicInteger(0)
    val maxObserved = new AtomicInteger(0)

    val submissionClient = new SubmissionClient:
      override def submitTransfer(
        stcId: String,
        correlationId: String,
        request: StcTransactionCreateRequest
      )(using uk.gov.hmrc.http.HeaderCarrier): Future[StcTransactionCreateResponse] =
        val nowInFlight = inFlight.incrementAndGet()
        maxObserved.updateAndGet(prev => math.max(prev, nowInFlight))

        Future {
          Thread.sleep(delayMs)
          StcTransactionCreateProcessed(
            StcTransactionCreateProcessedBody(
              processingDate = "2026-03-30T12:00:00Z",
              charges = request.transactionDetails.map { td =>
                StcChargeSuccess(td.recordId, s"utrn-${td.recordId}", "Charge", s"ref-${td.recordId}", "STF", BigDecimal(10), "2026-04-30")
              }.toList
            )
          )
        }.andThen { case _ => inFlight.decrementAndGet() }

    val transformer = new SubmissionTransformer(cfg)

    (new SubmissionConnectorImpl(submissionClient, transformer, cfg), maxObserved)

  "SubmissionConnectorImpl.submitTransfers" should:
    "reject an empty transfer sequence" in:
      val (connector, callCount, _) = connectorWithStubClient(maxRecordsPerRequest = 3)
      val exception = the[IllegalArgumentException] thrownBy {
        connector.submitTransfers("stcId", "submission-123", "correlationId", declaration, Seq.empty)
      }

      exception.getMessage should include("transfers must not be empty")
      callCount.get() shouldBe 0

    "return one response when one request is submitted" in:
      val (connector, callCount, batchSizes) = connectorWithStubClient(maxRecordsPerRequest = 3)

      val result = Await.result(
        connector.submitTransfers("stcId", "submission-123", "correlationId", declaration, Seq(singleRequest(1))),
        5.seconds
      )

      result.map(_.recordId) shouldBe Seq(1)
      callCount.get() shouldBe 1
      batchSizes.toSeq shouldBe Seq(1)

    "return n responses for n requests when n is less than or equal to max records per request" in:
      val (connector, callCount, batchSizes) = connectorWithStubClient(maxRecordsPerRequest = 3)
      val transfers = Seq(singleRequest(1), singleRequest(2), singleRequest(3))

      val result = Await.result(
        connector.submitTransfers("stcId", "submission-123", "correlationId", declaration, transfers),
        5.seconds
      )

      result.map(_.recordId) shouldBe Seq(1, 2, 3)
      result.size shouldBe 3
      callCount.get() shouldBe 1
      batchSizes.toSeq shouldBe Seq(3)

    "return m responses for m requests when m is greater than max records per request" in:
      val (connector, callCount, batchSizes) = connectorWithStubClient(maxRecordsPerRequest = 3)
      val transfers = (1 to 8).map(singleRequest)

      val result = Await.result(
        connector.submitTransfers("stcId", "submission-123", "correlationId", declaration, transfers),
        5.seconds
      )

      result.map(_.recordId) shouldBe (1 to 8)
      result.size shouldBe 8
      callCount.get() shouldBe 3
      batchSizes.toSeq shouldBe Seq(3, 3, 2)

    "respect max concurrent ETMP calls when configured above one" in:
      val (connector, maxObserved) = connectorWithConcurrencyTracking(
        maxRecordsPerRequest = 1,
        maxConcurrentCalls = 2,
        delayMs = 40
      )
      val transfers = (1 to 8).map(singleRequest)

      val result = Await.result(
        connector.submitTransfers("stcId", "submission-123", "correlationId", declaration, transfers),
        5.seconds
      )

      result.size shouldBe 8
      maxObserved.get() shouldBe 2

    "default to one concurrent ETMP call when max concurrent calls is configured as zero" in:
      val (connector, maxObserved) = connectorWithConcurrencyTracking(
        maxRecordsPerRequest = 1,
        maxConcurrentCalls = 0,
        delayMs = 40
      )
      val transfers = (1 to 6).map(singleRequest)

      val result = Await.result(
        connector.submitTransfers("stcId", "submission-123", "correlationId", declaration, transfers),
        5.seconds
      )

      result.size shouldBe 6
      maxObserved.get() shouldBe 1

    "return one response per input and synthesize 500 failures for downstream batch errors" in:
      val cfg = appConfig(maxRecordsPerRequest = 2)

      val submissionClient = new SubmissionClient:
        override def submitTransfer(
          stcId: String,
          correlationId: String,
          request: StcTransactionCreateRequest
        )(using uk.gov.hmrc.http.HeaderCarrier): Future[StcTransactionCreateResponse] =
          val recordIds = request.transactionDetails.map(_.recordId)

          if recordIds == Seq(3, 4) then
            Future.failed(new RuntimeException("boom"))
          else
            Future.successful(
              StcTransactionCreateProcessed(
                StcTransactionCreateProcessedBody(
                  processingDate = "2026-03-30T12:00:00Z",
                  charges = request.transactionDetails.map { td =>
                    StcChargeSuccess(td.recordId, s"utrn-${td.recordId}", "Charge", s"ref-${td.recordId}", "STF", BigDecimal(10), "2026-04-30")
                  }.toList
                )
              )
            )

      val transformer = new SubmissionTransformer(cfg)

      val connector = new SubmissionConnectorImpl(submissionClient, transformer, cfg)
      val transfers = (1 to 5).map(singleRequest)

      val result = Await.result(
        connector.submitTransfers("stcId", "submission-123", "correlationId", declaration, transfers),
        5.seconds
      )

      result.size shouldBe transfers.size
      result.map(_.recordId).toSet shouldBe Set(1, 2, 3, 4, 5)
      result.collect { case StcChargeFailure(recordId, "500", "Failed to submit transfer to ETMP") => recordId }.toSet shouldBe Set(3, 4)

    "return matching responses even when ETMP futures complete out of order" in:
      val cfg = appConfig(maxRecordsPerRequest = 1, maxConcurrentCalls = 3)
      val completionOrder = ArrayBuffer.empty[Int]
      val promises = (1 to 3).map(recordId => recordId -> Promise[StcTransactionCreateResponse]()).toMap

      val submissionClient = new SubmissionClient:
        override def submitTransfer(
          stcId: String,
          correlationId: String,
          request: StcTransactionCreateRequest
        )(using uk.gov.hmrc.http.HeaderCarrier): Future[StcTransactionCreateResponse] =
          val recordId = request.transactionDetails.head.recordId
          promises(recordId).future

      val transformer = new SubmissionTransformer(cfg)

      val connector = new SubmissionConnectorImpl(submissionClient, transformer, cfg)
      val transfers = Seq(singleRequest(1), singleRequest(2), singleRequest(3))

      val result = Await.result(
        {
          val resultF = connector.submitTransfers("stcId", "submission-123", "correlationId", declaration, transfers)

          Seq(3, 2, 1).foreach { recordId =>
            completionOrder.synchronized {
              completionOrder += recordId
            }
            promises(recordId).success(
              StcTransactionCreateProcessed(
                StcTransactionCreateProcessedBody(
                  processingDate = "2026-03-30T12:00:00Z",
                  charges = List(
                    StcChargeSuccess(recordId, s"utrn-$recordId", "Charge", s"ref-$recordId", "STF", BigDecimal(10), "2026-04-30")
                  )
                )
              )
            )
          }

          resultF
        },
        5.seconds
      )

      completionOrder.toSeq shouldBe Seq(3, 2, 1)
      result.map(_.recordId).toSet shouldBe Set(1, 2, 3)
