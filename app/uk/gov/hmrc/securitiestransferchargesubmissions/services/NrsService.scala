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
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{NrsAttachmentResponse, NrsConnector}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NrsService @Inject()(
                            nrsConnector: NrsConnector
                          )(implicit ec: ExecutionContext) extends Logging {

  /**
   * Submit single (HTML) submission to NRS
   * Fire-and-forget pattern - failures are logged but don't block the main flow
   */
  def submitSingle(request: NrsSingleSubmissionRequest)(implicit hc: HeaderCarrier): Future[Unit] = {
    val nrsSubmission = NrsSubmission(
      payload = request.payload,
      metadata = request.metadata
    )

    nrsConnector.submitToNrs(nrsSubmission).map {
      case Some(_) =>
        logger.info("Single NRS submission completed successfully")
      case None =>
        logger.warn("Single NRS submission failed - continuing with main flow")
    }.recover {
      case ex: Exception =>
        logger.error("Unexpected error during single NRS submission - continuing with main flow")
    }
  }

  /**
   * Submit bulk (HTML) submission to NRS with attachments
   * Fire-and-forget pattern - failures are logged but don't block the main flow
   */
  def submitBulk(request: NrsBulkSubmissionRequest)(implicit hc: HeaderCarrier): Future[Unit] = {
    val nrsSubmission = NrsSubmission(
      payload = request.payload,
      metadata = request.metadata
    )

    nrsConnector.submitToNrs(nrsSubmission).flatMap {
      case Some(submissionResp) =>
        logger.info("Bulk NRS submission completed successfully")

        submitAttachment(
          submissionResp.nrSubmissionId,
          request.metadata.businessId,
          request.metadata.notableEvent,
          request.attachments
        ).map {
          case Some(_) =>
            logger.info("NRS attachment submitted successfully")
          case None =>
            logger.warn("NRS attachment submission failed")
        }.recover {
          case _: Exception =>
            logger.error("Unexpected error during NRS attachment submission")
        }

      case None =>
        logger.warn("Bulk NRS submission failed - continuing with main flow")
        Future.successful(())
    }.recover {
      case ex: Exception =>
        logger.error("Unexpected error during bulk NRS submission - continuing with main flow")
    }
  }

  private def submitAttachment(
                                nrSubmissionId: String,
                                businessId: String,
                                notableEvent: String,
                                attachment: NrsAttachment
                              )(implicit hc: HeaderCarrier): Future[Option[NrsAttachmentResponse]] = {
    val attachmentRequest = NrsAttachmentRequest(
      businessId = businessId,
      notableEvent = notableEvent,
      attachmentUrl = attachment.attachmentUrl,
      attachmentId = attachment.attachmentId,
      attachmentSha256Checksum = attachment.attachmentSha256Checksum,
      attachmentContentType = attachment.attachmentContentType,
      nrSubmissionId = nrSubmissionId
    )

    nrsConnector.submitAttachment(attachmentRequest)
  }
}