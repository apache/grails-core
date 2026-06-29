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

# ./releaseDistributions.sh <tag> <username> <svn_folder>

set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <tag> <svn_folder> <username>" >&2
  exit 1
fi

RELEASE_TAG="$1"
RELEASE_VERSION="${RELEASE_TAG#v}"
SVN_FOLDER="$2"
SVN_USER="$3"
RELEASE_ROOT="https://dist.apache.org/repos/dist/release/grails/${SVN_FOLDER}"
DEV_ROOT="https://dist.apache.org/repos/dist/dev/grails/${SVN_FOLDER}"

read -r -s -p "Password: " SVN_PASS
echo

if [[ -z "${RELEASE_TAG}" ]]; then
  echo "❌ ERROR: Release Tag must not be empty." >&2
  exit 1
fi
if [[ -z "${SVN_FOLDER}" ]]; then
  echo "❌ ERROR: SVN folder name must not be empty." >&2
  exit 1
fi
if [[ -z "${SVN_USER}" ]]; then
  echo "❌ ERROR: Username must not be empty." >&2
  exit 1
fi
if [[ -z "${SVN_PASS}" ]]; then
  echo "❌ ERROR: Password must not be empty." >&2
  exit 1
fi

svn_flags=(--non-interactive --trust-server-cert --username "${SVN_USER}" --password "${SVN_PASS}")

svn_exists() {
  local url="$1"
  svn ls "${svn_flags[@]}" --depth=empty "${url}" >/dev/null 2>&1
}

old_release_folder="$(svn ls "${svn_flags[@]}" "${RELEASE_ROOT}" | awk -F/ 'NF{print $1; exit}')"
if [[ -n "${old_release_folder}" ]]; then
  PRIOR_RELEASE_URL="${RELEASE_ROOT}/${old_release_folder}"
  echo "🗑️ Deleting old release folder: ${PRIOR_RELEASE_URL}"
  svn rm "${svn_flags[@]}" -m "Remove previous release ${old_release_folder}" "${PRIOR_RELEASE_URL}"
  echo "✅ Deleted old release folder"
else
  echo "ℹ️ No existing release subfolder found under ${RELEASE_ROOT}"
fi

DEV_VERSION_URL="$DEV_ROOT/${RELEASE_VERSION}"
RELEASE_VERSION_URL="$RELEASE_ROOT/${RELEASE_VERSION}"

if ! svn_exists "${DEV_VERSION_URL}"; then
  echo "❌ ERROR: dev folder for ${RELEASE_VERSION} does not exist at: ${DEV_VERSION_URL}" >&2
  exit 2
fi

if svn_exists "${RELEASE_VERSION_URL}"; then
  echo "❌ ERROR: release folder for ${RELEASE_VERSION} already exists at: ${RELEASE_VERSION_URL}" >&2
  exit 3
fi

echo "🚀 Promoting ${DEV_VERSION_URL} -> ${RELEASE_VERSION_URL}"
svn mv "${svn_flags[@]}" -m "Promote Apache Grails ${RELEASE_VERSION} from dev to release" "${DEV_VERSION_URL}" "${RELEASE_VERSION_URL}"
echo "✅ Promoted"
