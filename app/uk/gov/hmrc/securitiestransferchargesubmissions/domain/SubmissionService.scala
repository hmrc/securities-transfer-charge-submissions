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

package uk.gov.hmrc.securitiestransferchargesubmissions.domain

import uk.gov.hmrc.securitiestransferchargesubmissions.domain.models.TransferData

import scala.concurrent.Future

trait SubmissionResult:
  def fold[A](onFailure: String => A)(onSuccess: String => A): A = this match
    case SubmissionSuccess(utrn) => onSuccess(utrn)
    case SubmissionFailure(error) => onFailure(error)

case class SubmissionSuccess(utrn: String) extends SubmissionResult
case class SubmissionFailure(error: String) extends SubmissionResult

trait SubmissionService:
  def submitSingle(data: TransferData): Future[SubmissionResult]
  def submitMultiple(data: Seq[TransferData]): Future[Seq[SubmissionResult]]
