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

package uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

class StcTransactionCreateResponseSpec extends AnyWordSpec with Matchers:

  "StcTransactionCreateResponse.fromHttpResponse" should:
    "parse a 201 processed response" in:
      val response = HttpResponse(
        status = 201,
        body = Json.obj(
          "success" -> Json.obj(
            "processingDate" -> "2026-03-30T12:00:00Z",
            "charges" -> Json.arr(
              Json.obj(
                "recordId" -> 1,
                "utrn" -> "900459020010",
                "chargeTypeDescription" -> "Charge Type",
                "chargeReference" -> "XA123",
                "chargeType" -> "STF",
                "chargeAmount" -> 10.00,
                "chargeDueDate" -> "2026-04-30"
              )
            )
          )
        ).toString(),
        headers = Map.empty
      )

      StcTransactionCreateResponse.fromHttpResponse(response) shouldBe
        StcTransactionCreateProcessed(
          StcTransactionCreateProcessedBody(
            processingDate = "2026-03-30T12:00:00Z",
            charges = List(
              StcChargeSuccess(
                recordId = 1,
                utrn = "900459020010",
                chargeTypeDescription = "Charge Type",
                chargeReference = "XA123",
                chargeType = "STF",
                chargeAmount = BigDecimal(10.00),
                chargeDueDate = "2026-04-30"
              )
            )
          )
        )

    "parse a 400 bad request response" in:
      val response = HttpResponse(
        status = 400,
        body = Json.obj(
          "error" -> Json.obj(
            "code" -> "400",
            "message" -> "Bad request",
            "logID" -> "C0000AB8190CB66000000003000007A6"
          )
        ).toString(),
        headers = Map.empty
      )

      StcTransactionCreateResponse.fromHttpResponse(response) shouldBe
        StcTransactionCreateBadRequest(
          StcTransactionCreateBadRequestBody(
            code = "400",
            message = "Bad request",
            logID = "C0000AB8190CB66000000003000007A6"
          )
        )

    "parse a 422 business error response" in:
      val response = HttpResponse(
        status = 422,
        body = Json.obj(
          "errors" -> Json.obj(
            "processingDate" -> "2026-03-30T12:00:00Z",
            "code" -> "037",
            "text" -> "Main Buyer Details Invalid"
          )
        ).toString(),
        headers = Map.empty
      )

      StcTransactionCreateResponse.fromHttpResponse(response) shouldBe
        StcTransactionCreateBusinessError(
          StcTransactionCreateBusinessErrorBody(
            processingDate = "2026-03-30T12:00:00Z",
            code = "037",
            text = "Main Buyer Details Invalid"
          )
        )

    "parse a 201 containing both successful and failed charges" in:
      val response = HttpResponse(
        status = 201,
        body = Json.obj(
          "success" -> Json.obj(
            "processingDate" -> "2026-03-30T12:00:00Z",
            "charges" -> Json.arr(
              Json.obj(
                "recordId" -> 1, "utrn" -> "900459020010", "chargeTypeDescription" -> "Charge Type",
                "chargeReference" -> "XA123", "chargeType" -> "STF", "chargeAmount" -> 10.00, "chargeDueDate" -> "2026-04-30"
              ),
              Json.obj("recordId" -> 2, "errorCode" -> "037", "errorText" -> "Main Buyer Details Invalid")
            )
          )
        ).toString(),
        headers = Map.empty
      )

      StcTransactionCreateResponse.fromHttpResponse(response) match
        case StcTransactionCreateProcessed(body) =>
          body.charges shouldBe List(
            StcChargeSuccess(1, "900459020010", "Charge Type", "XA123", "STF", BigDecimal(10), "2026-04-30"),
            StcChargeFailure(2, "037", "Main Buyer Details Invalid")
          )
        case other => fail(s"Unexpected result: $other")

    for (status, shape) <- Seq(201 -> "a charge without a recordId", 400 -> "an empty error", 422 -> "an empty errors object") do
      s"fail with a 502 when a $status body has $shape" in:
        val body = status match
          case 201 => Json.obj("success" -> Json.obj("processingDate" -> "x", "charges" -> Json.arr(Json.obj("utrn" -> "u"))))
          case 400 => Json.obj("error" -> Json.obj())
          case _   => Json.obj("errors" -> Json.obj())

        val exception = the[UpstreamErrorResponse] thrownBy
          StcTransactionCreateResponse.fromHttpResponse(HttpResponse(status, body.toString(), Map.empty))

        exception.statusCode shouldBe 502
        exception.message should include(s"ETMP create $status response")

    "fail with the original status for an unexpected response status" in:
      val exception = the[UpstreamErrorResponse] thrownBy
        StcTransactionCreateResponse.fromHttpResponse(HttpResponse(404, "", Map.empty))

      exception.statusCode shouldBe 404
      exception.message should include("Unexpected ETMP create response status: 404")

  "StcCharge JSON" should:
    "read a charge with errorCode and errorText as a failure" in:
      Json.obj("recordId" -> 2, "errorCode" -> "500", "errorText" -> "boom").as[StcCharge] shouldBe
        StcChargeFailure(2, "500", "boom")

    "read a charge with only one of errorCode and errorText as a success, which then fails validation" in:
      Json.obj("recordId" -> 2, "errorCode" -> "500").validate[StcCharge].isError shouldBe true

    "write the documented fields for success and failure charges" in:
      // `_type` is a play-json sealed-trait discriminator that currently leaks into the output; it is not part of the API contract.
      (Json.toJson[StcCharge](StcChargeFailure(2, "500", "boom")).as[JsObject] - "_type") shouldBe
        Json.obj("recordId" -> 2, "errorCode" -> "500", "errorText" -> "boom")
      (Json.toJson[StcCharge](StcChargeSuccess(1, "u", "d", "r", "STF", BigDecimal(10), "2026-04-30")).as[JsObject] - "_type") shouldBe
        Json.obj("recordId" -> 1, "utrn" -> "u", "chargeTypeDescription" -> "d", "chargeReference" -> "r",
          "chargeType" -> "STF", "chargeAmount" -> 10, "chargeDueDate" -> "2026-04-30")
