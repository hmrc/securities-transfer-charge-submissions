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
import play.api.Logging
import play.api.libs.json.*
import play.api.http.Status
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.*
import uk.gov.hmrc.securitiestransferchargesubmissions.utils.DateTimeFormats

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait SubmissionClient:
  def submitTransfer(
    subscriptionId: String,
    correlationId: String,
    request: StcTransactionCreateRequest)(
    using HeaderCarrier
  ): Future[StcTransactionCreateResponse]

  def getTransferDetails(
    subscriptionId: String,
    correlationId: String,
    utrn: String)(
    using HeaderCarrier
  ): Future[EtmpTransferDetails]

@Singleton
class SubmissionClientImpl @Inject() (
  httpClientV2: HttpClientV2,
  appConfig: AppConfig,
  clock: Clock,
  actorSystem: ActorSystem
)(using ec: ExecutionContext)
    extends SubmissionClient with Logging {

  override def submitTransfer(
                               subscriptionId: String, correlationId: String, request: StcTransactionCreateRequest)(
                               using hc: HeaderCarrier
                             ): Future[StcTransactionCreateResponse] =
    val receiptDate: String = DateTimeFormats.utcDateTimeStamp(Instant.now(clock))

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
        Headers.CorrelationId -> correlationId,
        Headers.OriginatingSystem -> appConfig.etmpOriginatingSystem,
        Headers.TransmittingSystem -> appConfig.etmpTransmittingSystem,
        Headers.ReceiptDate -> receiptDate
      )
      .withBody(Json.toJson(request))
      .execute[HttpResponse](using HttpReads.Implicits.readRaw)

  private def isRetriable5xx(status: Int): Boolean = status / 100 == 5

  private def backoffDelayForAttempt(retryAttempt: Int): FiniteDuration =
    appConfig.etmpCreateInitialBackoff * math.pow(2d, retryAttempt.toDouble).toLong


  def getTransferDetails(
    subscriptionId: String,
    correlationId: String,
    utrn: String)(
    using HeaderCarrier
  ): Future[EtmpTransferDetails] = {

    httpClientV2
      .get(url"${appConfig.etmpTransactionBaseUrl}/RESTAdapter/stc/transaction/$subscriptionId/$utrn")
      .setHeader(
        Headers.CorrelationId -> correlationId,
        Headers.OriginatingSystem -> appConfig.etmpOriginatingSystem,
        Headers.TransmittingSystem -> appConfig.etmpTransmittingSystem,
        Headers.ReceiptDate -> DateTimeFormats.utcDateTimeStamp(Instant.now(clock))
      )
      .execute[HttpResponse](using HttpReads.Implicits.readRaw)
      .map(SubmissionClientImpl.handleGetResponse)
      .recoverWith { ex =>
        logger.warn(
          s"Error getting transfer details for UTRN $utrn: ${ex.getLocalizedMessage}"
        )
        Future.failed[EtmpTransferDetails](ex)
      }
  }
}

  
  object SubmissionClientImpl {
    
    private val handleGetTransferSuccess: PartialFunction[HttpResponse, EtmpTransferDetails] = {
      case resp if resp.status == Status.OK =>
        resp.json.validate[TransferDetailsSuccess] match {
          case JsSuccess(value, _) => value
          case JsError(errors) => throw UpstreamErrorResponse(
            message    = s"Unable to parse ETMP get transfer 200 response: $errors",
            statusCode = 200,
            reportAs   = 500,
            headers    = resp.headers
          )
        }
    }

    private val handleGetTransferBusinessError: PartialFunction[HttpResponse, EtmpTransferDetails] = {
      case resp if resp.status == Status.UNPROCESSABLE_ENTITY =>
        resp.json.validate[TransferDetailsFailure] match {
          case JsSuccess(value, _) => value
          case JsError(errors) => throw UpstreamErrorResponse(
            message    = s"Unable to parse ETMP get transfer 422 response: $errors",
            statusCode = 422,
            reportAs   = 500,
            headers    = resp.headers
          )
        }
    }

    private val handleOtherError: PartialFunction[HttpResponse, EtmpTransferDetails] = {
      case resp =>
        val cid = resp.header(Headers.CorrelationId).getOrElse("Unknown")
        throw UpstreamErrorResponse(
            message     = s"ETMP returned HTTP ${resp.status} for request $cid.",
            statusCode  = resp.status,
            reportAs    = resp.status,
            headers     = resp.headers
          )
    }

    /*
     * Possible outcomes for getting a transfer:
     *
     * (1) HTTP Success + successful parsing of the payload => return the transfer details.
     * (2) HTTP Success + failure to parse the payload => return Server Error.
     * (3) HTTP 422 + successful parsing of the payload => return the business error
     *     (002 = STC ID does not exist, 005 = UTRN not found).
     * (4) HTTP 422 + failure to parse the payload => return Server Error.
     * (5) Other HTTP Response => log the error and propagate the status code upwards.
     */
    private val handleGetResponse: HttpResponse => EtmpTransferDetails =
      handleGetTransferSuccess
        .orElse(handleGetTransferBusinessError)
        .orElse(handleOtherError)
  }
