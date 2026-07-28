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

package uk.gov.hmrc.securitiestransferchargesubmissions.controllers

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs.{NrsBulkSubmissionRequest, NrsSingleSubmissionRequest}
import uk.gov.hmrc.securitiestransferchargesubmissions.services.NrsService

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class NrsController @Inject()(
  cc: ControllerComponents,
  nrsService: NrsService
) extends BackendController(cc) with Logging {

  /**
   * Endpoint to receive single (HTML) submission from frontend
   * POST /nrs/single
   */
  def submitSingle(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[NrsSingleSubmissionRequest].fold(
      errors => {
        logger.warn(s"Invalid single NRS submission request: $errors")
        Future.successful(BadRequest(Json.obj(
          "statusCode" -> 400,
          "message" -> "Invalid request format",
          "errors" -> errors.toString()
        )))
      },
      nrsRequest => {
        nrsService.submitSingle(nrsRequest)
        
        Future.successful(Accepted(Json.obj(
          "message" -> "Single submission accepted for NRS processing"
        )))
      }
    )
  }

  /**
   * Endpoint to receive bulk (XML) submission from frontend
   * POST /nrs/bulk
   */
  def submitBulk(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[NrsBulkSubmissionRequest].fold(
      errors => {
        logger.warn(s"Invalid bulk NRS submission request: $errors")
        Future.successful(BadRequest(Json.obj(
          "statusCode" -> 400,
          "message" -> "Invalid request format",
          "errors" -> errors.toString()
        )))
      },
      nrsRequest => {
        nrsService.submitBulk(nrsRequest)
        
        Future.successful(Accepted(Json.obj(
          "message" -> "Bulk submission accepted for NRS processing"
        )))
      }
    )
  }
}