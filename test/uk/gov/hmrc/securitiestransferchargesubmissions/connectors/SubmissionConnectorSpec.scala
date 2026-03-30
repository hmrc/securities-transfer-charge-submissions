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
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{StcTransactionCreateRequest, StcTransactionCreateResponse, SubmissionClient}
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmissionConnectorSpec extends AnyWordSpec with Matchers:

  given HeaderCarrier = HeaderCarrier()

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

  private val submissionClient = new SubmissionClient:
    override def submitTransfer(
      stcId: String,
      correlationId: String,
      request: StcTransactionCreateRequest
    )(using uk.gov.hmrc.http.HeaderCarrier): Future[StcTransactionCreateResponse] =
      fail("SubmissionClient should not be called for empty transfers")

  private val transformer = new SubmissionTransformer(appConfig):
    override def toSingleRecordRequest(recordId: Int, data: uk.gov.hmrc.securitiestransferchargesubmissions.controllers.TransferData) =
      fail("not used in this test")

  private val connector = new SubmissionConnectorImpl(submissionClient, transformer, appConfig)

  "SubmissionConnectorImpl.submitTransfers" should:
    "reject an empty transfer sequence" in:
      val exception = the[IllegalArgumentException] thrownBy {
        connector.submitTransfers("stcId", "correlationId", Seq.empty)
      }

      exception.getMessage should include("transfers must not be empty")
