# Local Testing Guide - NRS Integration with nrs-orchestrator

This guide provides step-by-step instructions for testing Securities Transfer Charge NRS implementation locally using **nrs-orchestrator**.

### Step 1: Start Required Services

Open a terminal and start all NRS orchestrator dependencies:

```bash
sm2 --start NRS_ORCHESTRATOR_ALL
```

### Step 2: Stop NRS_ORCHESTRATOR (We'll restart with custom config)

```bash
sm2 --stop NRS_ORCHESTRATOR
```

### Step 3: Configure NRS_ORCHESTRATOR for Securities Transfer Charge

Restart NRS_ORCHESTRATOR with the service configuration:

```bash
sm2 --start NRS_ORCHESTRATOR -appendArgs '{
"NRS_ORCHESTRATOR": [
"-J-Dnonrep-submission.clients.securities-transfer-charge.apiKeySha256=334ad56b6df663c2909f6563f6955d070b68498d2307338f7de3e22303344ca7",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.stc-single-submission.1=nino",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.stc-single-submission.2=transferDate",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.stc-bulk-submission.1=nino",
"-J-Dnonrep-submission.clients.securities-transfer-charge.notableEvents.stc-bulk-submission.2=submissionDate",
"-J-Dnonrep-submission.clients.securities-transfer-charge.attachmentsAllowed=false"
] }'
```

**Configuration Breakdown:**
- `businessId`: `securities-transfer-charge`
- `notableEvents`:
    - `stc-single-submission` (for HTML/UI submissions)
    - `stc-bulk-submission` (for XML bulk submissions)
- `searchKeys`: `nino`, `transferDate`, `submissionDate`
- `X-API-Key`: `nrs-local-dummy` (SHA-256: 334ad56b6df663c2909f6563f6955d070b68498d2307338f7de3e22303344ca7)

### Step 4: Start Service

In a **new terminal window**, navigate to the project and start the service:

```bash
cd /path/to/securities-transfer-charge-submissions
sbt run
```

Service should start on `http://localhost:30034`

### Step 5: Verify Services Are Running

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

## Postman Setup

### Step 1: Import Collections

1. Open Postman
2. Click **Import**
3. Import the files:
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

5. Click **Save**
6. Select this environment from the dropdown (top right)

## 🔍 Monitoring and Debugging

### View NRS Orchestrator Logs

```bash
# Tail orchestrator logs
sm2 --logs NRS_ORCHESTRATOR

# Or view in real-time
tail -f ~/.sm2/logs/NRS_ORCHESTRATOR.log
```
