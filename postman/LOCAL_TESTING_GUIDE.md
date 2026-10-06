# Local Testing Guide - NRS Integration with nrs-orchestrator

This guide provides step-by-step instructions for testing Securities Transfer Charge NRS implementation locally using **nrs-orchestrator**.

## Step 1: Start Required Services

Open a terminal and start all NRS orchestrator dependencies:

```bash
sm2 --start NRS_ORCHESTRATOR_ALL
```

## Step 2: Stop NRS_ORCHESTRATOR (We'll restart with custom config)

```bash
sm2 --stop NRS_ORCHESTRATOR
```

## Step 3: Configure NRS_ORCHESTRATOR for Securities Transfer Charge

Restart NRS_ORCHESTRATOR with the service configuration:

```bash
sm2 --start NRS_ORCHESTRATOR -appendArgs '{
"NRS_ORCHESTRATOR": [
"-J-Dnonrep-submission.clients.securities-transfer-charge.apiKeySha256=334ad56b6df663c2909f6563f6955d070b68498d2307338f7de3e22303344ca7",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.test.1=key1",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.stc-single-submission.1=nino",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.stc-single-submission.2=transferDate",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.stc-bulk-submission.1=nino",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.stc-bulk-submission.2=submissionDate",
"-J-Dnonrep-submission.clients.securities-transfer-charge.attachmentsAllowed=true"
] }'
```

## Step 4: Start Service

In a **new terminal window**, navigate to the project and start the service:

```bash
cd /path/to/securities-transfer-charge-submissions
sbt run
```

Service should start on `http://localhost:30034`

## Step 5: Verify Services Are Running

Check that all services are up:

```bash
# Check NRS Orchestrator
curl http://localhost:11311/ping/ping

# Check the service
curl http://localhost:30034/ping/ping

# Check Service Manager status
sm2 --status NRS_ORCHESTRATOR_ALL
```

All should return successful responses.

---

## Postman Setup

### Step 1: Import Collections

1. Open Postman
2. Click **Import**
3. Import the file:
    - `NRS_Securities_Transfer_Charge_Submissions.postman_collection.json`

### Step 2: Create Environment

1. In Postman, click **Environments** (left sidebar)
2. Click **Create Environment** (or **+** button)
3. Name it: **"Local NRS Testing"**
4. Add these variables:

| Variable | Initial Value | Current Value |
|----------|---------------|---------------|
| `nrs_orchestrator_url` | `http://localhost:11311` | `http://localhost:11311` |
| `base_url` | `http://localhost:30034` | `http://localhost:30034` |
| `nrs_api_key` | `nrs-local-dummy` | `nrs-local-dummy` |
| `auth_token` | `Bearer test-token-12345` | `Bearer test-token-12345` |
| `submission_id` | *(leave empty - auto-populated)* | *(leave empty)* |

5. Click **Save**
6. Select this environment from the dropdown (top right)

---

## Checksum Calculation

Use the provided `calculate_checksums.sh` script to calculate SHA-256 checksums:

```bash
# Calculate checksums for HTML payloads
./calculate_checksums.sh

# Calculate checksum for Excel file
./calculate_checksums.sh /path/to/bulk-transfers.xlsx
```

---

## Create a file in Upscan

To simplify this process the [hello-world-upscan](https://github.com/hmrc/hello-world-upscan) service allows to selection of a file and upload to upscan.

### Step 1: Start the hello-world-upscan service locally

First, download and run the `hello-world-upscan` service from https://github.com/hmrc/hello-world-upscan:

```shell
sm2 --start UPSCAN_STUB OBJECT_STORE_STUB INTERNAL_AUTH
sbt run
```

The service will be available at http://localhost:9001/hello-world-upscan/hello-world

### Step 2: Upload a sample file to Upscan

1) Create a test file (e.g., `attach.txt`) with some content:
```shell
echo "Sample attachment content" > attach.txt
```

2) Navigate to http://localhost:9001/hello-world-upscan/hello-world in your browser

3) Select and upload your test file (`attach.txt`)

### Step 3: Locate the download URL from logs

After uploading, check the `hello-world-upscan` service logs for the message **"Received callback notification"**. 

This log entry will contain:
- `reference`: The file reference ID
- `downloadUrl`: The URL to download the file (starts with `http://localhost:9570/upscan/download/`)

Example download URL: `http://localhost:9570/upscan/download/8e5f6948-4dcf-40b1-9e86-664b230cd947`

### Step 4: Generate the SHA256 checksum

```shell
shasum -a 256 attach.txt
```

---

## 🔍 Monitoring and Debugging

### View NRS Orchestrator Logs

```bash
# Tail orchestrator logs
sm2 --logs NRS_ORCHESTRATOR

# Or view in real-time
tail -f ~/.sm2/logs/NRS_ORCHESTRATOR.log
```
