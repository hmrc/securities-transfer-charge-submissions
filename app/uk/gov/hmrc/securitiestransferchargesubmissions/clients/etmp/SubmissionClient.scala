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
import uk.gov.hmrc.securitiestransferchargesubmissions.config.{AppConfig, RetryPolicy}
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

  def viewTransaction(subscriptionId: String, utrn: String, correlationId: String)(using
    HeaderCarrier
  ): Future[StcTransactionViewResponse]

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

    withRetry(appConfig.etmpCreateRetryPolicy)(doSubmitTransfer(subscriptionId, correlationId, request, receiptDate))
      .map(StcTransactionCreateResponse.fromHttpResponse)

  override def viewTransaction(subscriptionId: String, utrn: String, correlationId: String)(using
    hc: HeaderCarrier
  ): Future[StcTransactionViewResponse] =
    val receiptDate = dateTimeFormatter.format(Instant.now(clock))

    withRetry(appConfig.etmpViewRetryPolicy)(doViewTransaction(subscriptionId, utrn, correlationId, receiptDate))
      .map(StcTransactionViewResponse.fromHttpResponse)

  private def withRetry(policy: RetryPolicy, retryAttempt: Int = 0)(call: => Future[HttpResponse]): Future[HttpResponse] =
    call.flatMap { response =>
      if (isRetriable5xx(response.status) && retryAttempt < policy.maxRetries) {
        val nextDelay = backoffDelayForAttempt(policy, retryAttempt)
        after(nextDelay, actorSystem.scheduler)(withRetry(policy, retryAttempt + 1)(call))
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
      .setHeader(etmpHeaders(correlationId, receiptDate)*)
      .withBody(Json.toJson(request))
      .execute[HttpResponse](using HttpReads.Implicits.readRaw)

  private def doViewTransaction(
    subscriptionId: String,
    utrn: String,
    correlationId: String,
    receiptDate: String
  )(using hc: HeaderCarrier): Future[HttpResponse] =
    httpClientV2
      .get(url"${appConfig.etmpTransactionBaseUrl}/RESTAdapter/stc/transaction/$subscriptionId/$utrn")
      .setHeader(etmpHeaders(correlationId, receiptDate)*)
      .execute[HttpResponse](using HttpReads.Implicits.readRaw)

  private def etmpHeaders(correlationId: String, receiptDate: String): Seq[(String, String)] =
    Seq(
      "correlationid" -> correlationId,
      "X-Originating-System" -> appConfig.etmpOriginatingSystem,
      "X-Receipt-Date" -> receiptDate,
      "X-Transmitting-System" -> appConfig.etmpTransmittingSystem
    )

  private def isRetriable5xx(status: Int): Boolean = status / 100 == 5

  private def backoffDelayForAttempt(policy: RetryPolicy, retryAttempt: Int): FiniteDuration =
    policy.initialBackoff * math.pow(2d, retryAttempt.toDouble).toLong
