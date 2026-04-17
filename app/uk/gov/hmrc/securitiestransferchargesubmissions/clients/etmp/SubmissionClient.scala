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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.after
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}

import java.time.format.DateTimeFormatter
import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait SubmissionClient:
  def submitTransfer(subscriptionId: String, correlationId: String, request: StcTransactionCreateRequest)(using
    HeaderCarrier
  ): Future[StcTransactionCreateResponse]

@Singleton
class SubmissionClientImpl @Inject() (
  httpClientV2: HttpClientV2,
  appConfig: AppConfig,
  clock: Clock,
  actorSystem: ActorSystem
)(using ec: ExecutionContext)
    extends SubmissionClient:

  private val dateTimeFormatter = DateTimeFormatter.ISO_INSTANT

  override def submitTransfer(
    subscriptionId: String, correlationId: String, request: StcTransactionCreateRequest)(
    using hc: HeaderCarrier
  ): Future[StcTransactionCreateResponse] =
    val receiptDate = dateTimeFormatter.format(Instant.now(clock))

    submitTransferWithRetry(subscriptionId, correlationId, request, receiptDate, retryAttempt = 0)
      .map(StcTransactionCreateResponse.fromHttpResponse)

  private def submitTransferWithRetry(
    subscriptionId: String,
    correlationId: String,
    request: StcTransactionCreateRequest,
    receiptDate: String,
    retryAttempt: Int
  )(using hc: HeaderCarrier): Future[HttpResponse] =
    doSubmitTransfer(subscriptionId, correlationId, request, receiptDate).flatMap { response =>
      if (isRetriable5xx(response.status) && retryAttempt < appConfig.etmpCreateMaxRetries) {
        val nextDelay = backoffDelayForAttempt(retryAttempt)
        after(nextDelay, actorSystem.scheduler)(
          submitTransferWithRetry(subscriptionId, correlationId, request, receiptDate, retryAttempt + 1)
        )
      } else {
        Future.successful(response)
      }
    }

  private def doSubmitTransfer(
    subscriptionId: String,
    correlationId: String,
    request: StcTransactionCreateRequest,
    receiptDate: String
  )(using hc: HeaderCarrier): Future[HttpResponse] =
    httpClientV2
      .post(url"${appConfig.etmpTransactionBaseUrl}/RESTAdapter/stc/transaction/$subscriptionId")
      .setHeader(
        "correlationid" -> correlationId,
        "X-Originating-System" -> appConfig.etmpOriginatingSystem,
        "X-Receipt-Date" -> receiptDate,
        "X-Transmitting-System" -> appConfig.etmpTransmittingSystem
      )
      .withBody(Json.toJson(request))
      .execute[HttpResponse](using HttpReads.Implicits.readRaw)

  private def isRetriable5xx(status: Int): Boolean = status / 100 == 5

  private def backoffDelayForAttempt(retryAttempt: Int): FiniteDuration =
    appConfig.etmpCreateInitialBackoff * math.pow(2d, retryAttempt.toDouble).toLong
