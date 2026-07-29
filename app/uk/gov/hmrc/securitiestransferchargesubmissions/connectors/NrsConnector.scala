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

package uk.gov.hmrc.securitiestransferchargesubmissions.connectors

import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.{NrsSubmission, NrsSubmissionResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class NrsConnector @Inject()(
  httpClient: HttpClientV2,
  appConfig: AppConfig,
  actorSystem: ActorSystem
)(implicit ec: ExecutionContext) extends Logging {

  private val nrsSubmissionUrl = s"${appConfig.nrsBaseUrl}/submission"

  def submitToNrs(nrsSubmission: NrsSubmission)(implicit hc: HeaderCarrier): Future[Option[NrsSubmissionResponse]] = {
    retryWithBackoff(appConfig.nrsRetryDelays, 1) { attemptNumber =>
      makeNrsCall(nrsSubmission, attemptNumber)
    }
  }

  private def makeNrsCall(nrsSubmission: NrsSubmission, attemptNumber: Int)(implicit hc: HeaderCarrier): Future[Option[NrsSubmissionResponse]] = {
    val headers = Seq(
      "X-API-Key" -> appConfig.nrsApiKey,
      "Content-Type" -> "application/json"
    )

    logger.info(s"NRS submission attempt $attemptNumber")

    httpClient
      .post(url"$nrsSubmissionUrl")
      .setHeader(headers: _*)
      .withBody(Json.toJson(nrsSubmission))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case ACCEPTED =>
            Try(response.json.as[NrsSubmissionResponse]) match {
              case Success(nrsResponse) =>
                logger.info(s"NRS submission successful with ID: ${nrsResponse.nrSubmissionId} (attempt $attemptNumber)")
                Some(nrsResponse)
              case Failure(e) =>
                logger.error(s"Failed to parse NRS response: ${e.getMessage} (attempt $attemptNumber)", e)
                None
            }
          case status if status >= 400 && status < 500 =>
            // 4xx errors are not retryable
            logger.warn(s"NRS submission failed with 4xx error (status $status) - not retrying: ${response.body}")
            None
          case status if status >= 500 =>
            // 5xx errors are retryable
            logger.warn(s"NRS submission failed with 5xx error (status $status) - will retry: ${response.body}")
            throw UpstreamErrorResponse(s"NRS returned $status", status)
          case status =>
            logger.warn(s"NRS submission returned unexpected status $status: ${response.body}")
            None
        }
      }
      .recover {
        case e: UpstreamErrorResponse if e.statusCode >= 500 =>
          // Let 5xx errors bubble up for retry
          logger.warn(s"NRS 5xx error on attempt $attemptNumber: ${e.statusCode} - ${e.message}")
          throw e
        case e: UpstreamErrorResponse =>
          // 4xx errors - don't retry
          logger.error(s"NRS submission failed with 4xx error: ${e.statusCode} - ${e.message} (attempt $attemptNumber)")
          None
        case e: Exception =>
          logger.error(s"NRS submission failed with exception (attempt $attemptNumber): ${e.getMessage}", e)
          throw e
      }
  }

  private def retryWithBackoff[A](
    delays: Seq[FiniteDuration],
    attemptNumber: Int
  )(task: Int => Future[Option[NrsSubmissionResponse]]): Future[Option[NrsSubmissionResponse]] = {
    task(attemptNumber).recoverWith {
      case e: UpstreamErrorResponse if e.statusCode >= 500 && delays.nonEmpty =>
        val delay = delays.head
        logger.info(s"Retrying NRS submission after $delay (attempt ${attemptNumber + 1})")
        after(delay) {
          retryWithBackoff(delays.tail, attemptNumber + 1)(task)
        }
      case e: Exception if delays.nonEmpty =>
        val delay = delays.head
        logger.info(s"Retrying NRS submission after $delay due to exception (attempt ${attemptNumber + 1})")
        after(delay) {
          retryWithBackoff(delays.tail, attemptNumber + 1)(task)
        }
      case e =>
        logger.error(s"NRS submission failed after all retries: ${e.getMessage}")
        Future.successful(None)
    }
  }

  private def after[T](delay: FiniteDuration)(block: => Future[T]): Future[T] = {
    val promise = scala.concurrent.Promise[T]()
    actorSystem.scheduler.scheduleOnce(delay) {
      promise.completeWith(block)
    }
    promise.future
  }
}
