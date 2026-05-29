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

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
CWD=$(pwd)
VERSION=${RELEASE_TAG#v}
export PREFERRED_GRAILS_VERSION=${VERSION}

cleanup() {
  echo "❌ Verification failed. ❌"
}
trap cleanup ERR

echo "Verifying KEYS file ..."
"${SCRIPT_DIR}/verify-keys.sh" "${DOWNLOAD_LOCATION}"
echo "✅ KEYS Verified"

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
"${SCRIPT_DIR}/verify-jar-artifacts.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
echo "✅ JAR Artifacts Verified"

echo "Using Java at ..."
which java
java -version

echo "Determining Gradle on PATH ..."
if GRADLE_CMD="$(command -v gradlew 2>/dev/null)"; then
    :   # found the wrapper on PATH
elif GRADLE_CMD="$(command -v gradle 2>/dev/null)"; then
    :   # fall back to system-wide Gradle
else
    echo "❌ ERROR: Neither gradlew nor gradle found on \$PATH." >&2
    exit 1
fi
echo "✅ Using Gradle command: ${GRADLE_CMD}"

echo "Bootstrap Gradle ..."
cd "${DOWNLOAD_LOCATION}/grails/gradle-bootstrap"
${GRADLE_CMD}
echo "✅ Gradle Bootstrapped"

echo "Applying License Audit ..."
cd "${DOWNLOAD_LOCATION}/grails"
./gradlew rat
echo "✅ RAT passed"

echo "Validating Dependency Versions ..."
cd "${DOWNLOAD_LOCATION}/grails"
./gradlew validateDependencyVersions
echo "✅ Dependency Versions Validated"

echo "Verifying Reproducible Build ..."
set +e # because we have known issues here
"${SCRIPT_DIR}/verify-reproducible.sh" "${DOWNLOAD_LOCATION}"
set -e
echo "✅ Reproducible Build Verified"

echo "Manual verification steps:"
echo
echo "☑️ 1 | Verify that the generated applications start correctly"
echo "     1.1 | Wrapper Shell App:"
echo "           cd ${DOWNLOAD_LOCATION}/apache-grails-wrapper-${VERSION}-bin/ShellApp && ./gradlew bootRun"
echo "     1.2 | Wrapper Forge App:"
echo "           cd ${DOWNLOAD_LOCATION}/apache-grails-wrapper-${VERSION}-bin/ForgeApp && ./gradlew bootRun"
echo "     1.3 | CLI Shell App:"
echo "           cd ${DOWNLOAD_LOCATION}/apache-grails-${VERSION}-bin/bin/ShellApp && ./gradlew bootRun"
echo "     1.4 | CLI Forge App:"
echo "           cd ${DOWNLOAD_LOCATION}/apache-grails-${VERSION}-bin/bin/ForgeApp && ./gradlew bootRun"
echo
echo "☑️ 2 | Verify Grails command resolution"
echo "     2.1 | Run './grailsw help' inside any of the app directories above."
echo "     2.2 | Confirm that scaffolding commands (e.g. 'generate-*') are listed."
echo "           This verifies that dynamic command resolution is working correctly."
echo
echo "✅✅✅ Automatic verification finished. See above instructions for remaining manual testing."
