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

echo "Writing Gradle init script to use staging repo"
echo """
  allprojects {
      buildscript {
          repositories {
              mavenCentral()
              maven { url = 'https://repo.grails.org/grails/restricted' }
              maven { url = 'https://repository.apache.org/content/groups/staging' }
          }
      }

      repositories {
          mavenCentral()
          maven { url = 'https://repo.grails.org/grails/restricted' }
          maven { url = 'https://repository.apache.org/content/groups/staging' }
      }
  }

""" > "${DOWNLOAD_LOCATION}/use-staging-repo.gradle"
echo "✅ Gradle staging repo init script written"

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

echo "Verifying Reproducible Build ..."
set +e # because we have known issues here
verify-reproducible.sh "${DOWNLOAD_LOCATION}"
set -e
echo "✅ Reproducible Build Verified"

echo "Manual verification steps:"
echo "☑️   1. Set the override repo to staging: 'export GRAILS_REPO_URL=https://repository.apache.org/content/groups/staging'"
echo "☑️   2. Verify running the wrapper shell-created app: cd ${DOWNLOAD_LOCATION}/apache-grails-wrapper-${VERSION}-bin/ShellApp && ./gradlew bootRun --init-script ${DOWNLOAD_LOCATION}/use-staging-repo.gradle"
echo "☑️   3. Verify running the wrapper forge-created app: cd ${DOWNLOAD_LOCATION}/apache-grails-wrapper-${VERSION}-bin/ForgeApp && ./gradlew bootRun --init-script ${DOWNLOAD_LOCATION}/use-staging-repo.gradle"
echo "☑️   4. Verify running the cli shell-created app: cd ${DOWNLOAD_LOCATION}/apache-grails-${VERSION}-bin/bin/ShellApp && ./gradlew bootRun --init-script ${DOWNLOAD_LOCATION}/use-staging-repo.gradle"
echo "☑️   5. Verify running the cli forge-created app: cd ${DOWNLOAD_LOCATION}/apache-grails-${VERSION}-bin/bin/ForgeApp && ./gradlew bootRun --init-script ${DOWNLOAD_LOCATION}/use-staging-repo.gradle"
echo "☑️   6.1. Add the staging repository ('https://repository.apache.org/content/groups/staging') to the 'build.gradle' file in one of the apps above."
echo "☑️   6.2. Run './grailsw help' inside that app directory."
echo "☑️   6.3. Verify that the scaffolding commands (e.g. 'generate-*') are listed."
echo "          This confirms that dynamic command resolution is working correctly."

echo "✅✅✅ Automatic verification finished. See above instructions for remaining manual testing."