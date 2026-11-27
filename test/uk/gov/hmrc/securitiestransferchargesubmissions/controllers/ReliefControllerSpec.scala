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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status, stubControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{ReliefClient, ReliefClientException}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.Relief

import scala.concurrent.{ExecutionContext, Future}

class ReliefControllerSpec extends AnyWordSpec with Matchers with MockitoSugar:

  private given ExecutionContext = ExecutionContext.global

  private val reliefClient   = mock[ReliefClient]
  private val authConnector  = mock[AuthConnector]
  private val controller     = new ReliefController(stubControllerComponents(), reliefClient, authConnector)

  when(authConnector.authorise[Unit](any[Predicate], any[Retrieval[Unit]])(using any[HeaderCarrier], any[ExecutionContext]))
    .thenReturn(Future.successful(()))

  "ReliefController.getReliefs" should {

    "return 200 with the reliefs as name/percentage pairs" in {
      when(reliefClient.getReliefs()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List(Relief("Charities Relief", 50), Relief("Other", 100))))

      val result = controller.getReliefs()(FakeRequest())

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.arr(
        Json.obj("name" -> "Charities Relief", "percentage" -> 50),
        Json.obj("name" -> "Other", "percentage" -> 100)
      )
    }

    "return 502 with a stable error code when reliefs cannot be obtained" in {
      when(reliefClient.getReliefs()(using any[HeaderCarrier]))
        .thenReturn(Future.failed(ReliefClientException("ETMP down")))

      val result = controller.getReliefs()(FakeRequest())

      status(result) shouldBe BAD_GATEWAY
      contentAsJson(result) shouldBe Json.obj("error" -> "RELIEFS_UNAVAILABLE")
    }
  }
