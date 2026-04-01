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
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcChargeFailure
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{TransferData, TransformationFailure}
import uk.gov.hmrc.securitiestransferchargesubmissions.services.{SubmissionOutcome, SubmissionService}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SubmissionControllerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  private given ActorSystem = ActorSystem("SubmissionControllerSpec")
  private given Materializer = Materializer.matFromSystem
  private val controllerComponents = Helpers.stubControllerComponents()

  private val successService = new SubmissionService:
    override def submitSingleTransfer(data: TransferData)(using hc: HeaderCarrier): Future[SubmissionOutcome] =
      Future.successful(SubmissionOutcome.Submitted(Seq(StcChargeFailure(1, "INVALID_REQUEST", "x"))))

    override def submitMultipleTransfers(data: Seq[TransferData])(using hc: HeaderCarrier): Future[SubmissionOutcome] =
      Future.successful(SubmissionOutcome.Submitted(Seq(StcChargeFailure(1, "INVALID_REQUEST", "x"))))

  private val failingService = new SubmissionService:
    override def submitSingleTransfer(data: TransferData)(using hc: HeaderCarrier): Future[SubmissionOutcome] =
      Future.successful(
        SubmissionOutcome.TransformationFailed(
          Seq(TransformationFailure(1, 0, "INVALID_REQUEST", "invalid-transfer-1"))
        )
      )

    override def submitMultipleTransfers(data: Seq[TransferData])(using hc: HeaderCarrier): Future[SubmissionOutcome] =
      Future.successful(
        SubmissionOutcome.TransformationFailed(
          Seq(
            TransformationFailure(1, 0, "INVALID_REQUEST", "invalid-transfer-1"),
            TransformationFailure(2, 1, "INVALID_REQUEST", "invalid-transfer-2")
          )
        )
      )

  private val mixedSubscriptionService = new SubmissionService:
    override def submitSingleTransfer(data: TransferData)(using hc: HeaderCarrier): Future[SubmissionOutcome] =
      Future.successful(
        SubmissionOutcome.TransformationFailed(
          Seq(TransformationFailure(1, 0, "INVALID_REQUEST", "all transfers in a batch must have the same subscriptionId"))
        )
      )

    override def submitMultipleTransfers(data: Seq[TransferData])(using hc: HeaderCarrier): Future[SubmissionOutcome] =
      Future.successful(
        SubmissionOutcome.TransformationFailed(
          Seq(TransformationFailure(2, 1, "INVALID_REQUEST", "all transfers in a batch must have the same subscriptionId"))
        )
      )

  private val controller = new SubmissionController(controllerComponents, successService)
  private val controllerWithTransformationFailures = new SubmissionController(controllerComponents, failingService)
  private val controllerWithMixedSubscriptionFailures = new SubmissionController(controllerComponents, mixedSubscriptionService)

  "SubmissionController.submitMultipleTransfersAction" should:
    "return 400 for an empty JSON array" in:
      val request = FakeRequest("POST", "/submission/multiple").withBody(Json.arr())

      val result = controller.submitMultipleTransfersAction.apply(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("at least one transfer must be provided")

    "return 400 with all transformation failures including request indexes" in:
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

      val result = controllerWithTransformationFailures.submitMultipleTransfersAction.apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseBody = contentAsString(result)
      responseBody should include("\"error\":\"invalid transfer data\"")
      responseBody should include("\"recordId\":1")
      responseBody should include("\"recordId\":2")
      responseBody should include("\"requestIndex\":0")
      responseBody should include("\"requestIndex\":1")
      responseBody should include("\"errorCode\":\"INVALID_REQUEST\"")
      responseBody should include("invalid-transfer-1")
      responseBody should include("invalid-transfer-2")

    "return 400 including mixed-subscriptionId validation details" in:
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
            "subscriptionId" -> "stc-999",
            "submissionId" -> "sub-124",
            "submitterAffinity" -> "Individual",
            "data" -> Json.obj()
          )
        )
      )

      val result = controllerWithMixedSubscriptionFailures.submitMultipleTransfersAction.apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseBody = contentAsString(result)
      responseBody should include("\"error\":\"invalid transfer data\"")
      responseBody should include("\"errorCode\":\"INVALID_REQUEST\"")
      responseBody should include("\"requestIndex\":1")
      responseBody should include("all transfers in a batch must have the same subscriptionId")

  "SubmissionController.submitSingleTransferAction" should:
    "return 400 with transformation errors" in:
      val request = FakeRequest("POST", "/submission/single").withBody(
        Json.obj(
          "transferType" -> 1,
          "subscriptionId" -> "stc-123",
          "submissionId" -> "sub-123",
          "submitterAffinity" -> "Individual",
          "data" -> Json.obj()
        )
      )

      val result = controllerWithTransformationFailures.submitSingleTransferAction.apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseBody = contentAsString(result)
      responseBody should include("\"recordId\":1")
      responseBody should include("\"errorCode\":\"INVALID_REQUEST\"")
      responseBody should include("\"requestIndex\":0")

  override def afterAll(): Unit =
    Await.result(summon[ActorSystem].terminate(), 5.seconds)
    super.afterAll()
