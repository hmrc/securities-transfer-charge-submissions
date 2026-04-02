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
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.StcChargeSuccess
import uk.gov.hmrc.securitiestransferchargesubmissions.models.{TransferBatchRequest, TransformationFailure}
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

    override def submitMultipleTransfers(data: TransferBatchRequest)(using hc: HeaderCarrier): Future[SubmissionOutcome] =
      Future.successful(SubmissionOutcome.Submitted(Seq(
        StcChargeSuccess(1, "utrn-1", "Charge", "ref-1", "STF", BigDecimal(10), "2026-04-30")
      )))

  private val failingService = new SubmissionService:

    override def submitMultipleTransfers(data: TransferBatchRequest)(using hc: HeaderCarrier): Future[SubmissionOutcome] =
      Future.successful(
        SubmissionOutcome.TransformationFailed(
          Seq(
            TransformationFailure(1, 0, "INVALID_REQUEST", "invalid-transfer-1"),
            TransformationFailure(2, 1, "INVALID_REQUEST", "invalid-transfer-2")
          )
        )
      )

  private val controller = new SubmissionController(controllerComponents, successService)
  private val controllerWithTransformationFailures = new SubmissionController(controllerComponents, failingService)

  "SubmissionController.submitBatchAction" should:
    "return 200 for a valid multiple-transfer request" in:
      val request = FakeRequest("POST", "/submission").withBody(
        Json.obj(
          "transferType" -> 1,
          "subscriptionId" -> "stc-123",
          "submissionId" -> "sub-123",
          "submitterAffinity" -> "Individual",
          "transfers" -> Json.arr(
            Json.obj("data" -> Json.obj())
          )
        )
      )

      val result = controller.submitBatchAction.apply(request)

      status(result) shouldBe OK
      contentAsString(result) should include("\"recordId\":1")
      contentAsString(result) should include("\"utrn\":\"utrn-1\"")

    "return 400 for an empty transfers array" in:
      val request = FakeRequest("POST", "/submission").withBody(
        Json.obj(
          "transferType" -> 1,
          "subscriptionId" -> "stc-123",
          "submissionId" -> "sub-123",
          "submitterAffinity" -> "Individual",
          "transfers" -> Json.arr()
        )
      )

      val result = controller.submitBatchAction.apply(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("at least one transfer must be provided")

    "return 400 for invalid JSON schema" in:
      val request = FakeRequest("POST", "/submission").withBody(
        Json.obj("unexpected" -> "shape")
      )

      val result = controller.submitBatchAction.apply(request)

      status(result) shouldBe BAD_REQUEST
      val responseBody = contentAsString(result)
      responseBody should include("\"error\":\"invalid transfer data\"")
      responseBody should include("\"details\"")

    "return 400 with all transformation failures including request indexes" in:
      val request = FakeRequest("POST", "/submission").withBody(
        Json.obj(
          "transferType" -> 1,
          "subscriptionId" -> "stc-123",
          "submissionId" -> "sub-123",
          "submitterAffinity" -> "Individual",
          "transfers" -> Json.arr(
            Json.obj("data" -> Json.obj()),
            Json.obj("data" -> Json.obj())
          )
        )
      )

      val result = controllerWithTransformationFailures.submitBatchAction.apply(request)

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


  override def afterAll(): Unit =
    Await.result(summon[ActorSystem].terminate(), 5.seconds)
    super.afterAll()
