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

if [ -z "${RELEASE_TAG}" ]; then
  echo "Usage: $0 [release-tag] <optional download location>"
  exit 1
fi

VERSION=${RELEASE_TAG#v}

cd $DOWNLOAD_LOCATION
ZIP_FILE=$(ls "apache-grails-${VERSION}-incubating-src.zip" 2>/dev/null | head -n 1)

if [ -z "$ZIP_FILE" ]; then
  echo "Error: Could not find apache-grails-${VERSION}-incubating-src.zip in $DOWNLOAD_LOCATION"
  exit 1
fi

echo "Verifying checksum..."
shasum -a 512 -c "apache-grails-${VERSION}-incubating-src.zip.sha512"

echo "Verifying GPG signature..."
gpg --verify "apache-grails-${VERSION}-incubating-src.zip.asc" "apache-grails-${VERSION}-incubating-src.zip"

rm -rf grails || true
echo "Extracting zip file..."
unzip -q "apache-grails-${VERSION}-incubating-src.zip"

SRC_DIR="grails"
if [ ! -d "$SRC_DIR" ]; then
  echo "Error: Expected extracted folder '$SRC_DIR' not found."
  exit 1
fi

echo "Checking for required files existence..."
REQUIRED_FILES=("LICENSE" "NOTICE" "README.md" "CONTRIBUTING.md" "PUBLISHED_ARTIFACTS" "CHECKSUMS")

for FILE in "${REQUIRED_FILES[@]}"; do
  if [ ! -f "$SRC_DIR/$FILE" ]; then
    echo "Missing required file: $FILE"
    exit 1
  fi
done

echo "✅ All checks passed successfully for Apache Grails ${VERSION}."