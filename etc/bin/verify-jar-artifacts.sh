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
DOWNLOAD_LOCATION=$(realpath "${DOWNLOAD_LOCATION}")

if [ -z "${RELEASE_TAG}" ]; then
  echo "Usage: $0 [release-tag] <optional download location>"
  exit 1
fi

VERSION=${RELEASE_TAG#v}

ARTIFACTS_FILE="${DOWNLOAD_LOCATION}/grails/PUBLISHED_ARTIFACTS"
CHECKSUMS_FILE="${DOWNLOAD_LOCATION}/grails/CHECKSUMS"
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [ ! -f "${ARTIFACTS_FILE}" ]; then
  echo "Required file ${ARTIFACTS_FILE} not found."
  exit 1
fi

if [ ! -f "${CHECKSUMS_FILE}" ]; then
  echo "Required file ${CHECKSUMS_FILE} not found."
  exit 1
fi

export GRAILS_GPG_HOME=$(mktemp -d)
cleanup() {
  rm -rf "${GRAILS_GPG_HOME}"
}
trap cleanup EXIT
error() {
  echo "❌ JAR Verification failed ❌"
}
trap error ERR

echo "Importing GPG key to independent GPG home ..."
gpg --homedir "${GRAILS_GPG_HOME}" --import "${SCRIPT_DIR}/../../KEYS"
echo "✅ GPG Key Imported"

REPO_BASE_URL="https://repository.apache.org/content/groups/staging"

# switch to the extracted Grails source directory
cd grails

# Create a temporary directory to work in
WORK_DIR='etc/bin/results/first'
mkdir -p $WORK_DIR
echo "Using temp dir: $WORK_DIR"
cd "$WORK_DIR"

# Read each line from ARTIFACTS_FILE
while IFS= read -r line; do
  JAR_FILE=$(echo "${line}" | awk '{print $1}')
  [[ "${JAR_FILE}" != *.jar ]] && continue
  
  COORDINATES=$(echo "${line}" | awk '{print $2}')

  GROUP_ID=$(echo "${COORDINATES}" | cut -d: -f1 | tr '.' '/')
  ARTIFACT_ID=$(echo "${COORDINATES}" | cut -d: -f2)
  VERSION=$(echo "${COORDINATES}" | cut -d: -f3)
  CLASSIFIER=$(echo "${COORDINATES}" | cut -d: -f4-)

  if [[ -n "${CLASSIFIER}" ]]; then
    FILE_NAME="${ARTIFACT_ID}-${VERSION}-${CLASSIFIER}.jar"
  else
    FILE_NAME="${ARTIFACT_ID}-${VERSION}.jar"
  fi

  JAR_URL="${REPO_BASE_URL}/${GROUP_ID}/${ARTIFACT_ID}/${VERSION}/${FILE_NAME}"
  ASC_URL="${JAR_URL}.asc"

  echo "🔎 Checking artifact: ${FILE_NAME} as ${JAR_FILE}"
  if [ ! -f "${JAR_FILE}" ]; then
    echo "... Downloading: ${JAR_URL} to ${JAR_FILE}"
    curl -sSfL "${JAR_URL}" -o ${JAR_FILE}
  else
    echo "... Skipping download, already exists: ${JAR_FILE}"
  fi

 if [ ! -f "${FILE_NAME}.asc" ]; then
    echo "... Downloading signature: ${ASC_URL}"
     curl -sSfLO "${ASC_URL}"
  else
    echo "... Skipping download, already exists: ${FILE_NAME}.asc"
  fi

  echo "... Verifying GPG signature..."
  gpg --homedir "${GRAILS_GPG_HOME}" --verify "${FILE_NAME}.asc" "${JAR_FILE}"
  echo "✅ Verified GPG signature for ${JAR_FILE}"

  EXPECTED_CHECKSUM=$(grep "^${JAR_FILE} " "${CHECKSUMS_FILE}" | awk '{print $2}' || true)
  if [ -z "${EXPECTED_CHECKSUM}" ]; then
    echo "❌ Checksum not found for ${FILE_NAME}"
    exit 1
  fi

  echo "... Verifying checksum..."
  ACTUAL_CHECKSUM=$(shasum -a 512 "${JAR_FILE}" | awk '{print $1}')
  echo "✅ Verified Checksum for ${JAR_FILE}: ${ACTUAL_CHECKSUM}"

  if [ "${ACTUAL_CHECKSUM}" != "${EXPECTED_CHECKSUM}" ]; then
    echo "❌ Checksum mismatch for ${JAR_FILE}"
    echo "Expected: ${EXPECTED_CHECKSUM}"
    echo "Actual:   ${ACTUAL_CHECKSUM}"
    exit 1
  fi

  echo "✅ Verified: ${JAR_FILE}"
done < "${ARTIFACTS_FILE}"

# check to make sure the base profile jar contains the wrapper since it's dynamically generated & copied into position as part of the build
echo "🔍 Locating base profile jar for wrapper inspection..."
cd "${DOWNLOAD_LOCATION}"
mkdir -p "profile-wrapper-check"
cp "${DOWNLOAD_LOCATION}/grails/etc/bin/results/first/grails-profiles-base-${VERSION}.jar" "profile-wrapper-check/base.jar"
echo "✅ Located base profile jar, extracting ..."
unzip "profile-wrapper-check/base.jar" -d "profile-wrapper-check"
echo "✅ Extracted base profile jar ..."

cd "profile-wrapper-check"
if [ ! -s "META-INF/grails-profile/skeleton/grailsw" ]; then
  echo "❌ Missing grailsw in base profile jar"
  exit 1
else
  echo "✅ Found grailsw in base profile jar"
fi

if [ ! -s "META-INF/grails-profile/skeleton/grailsw.bat" ]; then
  echo "❌ Missing grailsw.bat in base profile jar"
  exit 1
else
  echo "✅ Found grailsw.bat in base profile jar"
fi

if [ ! -s "META-INF/grails-profile/skeleton/grails-wrapper.jar" ]; then
  echo "❌ Missing grails-wrapper.jar in base profile jar"
  exit 1
else
  echo "✅ Found grails-wrapper.jar in base profile jar"
fi

echo "✅✅✅ All artifacts verified successfully. ✅✅✅"
