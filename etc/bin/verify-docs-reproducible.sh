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

# Rebuilds the documentation distribution from the extracted source distribution and compares
# it with the published one. Run it after verify-source-distribution.sh has extracted the
# source and Gradle has been bootstrapped, as verify.sh does.
set -euo pipefail

RELEASE_TAG=$1
DOWNLOAD_LOCATION="${2:-downloads}"
DOWNLOAD_LOCATION=$(realpath "${DOWNLOAD_LOCATION}")

if [ -z "${RELEASE_TAG}" ]; then
  echo "Usage: $0 [release-tag] <optional download location>"
  exit 1
fi

VERSION=${RELEASE_TAG#v}
DOCS_ZIP="apache-grails-${VERSION}-docs.zip"
SOURCE_DIR="${DOWNLOAD_LOCATION}/grails"
RESULTS_DIR="${SOURCE_DIR}/etc/bin/results/docs"

cleanup() {
  echo "❌ Documentation reproducibility verification failed. ❌"
}
trap cleanup ERR

if [ ! -f "${DOWNLOAD_LOCATION}/${DOCS_ZIP}" ]; then
  echo "❌ ${DOCS_ZIP} not found in ${DOWNLOAD_LOCATION}."
  exit 1
fi

# BUILD_DATE pins the build date and GIT_TAGS pins the documentation's version dropdown,
# since the source distribution has no git history to read them from
for FILE in BUILD_DATE GIT_TAGS; do
  if [ ! -f "${SOURCE_DIR}/${FILE}" ]; then
    echo "❌ File '${FILE}' not found. Grails Source Distributions should have a ${FILE} file at the root..."
    exit 1
  fi
  echo "✅ File '${FILE}' exists."
done
export SOURCE_DATE_EPOCH=$(cat "${SOURCE_DIR}/BUILD_DATE")

echo "Building the documentation distribution from source ..."
cd "${SOURCE_DIR}"
./gradlew :grails-doc:dist --no-build-cache --no-daemon

BUILT_ZIP="${SOURCE_DIR}/grails-doc/build/distributions/${DOCS_ZIP}"
if cmp -s "${DOWNLOAD_LOCATION}/${DOCS_ZIP}" "${BUILT_ZIP}"; then
  echo "✅ The published ${DOCS_ZIP} is identical to the one built from source. ✅"
  exit 0
fi

echo "Differences were found, comparing the archive contents ..."
rm -rf "${RESULTS_DIR}"
mkdir -p "${RESULTS_DIR}/published" "${RESULTS_DIR}/built"
unzip -q "${DOWNLOAD_LOCATION}/${DOCS_ZIP}" -d "${RESULTS_DIR}/published"
unzip -q "${BUILT_ZIP}" -d "${RESULTS_DIR}/built"

cd "${RESULTS_DIR}"
diff -r -q published built > diff.txt || true
if [ -s diff.txt ]; then
  echo "❌ Differing files (full list in ${RESULTS_DIR}/diff.txt):"
  head -n 50 diff.txt
else
  echo "❌ The extracted files are identical, so the archives differ only in their zip metadata (entry order, timestamps or permissions)."
fi
exit 1
