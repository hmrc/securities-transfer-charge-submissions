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

package uk.gov.hmrc.securitiestransferchargesubmissions

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.securitiestransferchargesubmissions.config.AppConfig

trait SpecBase extends AnyWordSpec 
  with Matchers 
  with MockitoSugar 
  with BeforeAndAfterEach 
  with ScalaFutures 
  with OptionValues {

  protected def testConfiguration: Configuration = Configuration(testConfig)

  protected def testConfig: Config = ConfigFactory.parseString(
    """
      |appName = securities-transfer-charge-submissions
      |
      |microservice {
      |  services {
      |    auth {
      |      host = localhost
      |      port = 8500
      |    }
      |    etmp-transaction {
      |      host = localhost
      |      port = 30038
      |      protocol = "http"
      |      prefix = "securities-transfer-charge-stubs"
      |      originating-system = "MDTP-STC"
      |      transmitting-system = "HIP"
      |
      |      create {
      |        max-retries = 2
      |        initial-backoff-ms = 200
      |        max-records-per-request = 12
      |        max-concurrent-calls = 3
      |      }
      |    }
      |    
      |    nrs {
      |      host = localhost
      |      port = 9389
      |      protocol = "http"
      |      api-key = "test-api-key"
      |    }
      |  }
      |}
      |
      |nrs.retries = ["1s", "2s", "4s"]
      |""".stripMargin
  )

  protected lazy val appConfig: AppConfig = new AppConfig(testConfiguration)

  protected def appConfigWithOverrides(overrides: String): AppConfig = {
    val configWithOverrides = ConfigFactory.parseString(overrides).withFallback(testConfig)
    new AppConfig(Configuration(configWithOverrides))
  }
}
