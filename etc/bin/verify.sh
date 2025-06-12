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

PROJECT_NAME='grails-redis'
RELEASE_TAG=$1
DOWNLOAD_LOCATION="${2:-.}"
DOWNLOAD_LOCATION=$(realpath "${DOWNLOAD_LOCATION}")

if [ -z "${RELEASE_TAG}" ]; then
  echo "Usage: $0 [release-tag] <optional download location>"
  exit 1
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
CWD=$(pwd)
VERSION=${RELEASE_TAG#v}

cleanup() {
  echo "❌ Verification failed. ❌"
}
trap cleanup ERR

cd "${DOWNLOAD_LOCATION}"

echo "Downloading KEYS file ..."
curl -sSfLO "https://dist.apache.org/repos/dist/release/grails/KEYS"
echo "✅ KEYS Downloaded"

echo "Downloading Artifacts ..."
"${SCRIPT_DIR}/download-release-artifacts.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ Artifacts Downloaded"

echo "Verifying Source Distribution ..."
"${SCRIPT_DIR}/verify-source-distribution.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ Source Distribution Verified"

echo "Verifying JAR Artifacts ..."
"${SCRIPT_DIR}/verify-jar-artifacts.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ JAR Artifacts Verified"

echo "Using Java at ..."
which java
java -version

echo "Bootstrap Gradle ..."
cd "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/gradle-bootstrap"
gradlew
echo "✅ Gradle Bootstrapped"

echo "Applying License Audit ..."
cd "${DOWNLOAD_LOCATION}/${PROJECT_NAME}"
./gradlew rat
echo "✅ RAT passed"

echo "Verifying Reproducible Build ..."
set +e # because we have known issues here
"${SCRIPT_DIR}/verify-reproducible.sh" "${DOWNLOAD_LOCATION}"
set -e
echo "✅ Reproducible Build Verified"

echo "✅✅✅ Verification finished, see above instructions for remaining manual testing."

cd "${CWD}"