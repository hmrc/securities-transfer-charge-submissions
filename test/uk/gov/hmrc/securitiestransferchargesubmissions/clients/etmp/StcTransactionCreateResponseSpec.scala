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
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpResponse

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
