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

# ./releaseJarFiles.sh <staging repo description> <username> <password>

set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <staging repo description> <username> <password>" >&2
  exit 1
fi

NEXUS_URL="https://repository.apache.org"
STAGING_DESCRIPTION="$1"
NEXUS_USER="$2"
NEXUS_PASS="$3"

if [[ -z "${STAGING_DESCRIPTION}" ]]; then
  echo "ERROR: Staging Description must not be empty." >&2
  exit 1
fi
if [[ -z "${NEXUS_USER}" ]]; then
  echo "ERROR: Username must not be empty." >&2
  exit 1
fi
if [[ -z "${NEXUS_PASS}" ]]; then
  echo "ERROR: Password must not be empty." >&2
  exit 1
fi

nexusApi() {
  local request_method="$1"; shift
  local path="$1"; shift
  curl -fsS -u "${NEXUS_USER}:${NEXUS_PASS}" \
    -H 'Accept: application/json' \
    -H 'Content-Type: application/json' \
    -X "${request_method}" "${NEXUS_URL}/service/local/${path}" "$@"
}

wait_for_promotion() {
  local repoId="$1"
  local timeout_s="${2:-600}"   # default 10 minutes
  local interval_s="${3:-3}"
  local started
  started="$(date +%s)"

  echo "Waiting for release promotion to complete (timeout ${timeout_s}s)…"

  while :; do
    # 1) If any ERROR appears in activity, fail fast
    act="$(nexusApi GET "/staging/repository/${repoId}/activity" || true)"
    if [[ -n "$act" ]]; then
      err_count="$(jq -r '[.. | objects? | select(has("severity")) | select(.severity=="ERROR")] | length' <<<"$act" 2>/dev/null || echo 0)"
      if [[ "$err_count" != "0" ]]; then
        echo "ERROR: Staging activity contains failure(s). Aborting." >&2
        # Optionally dump recent relevant lines:
        jq -r '.. | objects? | select(has("severity")) | "\(.severity): \(.name // "event") - \(.message // "")"' <<<"$act" || true
        return 1
      fi
    fi

    # 2) Check transitioning flag — when false after promote, action is done
    trans="$(nexusApi GET '/staging/profile_repositories' \
      | jq -r --arg r "$repoId" '.data[]? | select(.repositoryId==$r) | .transitioning' 2>/dev/null || echo "true")"

    if [[ "$trans" == "false" ]]; then
      # sanity: make sure we actually saw some "release/promote" activity; otherwise keep waiting a bit
      if [[ -n "$act" ]]; then
        # did we see any promote/release-ish step?
        saw_promote="$(jq -r '
          [ .. | objects? | .name? // empty | ascii_downcase
            | select(test("release|promote|finish")) ] | length' <<<"$act" 2>/dev/null || echo 0)"
        if [[ "$saw_promote" -gt 0 ]]; then
          echo "Promotion appears complete."
          return 0
        fi
      fi
    fi

    # timeout?
    now="$(date +%s)"
    if (( now - started >= timeout_s )); then
      echo "ERROR: Timed out waiting for promotion to complete." >&2
      # Show a short summary to aid debugging
      if [[ -n "$act" ]]; then
        echo "--- Recent activity snapshot ---"
        jq -r '.. | objects? | select(has("severity") or has("name")) | "\(.severity // "")\t\(.name // "")\t\(.message // "")"' <<<"$act" | tail -n 20 || true
      fi
      return 1
    fi

    sleep "$interval_s"
  done
}

repos_json="$(nexusApi GET '/staging/profile_repositories')"
repoId="$(jq -r --arg d "${STAGING_DESCRIPTION}" '.data[] | select(.description==$d) | .repositoryId' <<<"${repos_json}")"
profileId="$(jq -r --arg d "${STAGING_DESCRIPTION}" '.data[] | select(.description==$d) | .profileId' <<<"${repos_json}")"
state="$(jq -r --arg d "${STAGING_DESCRIPTION}" '.data[] | select(.description==$d) | .type' <<<"${repos_json}")"

if [[ -z "${repoId}" || -z "${profileId}" ]]; then
  echo "ERROR: No staged repository found with description: ${STAGING_DESCRIPTION}" >&2
  exit 2
fi
echo "Found staged repo: ${repoId} (profile: ${profileId}, state: ${state})"
if [[ "${state}" == "open" ]]; then
  echo "ERROR: Staged Repo is not closed: ${STAGING_DESCRIPTION}" >&2
  exit 3
fi

if [[ "${state}" == "closed" ]]; then
  echo "Promoting (release) ${repoId}…"
  nexusApi POST "/staging/profiles/${profileId}/promote" \
  --data "$(jq -n --arg r "${repoId}" --arg d "${STAGING_DESCRIPTION}" '{data:{stagedRepositoryId:$r,description:$d}}')"
fi

wait_for_promotion "$repoId" 600 3

echo "Dropping staging repository ${repoId}…"
nexusApi POST "/staging/profiles/${profileId}/drop" \
  --data "$(jq -n --arg r "$repoId" --arg d "${STAGING_DESCRIPTION}" '{data:{stagedRepositoryId:$r,description:$d}}')"

echo "Done. Released ${repoId}."