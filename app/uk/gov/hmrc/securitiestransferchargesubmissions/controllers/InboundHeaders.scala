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

/**
 * Headers this service expects on requests from its own callers (e.g. the frontend).
 *
 * Kept separate from [[uk.gov.hmrc.securitiestransferchargesubmissions.clients.etmp.Headers]],
 * which holds the header names ETMP/HIP require on outbound calls. The two contracts
 * differ (inbound "correlation-id" vs ETMP's "correlationid") and must be able to change independently.
 */
object InboundHeaders:
  val CorrelationId  = "correlation-id"
  val SubscriptionId = "subscription-id"
