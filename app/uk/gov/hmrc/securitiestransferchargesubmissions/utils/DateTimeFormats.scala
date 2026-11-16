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

package uk.gov.hmrc.securitiestransferchargesubmissions.utils

import play.api.i18n.Lang

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, ZoneId}
import java.util.Locale

object DateTimeFormats {

  val ukZoneId: ZoneId = ZoneId.of("Europe/London")
  
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

  private val localisedDateTimeFormatters = Map(
    "en" -> dateTimeFormatter,
    "cy" -> dateTimeFormatter.withLocale(Locale.forLanguageTag("cy"))
  )

  def dateTimeFormat()(implicit lang: Lang): DateTimeFormatter = {
    localisedDateTimeFormatters.getOrElse(lang.code, dateTimeFormatter)
  }

  val dateTimeHintFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d M yyyy")

  def formatDate(date: LocalDate)(implicit lang: Lang): String = {
    date.format(dateTimeFormat())
  }
  
  // ETMP expects yyyy-MM-ddTHH:mm:ssZ in UTC (e.g. 2026-03-31T12:00:00Z)
  def utcDateTimeStamp(instant: Instant): String =
    DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS))
}
