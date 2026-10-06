#!/bin/bash
# Helper script to calculate SHA-256 checksums for NRS payloads

echo "=== NRS Payload Checksum Calculator ==="
echo ""

# Single submission payload (HTML)
SINGLE_PAYLOAD="<html><body><h1>Securities Transfer Charge</h1><p>NINO: AB123456C</p><p>Transfer Date: 2024-01-15</p></body></html>"
SINGLE_BASE64=$(echo -n "$SINGLE_PAYLOAD" | base64)
SINGLE_CHECKSUM=$(echo -n "$SINGLE_PAYLOAD" | shasum -a 256 | cut -d' ' -f1)

# Reverse
SINGLE_PAYLOAD_FROM_BASE64=$(echo "$SINGLE_BASE64" | base64 -d)
SINGLE_CHECKSUM_VERIFY=$(echo -n "$SINGLE_PAYLOAD_FROM_BASE64" | shasum -a 256 | cut -d' ' -f1)

echo "1. SINGLE SUBMISSION (HTML)"
echo "   Payload:                 $SINGLE_PAYLOAD"
echo "   Base64:                  $SINGLE_BASE64"
echo "   Base64 to Payload:       $SINGLE_PAYLOAD_FROM_BASE64"
echo "   SHA-256:                 $SINGLE_CHECKSUM"
echo "   SHA-256 to Payload:      $SINGLE_PAYLOAD_FROM_BASE64"
echo ""

# Bulk submission payload (HTML)
# For bulk: /submission endpoint receives HTML payload and returns submissionId
# Then /attachment endpoint uploads the .xlsx file
BULK_PAYLOAD="<html><body><h1>Bulk Securities Transfer Charge Submission</h1><p>NINO: AB123456C</p><p>Submission Date: 2024-01-15</p></body></html>"
BULK_BASE64=$(echo -n "$BULK_PAYLOAD" | base64)
BULK_CHECKSUM=$(echo -n "$BULK_PAYLOAD" | shasum -a 256 | cut -d' ' -f1)

# Reverse
BULK_PAYLOAD_FROM_BASE64=$(echo "$BULK_BASE64" | base64 -d)
BULK_CHECKSUM_VERIFY=$(echo -n "$BULK_PAYLOAD_FROM_BASE64" | shasum -a 256 | cut -d' ' -f1)

echo "2. BULK SUBMISSION (HTML)"
echo "   Payload:                 $BULK_PAYLOAD"
echo "   Base64:                  $BULK_BASE64"
echo "   Base64 to Payload:       $BULK_PAYLOAD_FROM_BASE64"
echo "   SHA-256:                 $BULK_CHECKSUM"
echo "   SHA-256 to Payload:      $BULK_PAYLOAD_FROM_BASE64"
echo ""

# Excel file checksum (for bulk attachment)
if [ -n "$1" ]; then
    echo "3. EXCEL FILE ATTACHMENT (for bulk /attachment endpoint)"
    FILE_CHECKSUM=$(shasum -a 256 "$1" | cut -d' ' -f1)
    echo "   File:                    $1"
    echo "   SHA-256:                 $FILE_CHECKSUM"
    echo ""
fi

echo "=== Flow Summary ==="
echo "SINGLE: /submission (HTML) -> NRS (no attachmentIds)"
echo "BULK:   /submission (HTML) -> get submissionId -> /attachment (Excel) -> NRS (1 attachmentId)"
echo ""
echo "Usage: ./calculate_checksums.sh [path/to/file.xlsx]"