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

# Tracks soft (non-aborting) verification failures so the run can still print the
# remaining steps but exit non-zero at the end (which fails the CI workflow).
VERIFY_FAILED=0

cleanup() {
  echo "❌ Verification failed. ❌"
}
trap cleanup ERR

# Wrap each verification step in a GitHub Actions log group so it is individually
# collapsible in the workflow log. Outside of Actions these emit a plain heading,
# so the script reads the same locally and in the container.
group_start() {
  if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
    echo "::group::$1"
  else
    echo "$1"
  fi
}
group_end() {
  if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
    echo "::endgroup::"
  fi
}

# Fail fast on anything the verification scripts depend on but do not install
# themselves, so a misconfigured environment is reported up front instead of
# halfway through a long run.
preflight() {
  local missing=0
  local tool

  for tool in java gpg curl unzip groovy; do
    if ! command -v "${tool}" > /dev/null 2>&1; then
      echo "❌ Required tool not found on \$PATH: ${tool}"
      missing=1
    fi
  done

  if ! command -v gradlew > /dev/null 2>&1 && ! command -v gradle > /dev/null 2>&1; then
    echo "❌ Neither gradlew nor gradle found on \$PATH."
    missing=1
  fi

  # verify-reproducible.sh recompiles the Grails-Micronaut island under JDK 25,
  # which must be provided out-of-band via JDK_25_HOME (see release.yml
  # JAVA_VERSION_MICRONAUT). Validate it here so the run fails before downloading.
  if [ -z "${JDK_25_HOME:-}" ]; then
    echo "❌ JDK_25_HOME is not set; the reproducible-build check needs a separate Liberica JDK 25 install."
    echo "   Install the JDK matching JAVA_VERSION_MICRONAUT in .github/workflows/release.yml"
    echo "   and export JDK_25_HOME=/path/to/jdk before running this script."
    missing=1
  elif [ ! -x "${JDK_25_HOME}/bin/java" ]; then
    echo "❌ JDK_25_HOME=${JDK_25_HOME} does not contain an executable bin/java."
    missing=1
  else
    local jdk25_version
    jdk25_version=$("${JDK_25_HOME}/bin/java" -version 2>&1 | head -n1)
    if ! printf '%s' "${jdk25_version}" | grep -q 'version "25'; then
      echo "❌ JDK_25_HOME=${JDK_25_HOME} is not a JDK 25 (reports: ${jdk25_version})."
      missing=1
    fi
  fi

  if [ "${missing}" -ne 0 ]; then
    echo "❌ Preflight checks failed. Resolve the issues above before running verification."
    exit 1
  fi
}

group_start "Running preflight checks ..."
preflight
group_end
echo "✅ Preflight checks passed"

group_start "Verifying KEYS file ..."
"${SCRIPT_DIR}/verify-keys.sh" "${DOWNLOAD_LOCATION}"
group_end
echo "✅ KEYS Verified"

group_start "Downloading Artifacts ..."
"${SCRIPT_DIR}/download-release-artifacts.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
group_end
echo "✅ Artifacts Downloaded"

group_start "Verifying Source Distribution ..."
"${SCRIPT_DIR}/verify-source-distribution.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
group_end
echo "✅ Source Distribution Verified"

group_start "Verifying Wrapper Distribution ..."
"${SCRIPT_DIR}/verify-wrapper-distribution.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
group_end
echo "✅ Wrapper Distribution Verified"

group_start "Verifying CLI Distribution ..."
"${SCRIPT_DIR}/verify-cli-distribution.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
group_end
echo "✅ CLI Distribution Verified"

group_start "Verifying JAR Artifacts ..."
"${SCRIPT_DIR}/verify-jar-artifacts.sh" "${RELEASE_TAG}" "${DOWNLOAD_LOCATION}"
group_end
echo "✅ JAR Artifacts Verified"

group_start "Using Java at ..."
which java
java -version
group_end

group_start "Determining Gradle on PATH ..."
if GRADLE_CMD="$(command -v gradlew 2>/dev/null)"; then
    :   # found the wrapper on PATH
elif GRADLE_CMD="$(command -v gradle 2>/dev/null)"; then
    :   # fall back to system-wide Gradle
else
    echo "❌ ERROR: Neither gradlew nor gradle found on \$PATH." >&2
    exit 1
fi
group_end
echo "✅ Using Gradle command: ${GRADLE_CMD}"

group_start "Bootstrap Gradle ..."
cd "${DOWNLOAD_LOCATION}/grails/gradle-bootstrap"
${GRADLE_CMD}
group_end
echo "✅ Gradle Bootstrapped"

group_start "Applying License Audit ..."
cd "${DOWNLOAD_LOCATION}/grails"
./gradlew rat
group_end
echo "✅ RAT passed"

group_start "Validating Dependency Versions ..."
cd "${DOWNLOAD_LOCATION}/grails"
./gradlew validateDependencyVersions
group_end
echo "✅ Dependency Versions Validated"

group_start "Verifying Reproducible Build ..."
# Do not abort here: capture the status so a failure is reported as a tracked
# failure at the end of the run rather than skipping the remaining output.
set +e
"${SCRIPT_DIR}/verify-reproducible.sh" "${DOWNLOAD_LOCATION}"
REPRODUCIBLE_STATUS=$?
set -e
group_end
if [ "${REPRODUCIBLE_STATUS}" -eq 0 ]; then
  echo "✅ Reproducible Build Verified"
else
  echo "❌ Reproducible Build verification failed (exit ${REPRODUCIBLE_STATUS})"
  VERIFY_FAILED=1
fi

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

if [ "${VERIFY_FAILED}" -ne 0 ]; then
  echo "❌❌❌ Automated verification FAILED. See the failed steps above. ❌❌❌"
  exit 1
fi
echo "✅✅✅ Automatic verification finished. See above instructions for remaining manual testing."
