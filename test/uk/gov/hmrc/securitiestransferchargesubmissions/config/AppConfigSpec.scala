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

import uk.gov.hmrc.securitiestransferchargesubmissions.SpecBase

import scala.concurrent.duration.*

class AppConfigSpec extends SpecBase:

  "AppConfig" should:
    "build the ETMP base URL from protocol, host, port and prefix" in:
      appConfig.etmpTransactionBaseUrl shouldBe "http://localhost:30038/securities-transfer-charge-stubs"

    "omit the path when no ETMP prefix is configured" in:
      appConfigWithOverrides("""microservice.services.etmp-transaction.prefix = """"").etmpTransactionBaseUrl shouldBe
        "http://localhost:30038"

    "read the create retry policy" in:
      appConfig.etmpCreateRetryPolicy shouldBe RetryPolicy(2, 200.milliseconds)

    "read the view retry policy separately from the create one" in:
      appConfigWithOverrides(
        """
          |microservice.services.etmp-transaction.view.max-retries = 4
          |microservice.services.etmp-transaction.view.initial-backoff-ms = 50
          |""".stripMargin
      ).etmpViewRetryPolicy shouldBe RetryPolicy(4, 50.milliseconds)

    "not retry view calls unless configured" in:
      appConfig.etmpViewRetryPolicy shouldBe RetryPolicy(0, 200.milliseconds)

    "read the NRS retry delays" in:
      appConfig.nrsRetryDelays shouldBe Seq(1.second, 2.seconds, 4.seconds)

    "reject a non-positive max records per request" in:
      val exception = the[IllegalArgumentException] thrownBy
        appConfigWithOverrides("microservice.services.etmp-transaction.create.max-records-per-request = 0")

      exception.getMessage should include("max-records-per-request must be > 0")
