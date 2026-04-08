# Glossary

| Term           | Definition                                                                                                                                                                                      |
|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Bulk upload    | Sending the details of one or more transfers in file format. The file is a spreadsheet that is available in template form from the STC service and must be filled out and uploaded by the user. |
| Posting        | The process of sending a completed draft for processing. See also submission.                                                                                                                   |
| Submission     | The completed draft that is posted to the system for processing.                                                                                                                                |
| View and amend | The ability to change a submission. Not to be confused with save and return.                                                                                                                    |

# Summary
This service deals with transfer details.

Transfer details can be collected from the UI forms and submitted as one or more transfers in a batch payload.

Current submission endpoint:

- `POST /submission/:submissionId`
- headers: `correlation-id`, `subscription-id`
- body: `SubmissionBatchPayload` containing a single `declaration` plus one or more `transfers`

After submission, transfer details can be viewed and amended if required, although not all fields are amendable.

## ETMP create client

This service now contains a Play HTTP client for the ETMP create endpoint:

- client trait: `app/uk/gov/hmrc/securitiestransferchargesubmissions/clients/etmp/SubmissionClient.scala`
- request models: `app/uk/gov/hmrc/securitiestransferchargesubmissions/clients/etmp/StcTransactionCreateRequest.scala`
- response models: `app/uk/gov/hmrc/securitiestransferchargesubmissions/clients/etmp/StcTransactionCreateResponse.scala`

### Required config

Set ETMP endpoint details under:

- `microservice.services.etmp-transaction.host`
- `microservice.services.etmp-transaction.port`
- `microservice.services.etmp-transaction.protocol` (default `http`)
- `microservice.services.etmp-transaction.originating-system` (default `MDTP-STC`)
- `microservice.services.etmp-transaction.transmitting-system` (default `HIP`)

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
