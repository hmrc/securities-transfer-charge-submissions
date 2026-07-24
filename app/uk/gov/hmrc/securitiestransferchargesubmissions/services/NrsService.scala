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

package uk.gov.hmrc.securitiestransferchargesubmissions.services

import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.{NrsBulkSubmissionRequest, NrsSubmission, NrsSingleSubmissionRequest, NrsSubmissionResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NrsService @Inject()(
  nrsConnector: NrsConnector
)(implicit ec: ExecutionContext) extends Logging {

  def submitSingle(request: NrsSingleSubmissionRequest)(implicit hc: HeaderCarrier): Future[Unit] = {
    val nrsSubmission = NrsSubmission(
      payload = request.payload,
      metadata = request.metadata
    )

    nrsConnector.submitToNrs(nrsSubmission).map {
      case Some(resp) =>
        logger.info(s"Single NRS submission completed successfully: ${resp.nrSubmissionId}")
      case None =>
        logger.warn("Single NRS submission failed - continuing with main flow")
    }.recover {
      case ex: Exception =>
        logger.error(s"Exception during single NRS submission: ${ex.getMessage}", ex)
    }
  }

  def submitBulk(request: NrsBulkSubmissionRequest)(implicit hc: HeaderCarrier): Future[Unit] = {
    val nrsSubmission = NrsSubmission(
      payload = request.payload,
      metadata = request.metadata
    )

    nrsConnector.submitToNrs(nrsSubmission).map {
      case Some(resp) =>
        logger.info(s"Bulk NRS submission completed successfully: ${resp.nrSubmissionId}")
      case None =>
        logger.warn("Bulk NRS submission failed - continuing with main flow")
    }.recover {
      case ex: Exception =>
        logger.error(s"Exception during bulk NRS submission: ${ex.getMessage}", ex)
    }
  }
}