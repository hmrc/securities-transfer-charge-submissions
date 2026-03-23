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

package uk.gov.hmrc.securitiestransferchargesubmissions.domain.models.etmp

import play.api.libs.json.*
import play.api.libs.json.Reads.IntReads

enum TransactionType(val value: Int):
  case STF    extends TransactionType(1)
  case SH03   extends TransactionType(2)
  case Other  extends TransactionType(3)

object TransactionType:
  given transactionTypeFormat: Format[TransactionType] = new Format[TransactionType]:

    def reads(json: JsValue): JsResult[TransactionType] = {
      json.validate[Int].flatMap(value => TransactionType.values.find(_.value == value) match
        case Some(transactionType) => JsSuccess(transactionType)
        case None => JsError(s"Invalid TransactionType value: $value")
      )
    }

    override def writes(o: TransactionType): JsValue = JsNumber(o.value)
