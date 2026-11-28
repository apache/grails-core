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
export PREFERRED_GRAILS_VERSION=${VERSION}

cd $DOWNLOAD_LOCATION
ZIP_FILE=$(ls "apache-grails-wrapper-${VERSION}-bin.zip" 2>/dev/null | head -n 1)

if [ -z "$ZIP_FILE" ]; then
  echo "Error: Could not find apache-grails-wrapper-${VERSION}-bin.zip in $DOWNLOAD_LOCATION"
  exit 1
fi

export GRAILS_GPG_HOME=$(mktemp -d)
cleanup() {
  rm -rf "${GRAILS_GPG_HOME}"
}
trap cleanup EXIT
error() {
  echo "❌ Wrapper Verification failed ❌"
  exit 1
}
trap cleanup ERR

echo "Verifying checksum..."
shasum -a 512 -c "apache-grails-wrapper-${VERSION}-bin.zip.sha512"
echo "✅ Checksum Verified"

echo "Importing GPG key to independent GPG home ..."
gpg --homedir "${GRAILS_GPG_HOME}" --import "${SCRIPT_DIR}/../../KEYS"
echo "✅ GPG Key Imported"

echo "Verifying GPG signature..."
gpg --homedir "${GRAILS_GPG_HOME}" --verify "apache-grails-wrapper-${VERSION}-bin.zip.asc" "apache-grails-wrapper-${VERSION}-bin.zip"
echo "✅ GPG Verified"

SRC_DIR="apache-grails-wrapper-${VERSION}-bin"
rm -rf "${SRC_DIR}" || true
echo "Extracting zip file..."
unzip -q "apache-grails-wrapper-${VERSION}-bin.zip"

if [ ! -d "${SRC_DIR}" ]; then
  echo "Error: Expected extracted folder '${SRC_DIR}' not found."
  exit 1
fi

echo "Checking for required files existence..."
REQUIRED_FILES=("LICENSE" "NOTICE")

for FILE in "${REQUIRED_FILES[@]}"; do
  if [ ! -f "${SRC_DIR}/$FILE" ]; then
    echo "❌ Missing required file: $FILE"
    exit 1
  fi

  echo "✅ Found required file: $FILE"
done

export GRAILS_REPO_URL=https://repository.apache.org/content/groups/staging
cd "${SRC_DIR}"
echo "Checking that the wrapper downloads and starts the grails command ..."
./grailsw --version
./grailsw help > /dev/null
echo "✅ The wrapper starts the grails command"

echo "Checking that the generated application has this wrapper ..."
./grailsw create-app -x -g mongodb -f gradle-settings-file ForgeApp
echo "✅ Generated Forge App"

for WRAPPER_FILE in grailsw grailsw.bat grails-wrapper.jar; do
  if ! cmp -s "${WRAPPER_FILE}" "ForgeApp/${WRAPPER_FILE}"; then
    echo "❌ ${WRAPPER_FILE} of the generated application is not the one of this distribution"
    exit 1
  fi
done
echo "✅ The generated application has the wrapper of this distribution"

echo "Checking the wrapper of the generated application ..."
(cd ForgeApp && ./grailsw --version)
echo "✅ The wrapper of the generated application starts the grails command"

echo "Verifying Forge App dependencies resolve ..."
DEP_OUTPUT=$(cd ForgeApp && ./gradlew dependencies 2>&1) || { echo "❌ Forge App dependency resolution failed"; exit 1; }
if echo "$DEP_OUTPUT" | grep -q 'FAILED'; then
  echo "❌ Forge App has unresolvable dependencies:"
  echo "$DEP_OUTPUT" | grep 'FAILED'
  exit 1
fi
echo "✅ Forge App dependencies resolved"

echo "✅✅✅ All wrapper binary distribution checks passed successfully for Apache Grails ${VERSION}."