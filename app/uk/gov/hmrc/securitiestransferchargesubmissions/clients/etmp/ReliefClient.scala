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

import play.api.Logging
import play.api.libs.functional.syntax.*
import play.api.libs.json.{JsError, JsSuccess, Json, Reads, __}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig
import uk.gov.hmrc.securitiestransferchargesubmissions.models.Relief

import java.time.format.DateTimeFormatter
import java.time.{Clock, Instant}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

final case class ReliefClientException(message: String) extends RuntimeException(message)

trait ReliefClient:
  def getReliefs()(using HeaderCarrier): Future[List[Relief]]

/** Calls the ETMP reliefs lookup.
  *
  * ETMP have not published this API yet, so the contract below is one we made up (see the stubs repo):
  * GET /RESTAdapter/stc/reliefs -> 200 {"success": {"processingDate": "...", "reliefs": [{"reliefName": "...", "reliefPercentage": 50}]}}
  */
@Singleton
class ReliefClientImpl @Inject() (
  httpClientV2: HttpClientV2,
  appConfig: AppConfig,
  clock: Clock
)(using ec: ExecutionContext)
    extends ReliefClient
    with Logging:

  import ReliefClientImpl.given

  override def getReliefs()(using hc: HeaderCarrier): Future[List[Relief]] =
    httpClientV2
      .get(url"${appConfig.etmpTransactionBaseUrl}/RESTAdapter/stc/reliefs")
      .setHeader(
        "correlationid"          -> UUID.randomUUID().toString,
        "X-Originating-System"   -> appConfig.etmpOriginatingSystem,
        "X-Receipt-Date"         -> DateTimeFormatter.ISO_INSTANT.format(Instant.now(clock)),
        "X-Transmitting-System"  -> appConfig.etmpTransmittingSystem
      )
      .execute[HttpResponse](using HttpReads.Implicits.readRaw)
      .map(parse)

  private def parse(response: HttpResponse): List[Relief] =
    if response.status != 200 then
      logger.warn(s"ETMP reliefs lookup returned status ${response.status}")
      throw ReliefClientException(s"ETMP reliefs lookup returned status ${response.status}")
    else
      Json.parse(response.body).validate[List[Relief]] match
        case JsSuccess(reliefs, _) => reliefs
        case JsError(errors) =>
          logger.warn(s"Could not parse ETMP reliefs response: $errors")
          throw ReliefClientException("Could not parse ETMP reliefs response")

object ReliefClientImpl:
  private val reliefReads: Reads[Relief] =
    (
      (__ \ "reliefName").read[String] and
        (__ \ "reliefPercentage").read[Int]
    )(Relief.apply)

  given Reads[List[Relief]] =
    (__ \ "success" \ "reliefs").read(using Reads.list(using reliefReads))
