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

package uk.gov.hmrc.securitiestransferchargesubmissions.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

@Singleton
class AppConfig @Inject()(config: Configuration):

  val appName: String = config.get[String]("appName")

  private val etmpHost = config.get[String]("microservice.services.etmp-transaction.host")
  private val etmpPort = config.get[Int]("microservice.services.etmp-transaction.port")
  private val etmpProtocol = config.getOptional[String]("microservice.services.etmp-transaction.protocol").getOrElse("http")

  val etmpTransactionBaseUrl: String = s"$etmpProtocol://$etmpHost:$etmpPort"
  val etmpOriginatingSystem: String = config
    .getOptional[String]("microservice.services.etmp-transaction.originating-system")
    .getOrElse("MDTP-STC")
  val etmpTransmittingSystem: String = config
    .getOptional[String]("microservice.services.etmp-transaction.transmitting-system")
    .getOrElse("HIP")
  val etmpCreateMaxRetries: Int = config
    .getOptional[Int]("microservice.services.etmp-transaction.create.max-retries")
    .getOrElse(0)

  val etmpCreateInitialBackoff: FiniteDuration = FiniteDuration(
    config.getOptional[Long]("microservice.services.etmp-transaction.create.initial-backoff-ms").getOrElse(200L),
    MILLISECONDS
  )

  val etmpCreateMaxRecordsPerRequest: Int = config
    .getOptional[Int]("microservice.services.etmp-transaction.create.max-records-per-request")
    .getOrElse(12)

  require(
    etmpCreateMaxRecordsPerRequest > 0,
    "microservice.services.etmp-transaction.create.max-records-per-request must be > 0"
  )

  val etmpCreateMaxConcurrentCalls: Int = config
    .getOptional[Int]("microservice.services.etmp-transaction.create.max-concurrent-calls")
    .getOrElse(3)
