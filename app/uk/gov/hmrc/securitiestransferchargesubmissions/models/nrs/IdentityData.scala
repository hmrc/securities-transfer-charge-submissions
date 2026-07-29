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

package uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

case class Credentials(providerId: String, providerType: String)

object Credentials {
  implicit val format: OFormat[Credentials] = Json.format[Credentials]
}

case class Name(name: Option[String], lastName: Option[String])

object Name {
  implicit val format: OFormat[Name] = Json.format[Name]
}

case class LoginTimes(currentLogin: String, previousLogin: Option[String])

object LoginTimes {
  implicit val format: OFormat[LoginTimes] = Json.format[LoginTimes]
}

case class ItmpName(givenName: Option[String], middleName: Option[String], familyName: Option[String])

object ItmpName {
  implicit val format: OFormat[ItmpName] = Json.format[ItmpName]
}

case class ItmpAddress(
  line1: Option[String],
  line2: Option[String],
  line3: Option[String],
  line4: Option[String],
  line5: Option[String],
  postCode: Option[String],
  countryName: Option[String],
  countryCode: Option[String]
)

object ItmpAddress {
  implicit val format: OFormat[ItmpAddress] = Json.format[ItmpAddress]
}

case class AgentInformation(
  agentId: Option[String],
  agentCode: Option[String],
  agentFriendlyName: Option[String]
)

object AgentInformation {
  implicit val format: OFormat[AgentInformation] = Json.format[AgentInformation]
}

case class MdtpInformation(deviceId: String, sessionId: String)

object MdtpInformation {
  implicit val format: OFormat[MdtpInformation] = Json.format[MdtpInformation]
}

case class IdentityData(
  internalId: Option[String] = None,
  externalId: Option[String] = None,
  agentCode: Option[String] = None,
  credentials: Option[Credentials] = None,
  confidenceLevel: Int,
  nino: Option[String] = None,
  saUtr: Option[String] = None,
  name: Option[Name] = None,
  dateOfBirth: Option[LocalDate] = None,
  email: Option[String] = None,
  agentInformation: Option[AgentInformation] = None,
  groupIdentifier: Option[String] = None,
  credentialRole: Option[String] = None,
  mdtpInformation: Option[MdtpInformation] = None,
  itmpName: Option[ItmpName] = None,
  itmpDateOfBirth: Option[LocalDate] = None,
  itmpAddress: Option[ItmpAddress] = None,
  affinityGroup: Option[String] = None,
  credentialStrength: Option[String] = None,
  loginTimes: Option[LoginTimes] = None
)

object IdentityData {
  implicit val format: OFormat[IdentityData] = Json.format[IdentityData]
}