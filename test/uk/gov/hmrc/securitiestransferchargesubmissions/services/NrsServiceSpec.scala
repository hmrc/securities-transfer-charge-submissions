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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securitiestransferchargesubmissions.SpecBase
import uk.gov.hmrc.securitiestransferchargesubmissions.connectors.{NrsAttachmentResponse, NrsConnector}
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.*
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.NrsTestData.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class NrsServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  private val mockNrsConnector = mock[NrsConnector]
  private val service = new NrsService(mockNrsConnector)

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    super.beforeEach()
    org.mockito.Mockito.reset(mockNrsConnector)
  }

  "NrsService" when {

    "submitSingle" should {

      val singleRequest = testSingleRequest

      "successfully submit to NRS and log success" in {
        val expectedResponse = NrsSubmissionResponse("nrs-id-123")
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(expectedResponse)))

        val result = service.submitSingle(singleRequest)

        whenReady(result) { _ =>
          verify(mockNrsConnector).submitToNrs(any[NrsSubmission])(any[HeaderCarrier])
        }
      }

      "handle NRS submission failure gracefully" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        val result = service.submitSingle(singleRequest)

        whenReady(result) { _ =>
          verify(mockNrsConnector).submitToNrs(any[NrsSubmission])(any[HeaderCarrier])
        }
      }

      "handle NRS submission exception gracefully" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException("NRS connection failed")))

        val result = service.submitSingle(singleRequest)

        whenReady(result) { _ =>
          verify(mockNrsConnector).submitToNrs(any[NrsSubmission])(any[HeaderCarrier])
        }
      }

      "convert NrsSingleSubmissionRequest to NrsSubmission correctly" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(testNrsResponse)))

        service.submitSingle(testSingleRequest)

        verify(mockNrsConnector).submitToNrs(
          org.mockito.ArgumentMatchers.argThat[NrsSubmission] { submission =>
            submission.payload == testSingleRequest.payload &&
              submission.metadata == testSingleRequest.metadata
          }
        )(any[HeaderCarrier])
      }

      "complete even when connector returns None" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        val result = service.submitSingle(singleRequest)

        whenReady(result) { _ =>
          succeed
        }
      }

      "complete even when connector throws exception" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new Exception("Test exception")))

        val result = service.submitSingle(singleRequest)

        whenReady(result) { _ =>
          succeed
        }
      }
    }

    "submitBulk" should {

      "successfully submit to NRS and log success" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(testNrsResponse)))

        when(mockNrsConnector.submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(NrsAttachmentResponse("att-123"))))

        val result = service.submitBulk(testBulkRequest)

        whenReady(result) { _ =>
          verify(mockNrsConnector).submitToNrs(any[NrsSubmission])(any[HeaderCarrier])
          verify(mockNrsConnector).submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier])
        }
      }

      "handle NRS submission failure gracefully and not submit attachment" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        val result = service.submitBulk(testBulkRequest)

        whenReady(result) { _ =>
          verify(mockNrsConnector).submitToNrs(any[NrsSubmission])(any[HeaderCarrier])
          verify(mockNrsConnector, never()).submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier])
        }
      }

      "handle NRS submission exception gracefully" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException("NRS connection failed")))

        val result = service.submitBulk(testBulkRequest)

        whenReady(result) { _ =>
          verify(mockNrsConnector).submitToNrs(any[NrsSubmission])(any[HeaderCarrier])
          verify(mockNrsConnector, never()).submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier])
        }
      }

      "handle attachment submission failure gracefully" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(testNrsResponse)))

        when(mockNrsConnector.submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        val result = service.submitBulk(testBulkRequest)

        whenReady(result) { _ =>
          verify(mockNrsConnector).submitToNrs(any[NrsSubmission])(any[HeaderCarrier])
          verify(mockNrsConnector).submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier])
        }
      }

      "handle attachment exception gracefully" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(testNrsResponse)))

        when(mockNrsConnector.submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException("Attachment exception")))

        val result = service.submitBulk(testBulkRequest)

        whenReady(result) { _ =>
          verify(mockNrsConnector).submitToNrs(any[NrsSubmission])(any[HeaderCarrier])
          verify(mockNrsConnector).submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier])
        }
      }

      "create correct attachment request with submission ID" in {
        val capturedAttachmentRequest = ArgumentCaptor.forClass(classOf[NrsAttachmentRequest])

        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(testNrsResponse)))

        when(mockNrsConnector.submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(NrsAttachmentResponse("att-123"))))

        val result = service.submitBulk(testBulkRequest)

        Await.result(result, 5.seconds)

        verify(mockNrsConnector).submitAttachment(capturedAttachmentRequest.capture())(any[HeaderCarrier])

        val attachmentRequest = capturedAttachmentRequest.getValue
        attachmentRequest.nrSubmissionId shouldBe testNrsResponse.nrSubmissionId
        attachmentRequest.businessId shouldBe testBulkRequest.metadata.businessId
        attachmentRequest.notableEvent shouldBe testBulkRequest.metadata.notableEvent
        attachmentRequest.attachmentUrl shouldBe testBulkRequest.attachments.attachmentUrl
        attachmentRequest.attachmentId shouldBe testBulkRequest.attachments.attachmentId
      }

      "convert NrsBulkSubmissionRequest to NrsSubmission correctly" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(testNrsResponse)))

        when(mockNrsConnector.submitAttachment(any[NrsAttachmentRequest])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(NrsAttachmentResponse("att-123"))))

        service.submitBulk(testBulkRequest)

        verify(mockNrsConnector).submitToNrs(
          org.mockito.ArgumentMatchers.argThat[NrsSubmission] { submission =>
            submission.payload == testBulkRequest.payload &&
              submission.metadata == testBulkRequest.metadata
          }
        )(any[HeaderCarrier])
      }

      "complete even when connector returns None" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        val result = service.submitBulk(testBulkRequest)

        whenReady(result) { _ =>
          succeed
        }
      }

      "complete even when connector throws exception" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new Exception("Test exception")))

        val result = service.submitBulk(testBulkRequest)

        whenReady(result) { _ =>
          succeed
        }
      }
    }

    "fire-and-forget pattern" should {

      "not propagate failures to the caller for single submissions" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException("Simulated NRS failure")))

        val result = service.submitSingle(testSingleRequest)

        whenReady(result) { _ =>
          succeed
        }
      }

      "not propagate failures to the caller for bulk submissions" in {
        when(mockNrsConnector.submitToNrs(any[NrsSubmission])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException("Simulated NRS failure")))

        val result = service.submitBulk(testBulkRequest)

        whenReady(result) { _ =>
          succeed
        }
      }
    }
  }
}