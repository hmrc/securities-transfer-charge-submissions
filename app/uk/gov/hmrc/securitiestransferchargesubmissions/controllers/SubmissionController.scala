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

package uk.gov.hmrc.securitiestransferchargesubmissions.controllers

import play.api.libs.json.JsValue
import play.api.mvc.*
import uk.gov.hmrc.securitiestransferchargesubmissions.domain.SubmissionService
import uk.gov.hmrc.securitiestransferchargesubmissions.domain.models.TransferData

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubmissionController @Inject()(submissionService: SubmissionService,
                                     val controllerComponents: MessagesControllerComponents)
                                    (implicit ec: ExecutionContext) extends BaseController:
  def notJson: Future[Result] = ???
  def handleSubmissionError(error: String): Result = ???
  def handleSubmissionSuccess(utrn: String): Result = ???
  def extractTransferData(json: JsValue): Either[Result, TransferData] = ???
  
  def submitSingle(): Action[AnyContent] = Action.async { implicit request =>
    request.body.asJson.fold(notJson) { json =>
      extractTransferData(json)
        .fold(Future.successful, { transferData =>
          for {
            etmpResp <- submissionService.submitSingle(transferData)
            resp      = etmpResp.fold(handleSubmissionError)(handleSubmissionSuccess)
          } yield resp
        })
    }
  }

  def submitMultiple(): Action[AnyContent] = ???

