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

# Creates a comment on a pull request, or updates the one a previous run left behind, so a
# workflow that runs repeatedly on the same pull request keeps a single up to date comment
# instead of appending a new one each time. The comment is identified by an HTML marker
# written as its first line.
#
# Usage: postStickyComment.sh <pull-request-number> <marker> <body-file>
#
# Requires the `gh` CLI, a GH_TOKEN with `pull-requests: write`, and GITHUB_REPOSITORY.

set -euo pipefail

PR_NUMBER="${1:?pull request number is required}"
MARKER="${2:?comment marker is required}"
BODY_FILE="${3:?path to the comment body is required}"

REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"

FULL_BODY_FILE=$(mktemp)
trap 'rm -f "$FULL_BODY_FILE"' EXIT
{
    printf '%s\n\n' "$MARKER"
    cat "$BODY_FILE"
} > "$FULL_BODY_FILE"

# Match on the marker rather than on the comment author so a run cannot adopt an unrelated
# comment the same bot left on the pull request.
EXISTING_IDS=$(gh api --paginate "repos/${REPO}/issues/${PR_NUMBER}/comments" \
    --jq "[.[] | select((.body // \"\") | startswith(\"${MARKER}\")) | .id] | .[]")
EXISTING_ID=$(printf '%s\n' "$EXISTING_IDS" | head -n 1)

if [ -n "$EXISTING_ID" ]; then
    echo "Updating existing comment ${EXISTING_ID} on pull request #${PR_NUMBER}"
    jq -n --rawfile body "$FULL_BODY_FILE" '{body: $body}' \
        | gh api -X PATCH "repos/${REPO}/issues/comments/${EXISTING_ID}" --input - --silent
else
    echo "Creating comment on pull request #${PR_NUMBER}"
    jq -n --rawfile body "$FULL_BODY_FILE" '{body: $body}' \
        | gh api -X POST "repos/${REPO}/issues/${PR_NUMBER}/comments" --input - --silent
fi
