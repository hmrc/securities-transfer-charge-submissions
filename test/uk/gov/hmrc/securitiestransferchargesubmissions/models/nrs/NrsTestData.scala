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

package uk.gov.hmrc.securitiestransferchargesubmissions.models.nrs

import java.time.LocalDate

object NrsTestData {

  val testTimestamp: String = "2024-01-15T10:30:00.000Z"
  val alternateTimestamp: String = "2026-07-27T10:00:00.000Z"

  val encodedHtmlPayload: String = java.util.Base64.getEncoder.encodeToString("<html>test</html>".getBytes("UTF-8"))
  val encodedXmlPayload: String = java.util.Base64.getEncoder.encodeToString("<xml>test</xml>".getBytes("UTF-8"))
  val encodedTestPayload: String = java.util.Base64.getEncoder.encodeToString("test payload".getBytes("UTF-8"))
  val encodedBase64Payload: String = "dGVzdCBwYXlsb2Fk"

  val testCredentials: Credentials = Credentials(
    providerId = "12345",
    providerType = "GovernmentGateway"
  )

  val testName: Name = Name(
    name = Some("John"),
    lastName = Some("Doe")
  )

  val testLoginTimes: LoginTimes = LoginTimes(
    currentLogin = testTimestamp,
    previousLogin = None
  )

  val testLoginTimesWithPrevious: LoginTimes = LoginTimes(
    currentLogin = "2026-07-27T10:00:00.000Z",
    previousLogin = Some("2026-07-26T09:00:00.000Z")
  )

  val testItmpName: ItmpName = ItmpName(
    givenName = Some("John"),
    middleName = None,
    familyName = Some("Doe")
  )

  val testItmpAddress: ItmpAddress = ItmpAddress(
    line1 = Some("123 Test St"),
    line2 = None,
    line3 = None,
    line4 = None,
    line5 = None,
    postCode = None,
    countryName = None,
    countryCode = None
  )

  val fullItmpAddress: ItmpAddress = ItmpAddress(
    line1 = Some("123 Test Street"),
    line2 = Some("Test Area"),
    line3 = Some("Test Town"),
    line4 = Some("Test County"),
    line5 = Some("Test Region"),
    postCode = Some("TE1 1ST"),
    countryName = Some("United Kingdom"),
    countryCode = Some("GB")
  )

  val testAgentInformation: AgentInformation = AgentInformation(
    agentId = Some("AGENT001"),
    agentCode = Some("AGENT001"),
    agentFriendlyName = None
  )

  val testMdtpInformation: MdtpInformation = MdtpInformation(
    deviceId = "device-123",
    sessionId = "session-123"
  )

  val minimalIdentityData: IdentityData = IdentityData(
    confidenceLevel = 200
  )

  val testIdentityData: IdentityData = IdentityData(
    internalId = Some("test-internal-id"),
    externalId = Some("test-external-id"),
    agentCode = None,
    credentials = Some(testCredentials),
    confidenceLevel = 200,
    nino = Some("AB123456C"),
    saUtr = Some("1234567890"),
    name = Some(testName),
    dateOfBirth = Some(LocalDate.parse("1980-01-01")),
    email = Some("test@example.com"),
    agentInformation = None,
    groupIdentifier = Some("group-123"),
    credentialRole = Some("User"),
    mdtpInformation = Some(testMdtpInformation),
    itmpName = Some(testItmpName),
    itmpDateOfBirth = Some(LocalDate.parse("1980-01-01")),
    itmpAddress = Some(testItmpAddress),
    affinityGroup = Some("Individual"),
    credentialStrength = Some("strong"),
    loginTimes = Some(testLoginTimes)
  )

  val fullIdentityData: IdentityData = IdentityData(
    internalId = Some("int-id-123"),
    externalId = Some("ext-id-456"),
    agentCode = Some("AGENT001"),
    credentials = Some(testCredentials),
    confidenceLevel = 200,
    nino = Some("AB123456C"),
    saUtr = Some("1234567890"),
    name = Some(testName),
    dateOfBirth = Some(LocalDate.of(1980, 1, 15)),
    email = Some("john.doe@example.com"),
    agentInformation = Some(testAgentInformation),
    groupIdentifier = Some("group-id-789"),
    credentialRole = Some("User"),
    mdtpInformation = Some(testMdtpInformation),
    itmpName = Some(testItmpName),
    itmpDateOfBirth = Some(LocalDate.of(1980, 1, 15)),
    itmpAddress = Some(fullItmpAddress),
    affinityGroup = Some("Individual"),
    credentialStrength = Some("strong"),
    loginTimes = Some(testLoginTimesWithPrevious)
  )

  val agentIdentityData: IdentityData = IdentityData(
    internalId = Some("agent-internal-id"),
    externalId = Some("agent-external-id"),
    agentCode = Some("AGENT001"),
    credentials = Some(testCredentials),
    confidenceLevel = 200,
    nino = None,
    saUtr = None,
    name = None,
    dateOfBirth = None,
    email = Some("agent@example.com"),
    agentInformation = Some(testAgentInformation),
    groupIdentifier = Some("agent-group-123"),
    credentialRole = Some("User"),
    mdtpInformation = Some(testMdtpInformation),
    itmpName = None,
    itmpDateOfBirth = None,
    itmpAddress = None,
    affinityGroup = Some("Agent"),
    credentialStrength = Some("strong"),
    loginTimes = Some(testLoginTimesWithPrevious)
  )

