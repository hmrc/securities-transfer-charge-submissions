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

import ch.qos.logback.classic.Level
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, verify, when}
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.securitiestransferchargesubmissions.SpecBase
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{NrsAttachmentResponse, NrsConnector}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.NrsTestData.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NrsServiceSpec extends SpecBase with LogCapturing {

  private val mockNrsConnector = mock[NrsConnector]
  private val service = new NrsService(mockNrsConnector)

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockNrsConnector)
  }

  private def stubSubmission(result: Future[Option[NrsSubmissionResponse]]): Unit =
    when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier])).thenReturn(result)

  private def stubAttachment(result: Future[Option[NrsAttachmentResponse]]): Unit =
    when(mockNrsConnector.submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier])).thenReturn(result)

  /** Runs `call` while capturing the service's logs, and returns (level, message) pairs once it completes. */
  private def logsFrom(call: => Future[Unit]): Seq[(Level, String)] = {
    var logs: Seq[(Level, String)] = Nil
    withCaptureOfLoggingFrom(Logger(classOf[NrsService])) { events =>
      whenReady(call)(_ => ())
      logs = events.map(e => (e.getLevel, e.getFormattedMessage))
    }
    logs
  }

  "NrsService.submitSingle" should {

    "log success when NRS accepts the submission" in {
      stubSubmission(Future.successful(Some(testNrsResponse)))

      logsFrom(service.submitSingle(testSingleRequest)) shouldBe
        Seq(Level.INFO -> "Single NRS submission completed successfully")
    }

    "warn, and still complete, when NRS does not accept the submission" in {
      stubSubmission(Future.successful(None))

      logsFrom(service.submitSingle(testSingleRequest)) shouldBe
        Seq(Level.WARN -> "Single NRS submission failed - continuing with main flow")
    }

    "log an error, and still complete, when the connector fails" in {
      stubSubmission(Future.failed(new RuntimeException("NRS connection failed")))

      logsFrom(service.submitSingle(testSingleRequest)) shouldBe
        Seq(Level.ERROR -> "Unexpected error during single NRS submission - continuing with main flow")
    }

    "send the request's payload and metadata to the connector" in {
      stubSubmission(Future.successful(Some(testNrsResponse)))

      whenReady(service.submitSingle(testSingleRequest))(_ => ())

      val captor = ArgumentCaptor.forClass(classOf[NrsSubmission])
      verify(mockNrsConnector).submitToNrs(captor.capture())(any[HeaderCarrier])
      captor.getValue shouldBe NrsSubmission(testSingleRequest.payload, testSingleRequest.metadata)
    }
  }

  "NrsService.submitBulk" should {

    "submit the attachment with the NRS submission id and log both successes" in {
      stubSubmission(Future.successful(Some(testNrsResponse)))
      stubAttachment(Future.successful(Some(NrsAttachmentResponse("att-123"))))

      logsFrom(service.submitBulk(testBulkRequest)) shouldBe Seq(
        Level.INFO -> "Bulk NRS submission completed successfully",
        Level.INFO -> "NRS attachment submitted successfully"
      )

      val captor = ArgumentCaptor.forClass(classOf[NrsAttachmentRequest])
      verify(mockNrsConnector).submitAttachment(captor.capture())(any[HeaderCarrier])
      captor.getValue shouldBe NrsAttachmentRequest(
        businessId = testBulkRequest.metadata.businessId,
        notableEvent = testBulkRequest.metadata.notableEvent,
        attachmentUrl = testBulkRequest.attachments.attachmentUrl,
        attachmentId = testBulkRequest.attachments.attachmentId,
        attachmentSha256Checksum = testBulkRequest.attachments.attachmentSha256Checksum,
        attachmentContentType = testBulkRequest.attachments.attachmentContentType,
        nrSubmissionId = testNrsResponse.nrSubmissionId
      )
    }

    "send the request's payload and metadata to the connector" in {
      stubSubmission(Future.successful(Some(testNrsResponse)))
      stubAttachment(Future.successful(Some(NrsAttachmentResponse("att-123"))))

      whenReady(service.submitBulk(testBulkRequest))(_ => ())

      val captor = ArgumentCaptor.forClass(classOf[NrsSubmission])
      verify(mockNrsConnector).submitToNrs(captor.capture())(any[HeaderCarrier])
      captor.getValue shouldBe NrsSubmission(testBulkRequest.payload, testBulkRequest.metadata)
    }

    "warn when the attachment is not accepted" in {
      stubSubmission(Future.successful(Some(testNrsResponse)))
      stubAttachment(Future.successful(None))

      logsFrom(service.submitBulk(testBulkRequest)) shouldBe Seq(
        Level.INFO -> "Bulk NRS submission completed successfully",
        Level.WARN -> "NRS attachment submission failed"
      )
    }

    "log an error, and still complete, when the attachment call fails" in {
      stubSubmission(Future.successful(Some(testNrsResponse)))
      stubAttachment(Future.failed(new RuntimeException("Attachment exception")))

      logsFrom(service.submitBulk(testBulkRequest)) shouldBe Seq(
        Level.INFO  -> "Bulk NRS submission completed successfully",
        Level.ERROR -> "Unexpected error during NRS attachment submission"
      )
    }

    "not submit the attachment when the submission is not accepted" in {
      stubSubmission(Future.successful(None))

      logsFrom(service.submitBulk(testBulkRequest)) shouldBe
        Seq(Level.WARN -> "Bulk NRS submission failed - continuing with main flow")
      verify(mockNrsConnector, never()).submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier])
    }

    "not submit the attachment, and still complete, when the submission call fails" in {
      stubSubmission(Future.failed(new RuntimeException("NRS connection failed")))

      logsFrom(service.submitBulk(testBulkRequest)) shouldBe
        Seq(Level.ERROR -> "Unexpected error during bulk NRS submission - continuing with main flow")
      verify(mockNrsConnector, never()).submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier])
    }
  }
}
