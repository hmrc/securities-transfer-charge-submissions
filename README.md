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

- `POST /securities-transfer-charge-submissions/submission/:submissionId`
- headers: `correlation-id`, `subscription-id`
- body: `SubmissionBatchPayload` containing a single `declaration` plus one or more `transfers`

After submission, transfer details can be viewed and amended if required, although not all fields are amendable.

### Request validation strategy

Validation runs in three phases. Each phase short-circuits if it finds a problem, so callers always get the most actionable error first:

| Phase | What is checked | Behaviour |
|-------|----------------|-----------|
| 1 – Headers | Both `correlation-id` and `subscription-id` must be present and non-blank | **Fail fast** – returns `400` immediately with a single details entry |
| 2 – Body/schema | The request body must be valid JSON and must deserialise to `SubmissionBatchPayload` | **Fail fast** – returns `400` immediately with a single details entry |
| 3 – Payload constraints | All business rules that apply to a structurally valid payload (e.g. non-empty `transfers`, unique `recordId`s) | **Fail fast** – constraints are mutually exclusive; the first failing constraint is returned in one `400` |

All `400` responses use the shape:

```json
{
  "error": "invalid transfer data",
  "details": [
    { "message": "<human-readable reason>" }
  ]
}
```

Phase 2 schema errors follow the same `details` array structure but use the raw `JsError` object produced by Play-JSON instead of a `message` key.

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

The host and port default to `localhost` and `30038` as set in `application.conf`. Like any
Play config key, they can be overridden at runtime via environment variables that follow the
standard Play convention (uppercase, dots and hyphens replaced by underscores):

- `MICROSERVICE_SERVICES_ETMP_TRANSACTION_HOST`
- `MICROSERVICE_SERVICES_ETMP_TRANSACTION_PORT`

Note: there is no code-level fallback — `AppConfig` reads these keys with `config.get`, so they
must be present (either from `application.conf` or an override). Removing them without a
replacement will cause the application to fail to start.

### Local stub wiring check

You can verify this service is targeting `stamp-taxes-on-shares-stubs` with:

1. Ensure the stub service is running (for example via service-manager profile).
2. Check the env vars are present in the shell used to run this service:

```bash
echo "$MICROSERVICE_SERVICES_ETMP_TRANSACTION_HOST"
echo "$MICROSERVICE_SERVICES_ETMP_TRANSACTION_PORT"
```

3. Start this service and send a submission request.
4. Confirm outbound calls are being made to the resolved host/port:

```bash
grep -E "RESTAdapter/stc/transaction" logs/securities-transfer-charge-submissions.log | tail -n 20
```

If those env vars are not set, the client uses `localhost:30038`.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