  val testMetadata: NrsMetadata = NrsMetadata(
    businessId = "stc",
    notableEvent = "stc-submission",
    payloadContentType = "application/json",
    payloadSha256Checksum = "abc123def456",
    userSubmissionTimestamp = testTimestamp,
    identityData = testIdentityData,
    userAuthToken = "Bearer token123",
    headerData = Map("Host" -> "localhost", "User-Agent" -> "test-agent"),
    searchKeys = Map("submissionId" -> "sub-123")
  )

  val minimalMetadata: NrsMetadata = NrsMetadata(
    businessId = "stc",
    notableEvent = "stc-submission",
    payloadContentType = "application/json",
    payloadSha256Checksum = "checksum",
    userSubmissionTimestamp = testTimestamp,
    identityData = minimalIdentityData,
    userAuthToken = "token",
    headerData = Map.empty,
    searchKeys = Map.empty
  )

  val singleHtmlMetadata: NrsMetadata = NrsMetadata(
    businessId = "stc",
    notableEvent = "stc-single-submission",
    payloadContentType = "text/html",
    payloadSha256Checksum = "html-checksum",
    userSubmissionTimestamp = alternateTimestamp,
    identityData = testIdentityData,
    userAuthToken = "Bearer token123",
    headerData = Map("User-Agent" -> "Mozilla/5.0"),
    searchKeys = Map("submissionId" -> "sub-123", "nino" -> "AB123456C")
  )

  val bulkXmlMetadata: NrsMetadata = NrsMetadata(
    businessId = "stc",
    notableEvent = "stc-bulk-submission",
    payloadContentType = "application/xml",
    payloadSha256Checksum = "xml-checksum",
    userSubmissionTimestamp = alternateTimestamp,
    identityData = agentIdentityData,
    userAuthToken = "Bearer token456",
    headerData = Map("User-Agent" -> "Mozilla/5.0", "X-Request-ID" -> "req-123"),
    searchKeys = Map("submissionId" -> "bulk-sub-456", "submissionDate" -> "2026-07-27")
  )

  val testSubmission: NrsSubmission = NrsSubmission(
    payload = encodedTestPayload,
    metadata = testMetadata
  )

  val minimalSubmission: NrsSubmission = NrsSubmission(
    payload = encodedTestPayload,
    metadata = minimalMetadata
  )

  val testSingleRequest: NrsSingleSubmissionRequest = NrsSingleSubmissionRequest(
    payload = encodedBase64Payload,
    metadata = singleHtmlMetadata
  )

  val singleHtmlRequest: NrsSingleSubmissionRequest = NrsSingleSubmissionRequest(
    payload = encodedHtmlPayload,
    metadata = singleHtmlMetadata
  )

  val testBulkRequest: NrsBulkSubmissionRequest = NrsBulkSubmissionRequest(
    payload = encodedBase64Payload,
    metadata = bulkXmlMetadata
  )

  val bulkXmlRequest: NrsBulkSubmissionRequest = NrsBulkSubmissionRequest(
    payload = encodedXmlPayload,
    metadata = bulkXmlMetadata
  )

  val testNrsResponse: NrsSubmissionResponse = NrsSubmissionResponse(
    nrSubmissionId = "test-nrs-id-123"
  )

  def identityDataWith(
    confidenceLevel: Int = 200,
    nino: Option[String] = Some("AB123456C"),
    affinityGroup: Option[String] = Some("Individual")
  ): IdentityData = IdentityData(
    internalId = Some("test-internal-id"),
    externalId = Some("test-external-id"),
    agentCode = None,
    credentials = Some(testCredentials),
    confidenceLevel = confidenceLevel,
    nino = nino,
    saUtr = None,
    name = Some(testName),
    dateOfBirth = None,
    email = Some("test@example.com"),
    agentInformation = None,
    groupIdentifier = Some("group-123"),
    credentialRole = Some("User"),
    mdtpInformation = Some(testMdtpInformation),
    itmpName = None,
    itmpDateOfBirth = None,
    itmpAddress = None,
    affinityGroup = affinityGroup,
    credentialStrength = Some("strong"),
    loginTimes = None
  )

  def metadataWith(
    notableEvent: String = "stc-submission",
    payloadContentType: String = "application/json",
    identityData: IdentityData = testIdentityData
  ): NrsMetadata = NrsMetadata(
    businessId = "stc",
    notableEvent = notableEvent,
    payloadContentType = payloadContentType,
    payloadSha256Checksum = "checksum",
    userSubmissionTimestamp = testTimestamp,
    identityData = identityData,
    userAuthToken = "Bearer token",
    headerData = Map.empty,
    searchKeys = Map.empty
  )

  def singleRequestWith(
    payload: String = encodedBase64Payload,
    metadata: NrsMetadata = singleHtmlMetadata
  ): NrsSingleSubmissionRequest = NrsSingleSubmissionRequest(payload, metadata)

  def bulkRequestWith(
    payload: String = encodedBase64Payload,
    metadata: NrsMetadata = bulkXmlMetadata
  ): NrsBulkSubmissionRequest = NrsBulkSubmissionRequest(payload, metadata)
}