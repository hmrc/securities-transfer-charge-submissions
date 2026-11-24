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
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.securitiestransferchargesubmissions.ViewTransactionFixtures

class StcTransactionViewResponseSpec extends AnyWordSpec with Matchers:

  private def response(status: Int, body: String) = HttpResponse(status, body, Map.empty)

  "StcTransactionViewResponse.fromHttpResponse" should:
    "parse a 200 into the found body" in:
      StcTransactionViewResponse.fromHttpResponse(response(200, ViewTransactionFixtures.successJson.toString)) shouldBe
        StcTransactionViewFound(ViewTransactionFixtures.successBody)

    "parse a 400 into a bad request" in:
      StcTransactionViewResponse.fromHttpResponse(response(400, ViewTransactionFixtures.badRequestJson.toString)) shouldBe a[StcTransactionViewBadRequest]

    "parse a 422 into a business error carrying the code and text" in:
      StcTransactionViewResponse.fromHttpResponse(response(422, ViewTransactionFixtures.businessErrorJson("005", "UTRN not found").toString)) shouldBe
        StcTransactionViewBusinessError(StcTransactionCreateBusinessErrorBody("2026-03-31T12:00:00Z", "005", "UTRN not found"))

    for (status, body) <- Seq(200 -> Json.obj("success" -> Json.obj()), 400 -> Json.obj("error" -> Json.obj()), 422 -> Json.obj("errors" -> Json.obj())) do
      s"fail with a 502 when a $status body cannot be parsed" in:
        val exception = the[UpstreamErrorResponse] thrownBy
          StcTransactionViewResponse.fromHttpResponse(response(status, body.toString))

        exception.statusCode shouldBe 502
        exception.message should include(s"Unable to parse ETMP view $status response")

    "fail with the original status for an unexpected response status" in:
      val exception = the[UpstreamErrorResponse] thrownBy
        StcTransactionViewResponse.fromHttpResponse(response(503, ""))

      exception.statusCode shouldBe 503
      exception.message should include("Unexpected ETMP view response status: 503")
