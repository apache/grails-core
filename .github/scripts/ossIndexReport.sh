#!/bin/bash

#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#

# Renders a Markdown vulnerability report from the log of an `ossIndexAudit --info` run
# and writes it to stdout. Shared by the job summary and the pull request comment so both
# present the scan identically.
#
# Usage: ossIndexReport.sh <scan-log> <scan-outcome> <title> [max-report-bytes]
#
#   scan-log          path to the tee'd `ossIndexAudit` output
#   scan-outcome      `success` when the audit found nothing, anything else otherwise
#   title             heading text for the report
#   max-report-bytes  truncate the vulnerability listing to this many bytes; 0 (default)
#                     leaves it untruncated. Used to stay under GitHub's comment size cap.

set -uo pipefail

SCAN_LOG="${1:?path to the ossIndexAudit log is required}"
SCAN_OUTCOME="${2:?scan outcome is required}"
TITLE="${3:?report title is required}"
MAX_REPORT_BYTES="${4:-0}"

echo "## ${TITLE}"

if [ "$SCAN_OUTCOME" = "success" ]; then
    echo "✅ No vulnerabilities found."
    exit 0
fi

# The audit prints a line per resolved coordinate; hold each one back and print it only when a
# vulnerability follows, so a clean dependency contributes nothing. Report each
# coordinate and each CVE once even though a CVE may be reported against several modules.
REPORT=$(awk '
    BEGIN { in_section=0; in_vuln=0 }
    { gsub(/\033\[[0-9;]*m/, "") }
    /^##\[ossIndexAudit:begin\]/ { in_section=1; next }
    /^##\[ossIndexAudit:end\]/ { in_section=0; in_vuln=0; next }
    !in_section { next }
    /^\[[0-9]+\/[0-9]+\] - pkg:maven\// {
      sub(/^\[[0-9]+\/[0-9]+\] - /, "")
      coord=$0
      next
    }
    /^   Vulnerability Title:/ { in_vuln=1; block=$0 "\n"; cve_id=""; next }
    in_vuln && /^   CVE:/ { match($0,/CVE-[0-9-]+/); if (RSTART) cve_id=substr($0,RSTART,RLENGTH); block=block $0 "\n"; next }
    in_vuln && /^   Reference:/ {
      block=block $0 "\n"
      if (cve_id && !seen_cve[cve_id]++) {
        if (coord != "" && !seen_coord[coord]++) { print ""; print coord }
        printf "%s",block
      }
      in_vuln=0
      next
    }
    in_vuln { block=block $0 "\n" }
' "$SCAN_LOG" 2>/dev/null)

TRUNCATED=''
if [ "$MAX_REPORT_BYTES" -gt 0 ] && [ "$(printf '%s' "$REPORT" | wc -c)" -gt "$MAX_REPORT_BYTES" ]; then
    REPORT=$(printf '%s' "$REPORT" | head -c "$MAX_REPORT_BYTES")
    TRUNCATED='yes'
fi

if [ -z "$REPORT" ]; then
    REPORT='(no scan output captured — check the full log)'
fi

echo "❌ Vulnerabilities detected."
echo
echo '```'
printf '%s\n' "$REPORT"
if [ -n "$TRUNCATED" ]; then
    echo
    echo '… report truncated; see the workflow run for the complete listing.'
fi
echo '```'
