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

package uk.gov.hmrc.securitiestransferchargesubmissions

import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module => AppModule}
import uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.{SubmissionClient, SubmissionClientImpl}
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{SubmissionConnector, SubmissionConnectorImpl}
import uk.gov.hmrc.securitiestransferchargesubmissions.services.{SubmissionService, SubmissionServiceImpl}
import uk.gov.hmrc.securitiestransferchargesubmissions.validation.{TransferTransformationValidator, TransferTransformationValidatorImpl}

import java.time.Clock

class Module extends AppModule:

  override def bindings(
    environment  : Environment,
    configuration: Configuration
  ): Seq[Binding[_]] =
    bind[Clock].toInstance(Clock.systemDefaultZone) ::
    bind[SubmissionClient].to[SubmissionClientImpl] ::
    bind[SubmissionConnector].to[SubmissionConnectorImpl] ::
    bind[TransferTransformationValidator].to[TransferTransformationValidatorImpl] ::
    bind[SubmissionService].to[SubmissionServiceImpl] ::
    Nil
