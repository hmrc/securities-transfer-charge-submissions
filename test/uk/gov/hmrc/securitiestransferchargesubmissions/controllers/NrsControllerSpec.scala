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

package uk.gov.hmrc.securitiestransferchargesubmissions.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status, stubControllerComponents}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.*
import NrsTestData._
import uk.gov.hmrc.securitiestransferchargesubmissions.services.NrsService

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class NrsControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {
  
  private val mockNrsService = mock[NrsService]
  private val mockAuthConnector = mock[AuthConnector]
  private val cc: ControllerComponents = stubControllerComponents()

  when(mockAuthConnector.authorise[Unit](any[Predicate], any[Retrieval[Unit]])(any[HeaderCarrier], any[ExecutionContext]))
    .thenReturn(Future.successful(()))

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private val controller = new NrsController(cc, mockNrsService, mockAuthConnector)

  private val unauthorisedController = new NrsController(
    cc,
    mockNrsService,
    new AuthConnector {
      override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
        Future.failed(new MissingBearerToken)
    }
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockNrsService)
  }


  "NrsController.submitSingle" should {
    "accept valid single submission and return 202" in {
      when(mockNrsService.submitSingle(any())(any())).thenReturn(Future.successful(()))

      val request = FakeRequest(Helpers.POST, "/nrs/single")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(testSingleRequest))

      val result = controller.submitSingle()(request)

      status(result) shouldBe ACCEPTED
      val jsonResponse = contentAsJson(result)
      (jsonResponse \ "message").as[String] should include("Single submission accepted")

      verify(mockNrsService).submitSingle(any())(any())
    }

    "return 400 for invalid request format" in {
      val invalidRequest = FakeRequest(Helpers.POST, "/nrs/single")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("invalid" -> "data"))

      val result = controller.submitSingle()(invalidRequest)

      status(result) shouldBe BAD_REQUEST
      val jsonResponse = contentAsJson(result)
      (jsonResponse \ "statusCode").as[Int] shouldBe 400
      (jsonResponse \ "message").as[String] should include("Invalid request format")
      verify(mockNrsService, never()).submitSingle(any())(any())
    }

    "pass the parsed request to the service" in {
      when(mockNrsService.submitSingle(any())(any())).thenReturn(Future.successful(()))

      val request = FakeRequest(Helpers.POST, "/nrs/single")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(testSingleRequest))

      status(controller.submitSingle()(request)) shouldBe ACCEPTED

      verify(mockNrsService).submitSingle(org.mockito.ArgumentMatchers.eq(testSingleRequest))(any())
    }

    "return 202 without waiting for the NRS call to finish" in {
      when(mockNrsService.submitSingle(any())(any())).thenReturn(Future.never)

      val request = FakeRequest(Helpers.POST, "/nrs/single")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(testSingleRequest))

      status(controller.submitSingle()(request)) shouldBe ACCEPTED
    }

    "fail without calling the service when the caller is not authorised" in {
      val result = unauthorisedController.submitSingle()(
        FakeRequest(Helpers.POST, "/nrs/single").withBody(Json.toJson(testSingleRequest))
      )

      Await.result(result.failed, 5.seconds) shouldBe a[MissingBearerToken]
      verify(mockNrsService, never()).submitSingle(any())(any())
    }
  }

  "NrsController.submitBulk" should {
    "accept valid bulk submission and return 202" in {
      when(mockNrsService.submitBulk(any())(any())).thenReturn(Future.successful(()))

      val request = FakeRequest(Helpers.POST, "/nrs/bulk")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(testBulkRequest))

      val result = controller.submitBulk()(request)

      status(result) shouldBe ACCEPTED
      val jsonResponse = contentAsJson(result)
      (jsonResponse \ "message").as[String] should include("Bulk submission accepted")

      verify(mockNrsService).submitBulk(any())(any())
    }

    "return 400 for invalid request format" in {
      val invalidRequest = FakeRequest(Helpers.POST, "/nrs/bulk")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("invalid" -> "data"))

      val result = controller.submitBulk()(invalidRequest)

      status(result) shouldBe BAD_REQUEST
      val jsonResponse = contentAsJson(result)
      (jsonResponse \ "statusCode").as[Int] shouldBe 400
      (jsonResponse \ "message").as[String] should include("Invalid request format")
      verify(mockNrsService, never()).submitBulk(any())(any())
    }

    "pass the parsed request to the service" in {
      when(mockNrsService.submitBulk(any())(any())).thenReturn(Future.successful(()))

      val request = FakeRequest(Helpers.POST, "/nrs/bulk")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(testBulkRequest))

      status(controller.submitBulk()(request)) shouldBe ACCEPTED

      verify(mockNrsService).submitBulk(org.mockito.ArgumentMatchers.eq(testBulkRequest))(any())
    }

    "return 202 without waiting for the NRS calls to finish" in {
      when(mockNrsService.submitBulk(any())(any())).thenReturn(Future.never)

      val request = FakeRequest(Helpers.POST, "/nrs/bulk")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(testBulkRequest))

      status(controller.submitBulk()(request)) shouldBe ACCEPTED
    }

    "fail without calling the service when the caller is not authorised" in {
      val result = unauthorisedController.submitBulk()(
        FakeRequest(Helpers.POST, "/nrs/bulk").withBody(Json.toJson(testBulkRequest))
      )

      Await.result(result.failed, 5.seconds) shouldBe a[MissingBearerToken]
      verify(mockNrsService, never()).submitBulk(any())(any())
    }
  }
}
