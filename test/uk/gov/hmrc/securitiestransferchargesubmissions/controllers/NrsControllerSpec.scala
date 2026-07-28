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
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status, stubControllerComponents}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.*
import NrsTestData._
import uk.gov.hmrc.securitiestransferchargesubmissions.services.NrsService

import scala.concurrent.Future

class NrsControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {
  
  private val mockNrsService = mock[NrsService]
  private val cc: ControllerComponents = stubControllerComponents()

  private val controller = new NrsController(cc, mockNrsService)

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
    }
  }
}
