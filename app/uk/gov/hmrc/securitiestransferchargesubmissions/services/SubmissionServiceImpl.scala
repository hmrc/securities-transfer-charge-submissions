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

package uk.gov.hmrc.securitiestransferchargesubmissions.services

import uk.gov.hmrc.securitiestransferchargesubmissions.domain.models.TransferData
import uk.gov.hmrc.securitiestransferchargesubmissions.domain.{EtmpService, ParsingService, SubmissionFailure, SubmissionResult, SubmissionService, SubmissionSuccess}

import javax.inject.Inject
import scala.concurrent.Future


class SubmissionServiceImpl @Inject() (parser: ParsingService, etmp: EtmpService)
                                      (implicit ec: scala.concurrent.ExecutionContext) extends SubmissionService:

  def parserError(error: String): Future[SubmissionResult] = ???

  
  override def submitSingle(data: TransferData): Future[SubmissionResult] =
    parser.parseSingle(data).fold(parserError, etmpData =>
      etmp.createTransfer(etmpData)
        .map(result => result.fold(SubmissionFailure.apply, SubmissionSuccess.apply))
    )
    
  override def submitMultiple(data: Seq[TransferData]): Future[Seq[SubmissionResult]] = ???

