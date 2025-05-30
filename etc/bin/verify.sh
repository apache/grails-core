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

STAGING_REPO_ID=$1
RELEASE_TAG=$2
DOWNLOAD_LOCATION="${3:-downloads}"
DOWNLOAD_LOCATION=$(realpath "${DOWNLOAD_LOCATION}")

if [ -z "${STAGING_REPO_ID}" ] || [ -z "${RELEASE_TAG}" ]; then
  echo "Usage: $0 [staging-repo-id] [release-tag] <optional download location>"
  exit 1
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
CWD=$(pwd)

cleanup() {
  echo "❌ Verification failed. ❌"
}
trap cleanup ERR

echo "Downloading Artifacts ..."
"${SCRIPT_DIR}/download-release-artifacts.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ Artifacts Downloaded"

echo "Verifying Source Distribution ..."
"${SCRIPT_DIR}/verify-source-distribution.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ Source Distribution Verified"

echo "Verifying Wrapper Distribution ..."
"${SCRIPT_DIR}/verify-wrapper-distribution.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ Wrapper Distribution Verified"

echo "Verifying CLI Distribution ..."
"${SCRIPT_DIR}/verify-cli-distribution.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ CLI Distribution Verified"

echo "Verifying JAR Artifacts ..."
"${SCRIPT_DIR}/verify-jar-artifacts.sh" "${STAGING_REPO_ID}" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ JAR Artifacts Verified"

echo "Bootstrapping Gradle..."
cd "${DOWNLOAD_LOCATION}/grails"
gradle wrapper
cd grails-gradle
gradle wrapper
cd "${CWD}"

echo "Testing for Reproducible Build ..."
echo " ... Manually"
