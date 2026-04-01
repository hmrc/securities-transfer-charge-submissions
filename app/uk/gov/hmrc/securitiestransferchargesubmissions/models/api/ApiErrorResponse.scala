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

package uk.gov.hmrc.securitiestransferchargesubmissions.models.api

import play.api.libs.json.{JsValue, Json, OWrites}

final case class ApiErrorResponse(error: String, details: Option[JsValue] = None)

object ApiErrorResponse:
  given OWrites[ApiErrorResponse] = OWrites { response =>
    Json.obj("error" -> response.error) ++
      response.details.fold(Json.obj())(value => Json.obj("details" -> value))
  }
