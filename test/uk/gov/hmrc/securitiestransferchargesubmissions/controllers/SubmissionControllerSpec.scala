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

package uk.gov.hmrc.securitiestransferchargesubmissions.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{StcTransactionCreateSingleRecordRequest, StcTransactionCreateSingleRecordResponse, SubmissionConnector, SubmissionTransformer}
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SubmissionControllerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  private given ActorSystem = ActorSystem("SubmissionControllerSpec")
  private given Materializer = Materializer.matFromSystem
  private val controllerComponents = Helpers.stubControllerComponents()

  private val connector = new SubmissionConnector:
    override def submitTransfers(
      stcId: String,
      correlationId: String,
      transfers: Seq[StcTransactionCreateSingleRecordRequest]
    )(using HeaderCarrier): Future[Seq[StcTransactionCreateSingleRecordResponse]] =
      fail("SubmissionConnector should not be called for an empty payload")

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

  private val transformer = new SubmissionTransformer(appConfig):
    override def toSingleRecordRequest(recordId: Int, data: TransferData) =
      fail("SubmissionTransformer should not be called for an empty payload")

  private val controller = new SubmissionControllerImpl(
    controllerComponents,
    connector,
    transformer
  )

  private val realTransformer = new SubmissionTransformer(appConfig)

  private val controllerWithRealTransformer = new SubmissionControllerImpl(
    controllerComponents,
    connector,
    realTransformer
  )

  private val failingTransformer = new SubmissionTransformer(appConfig):
    override def toSingleRecordRequest(recordId: Int, data: TransferData) =
      throw new IllegalArgumentException(s"invalid-transfer-$recordId")

  private val controllerWithFailingTransformer = new SubmissionControllerImpl(
    controllerComponents,
    connector,
    failingTransformer
  )

  "SubmissionControllerImpl.submitMultipleTransfers" should:
    "return 400 for an empty JSON array" in:
      val request = FakeRequest("POST", "/submission/multiple").withBody(Json.arr())

      val result = controller.submitMultipleTransfers.apply(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("at least one transfer must be provided")

    "return 400 with all transformation failures instead of failing fast" in:
      val request = FakeRequest("POST", "/submission/multiple").withBody(
        Json.arr(
          Json.obj(
            "transferType" -> 1,
            "subscriptionId" -> "stc-123",
            "submissionId" -> "sub-123",
            "submitterAffinity" -> "Individual",
            "data" -> Json.obj()
          ),
          Json.obj(
            "transferType" -> 1,
            "subscriptionId" -> "stc-123",
            "submissionId" -> "sub-123",
            "submitterAffinity" -> "Individual",
            "data" -> Json.obj()
          )
        )
      )

      val result = controllerWithFailingTransformer.submitMultipleTransfers.apply(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("invalid transfer data")
      contentAsString(result) should include("\"recordId\":1")
      contentAsString(result) should include("\"recordId\":2")
      contentAsString(result) should include("invalid-transfer-1")
      contentAsString(result) should include("invalid-transfer-2")

  "SubmissionControllerImpl.submitSingleTransfer" should:
    "return 400 when transfer data is missing required page values" in:
      val request = FakeRequest("POST", "/submission/single").withBody(
        Json.obj(
          "transferType" -> 1,
          "subscriptionId" -> "stc-123",
          "submissionId" -> "sub-123",
          "submitterAffinity" -> "Individual",
          "data" -> Json.obj()
        )
      )

      val result = controllerWithRealTransformer.submitSingleTransfer.apply(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("invalid transfer data")

  override def afterAll(): Unit =
    Await.result(summon[ActorSystem].terminate(), 5.seconds)
    super.afterAll()
