#!/usr/bin/env bash
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
set -euo pipefail

RELEASE_TAG=$1
DOWNLOAD_LOCATION="${2:-downloads}"
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [ -z "${RELEASE_TAG}" ]; then
  echo "Usage: $0 [release-tag] <optional download location>"
  exit 1
fi

VERSION=${RELEASE_TAG#v}
DOCS_NAME="apache-grails-${VERSION}-docs"

cd "${DOWNLOAD_LOCATION}"
if [ ! -f "${DOCS_NAME}.zip" ]; then
  echo "Error: Could not find ${DOCS_NAME}.zip in ${DOWNLOAD_LOCATION}"
  exit 1
fi

export GRAILS_GPG_HOME=$(mktemp -d)
cleanup() {
  rm -rf "${GRAILS_GPG_HOME}"
}
trap cleanup EXIT

error() {
  echo "❌ Documentation Verification failed ❌"
  exit 1
}
trap error ERR

echo "Verifying checksum..."
shasum -a 512 -c "${DOCS_NAME}.zip.sha512"
echo "✅ Checksum Verified"

echo "Importing GPG key to independent GPG home ..."
gpg --homedir "${GRAILS_GPG_HOME}" --import "${SCRIPT_DIR}/../../KEYS"
echo "✅ GPG Key Imported"

echo "Verifying GPG signature..."
gpg --homedir "${GRAILS_GPG_HOME}" --verify "${DOCS_NAME}.zip.asc" "${DOCS_NAME}.zip"
echo "✅ GPG Verified"

rm -rf "${DOCS_NAME}" || true
echo "Extracting zip file..."
unzip -q "${DOCS_NAME}.zip"

if [ ! -d "${DOCS_NAME}" ]; then
  echo "Error: Expected extracted folder '${DOCS_NAME}' not found."
  exit 1
fi

echo "Checking for required files existence..."
REQUIRED_FILES=("LICENSE" "NOTICE" "html/index.html" "html/guide/single.html" "html/api/index.html" "html/grails-data/index.html")

for FILE in "${REQUIRED_FILES[@]}"; do
  if [ ! -f "${DOCS_NAME}/${FILE}" ]; then
    echo "❌ Missing required file: ${FILE}"
    exit 1
  fi

  echo "✅ Found required file: ${FILE}"
done

# Every license file that LICENSE or NOTICE points to ("See licenses/... for the full license
# terms.") must ship in the distribution at that path, relative to the distribution root.
echo "Checking for referenced license files existence..."
MISSING_REFERENCED_LICENSES=()
for META_FILE in LICENSE NOTICE; do
  while IFS= read -r REFERENCED_LICENSE; do
    [ -z "${REFERENCED_LICENSE}" ] && continue
    if [ ! -f "${DOCS_NAME}/${REFERENCED_LICENSE}" ]; then
      MISSING_REFERENCED_LICENSES+=("${META_FILE} references '${REFERENCED_LICENSE}' but ${REFERENCED_LICENSE} does not exist")
    else
      echo "✅ Found referenced license file: ${REFERENCED_LICENSE} (from ${META_FILE})"
    fi
  done < <(grep -oE 'See [^[:space:]]+ for the full license terms' "${DOCS_NAME}/${META_FILE}" | awk '{print $2}' || true)
done

if ((${#MISSING_REFERENCED_LICENSES[@]})); then
  printf '❌ %s\n' "${MISSING_REFERENCED_LICENSES[@]}"
  exit 1
fi

echo "✅✅✅ All documentation distribution checks passed successfully for Apache Grails ${VERSION}."
