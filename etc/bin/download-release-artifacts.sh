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
set -e

PROJECT_NAME='grails'
REPO_NAME='apache/grails-core'
SVN_FOLDER='core'
RELEASE_TAG=$1
DOWNLOAD_LOCATION="${2:-downloads}"

if [ -z "${RELEASE_TAG}" ]; then
  echo "Usage: $0 [release-tag] <optional download location>"
  exit 1
fi

echo "Downloading files to ${DOWNLOAD_LOCATION}"
mkdir -p "${DOWNLOAD_LOCATION}"

VERSION=${RELEASE_TAG#v}

# Source distro
echo "Downloading GitHub source release files"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.asc" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.asc"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.sha512" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.sha512"

echo "Downloading SVN source release files"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/sources/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.asc" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/sources/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.asc"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.sha512" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/sources/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.sha512"

# wrapper
echo "Downloading GitHub wrapper release files"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.asc" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.asc"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.sha512" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.sha512"

echo "Downloading SVN wrapper release files"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/distribution/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.asc" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/distribution/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.asc"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.sha512" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/distribution/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.sha512"

# sdkman delegating cli
echo "Downloading GitHub cli release files"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.asc" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.asc"
curl -f -L -o "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.sha512" "https://github.com/${REPO_NAME}/releases/download/${RELEASE_TAG}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.sha512"

echo "Downloading SVN wrapper release files"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/distribution/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.asc" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/distribution/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.asc"
curl -f -L -o "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.sha512" "https://dist.apache.org/repos/dist/dev/incubator/grails/${SVN_FOLDER}/${VERSION}/distribution/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.sha512"

# validate downloads
set +e

echo "Comparing SVN vs GitHub source release files"
cmp -s "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.asc" "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.asc"
if [ $? -eq 0 ]; then
  echo "✅ Identical SVN vs GitHub Upload for apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.asc"
else
  echo "❌Different SVN vs GitHub Upload for apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.asc"
  exit 1
fi

cmp -s "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.sha512" "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.sha512"
if [ $? -eq 0 ]; then
  echo "✅ Identical SVN vs GitHub Upload for apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.sha512"
else
  echo "❌ Different SVN vs GitHub Upload for apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip.sha512"
  exit 1
fi

SRC_ZIP_SVN_CHECKSUM=$(shasum -a 512 "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip" | awk '{print $1}')
SRC_ZIP_GITHUB_CHECKSUM=$(shasum -a 512 "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-src.zip" | awk '{print $1}')
if [ "${SRC_ZIP_SVN_CHECKSUM}" != "${SRC_ZIP_GITHUB_CHECKSUM}" ]; then
    echo "❌ Checksum mismatch between SVN and GitHub source zip files"
    exit 1
else
    echo "✅ Checksum matches between SVN and GitHub source zip files"
fi

echo "Comparing SVN vs GitHub wrapper release files"
cmp -s "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.asc" "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.asc"
if [ $? -eq 0 ]; then
  echo "✅ Identical SVN vs GitHub Upload for apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.asc"
else
  echo "❌Different SVN vs GitHub Upload for apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.asc"
  exit 1
fi

cmp -s "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.sha512" "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.sha512"
if [ $? -eq 0 ]; then
  echo "✅ Identical SVN vs GitHub Upload for apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.sha512"
else
  echo "❌ Different SVN vs GitHub Upload for apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip.sha512"
  exit 1
fi

WRAPPER_ZIP_SVN_CHECKSUM=$(shasum -a 512 "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip" | awk '{print $1}')
WRAPPER_ZIP_GITHUB_CHECKSUM=$(shasum -a 512 "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-wrapper-${VERSION}-incubating-bin.zip" | awk '{print $1}')
if [ "${WRAPPER_ZIP_SVN_CHECKSUM}" != "${WRAPPER_ZIP_GITHUB_CHECKSUM}" ]; then
    echo "❌ Checksum mismatch between SVN and GitHub wrapper zip files"
    exit 1
else
    echo "✅ Checksum matches between SVN and GitHub wrapper zip files"
fi

echo "Comparing SVN vs GitHub cli release files"
cmp -s "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.asc" "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.asc"
if [ $? -eq 0 ]; then
  echo "✅ Identical SVN vs GitHub Upload for apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.asc"
else
  echo "❌Different SVN vs GitHub Upload for apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.asc"
  exit 1
fi

cmp -s "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.sha512" "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.sha512"
if [ $? -eq 0 ]; then
  echo "✅ Identical SVN vs GitHub Upload for apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.sha512"
else
  echo "❌ Different SVN vs GitHub Upload for apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip.sha512"
  exit 1
fi

CLI_ZIP_SVN_CHECKSUM=$(shasum -a 512 "${DOWNLOAD_LOCATION}/apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip" | awk '{print $1}')
CLI_ZIP_GITHUB_CHECKSUM=$(shasum -a 512 "${DOWNLOAD_LOCATION}/github-apache-${PROJECT_NAME}-${VERSION}-incubating-bin.zip" | awk '{print $1}')
if [ "${CLI_ZIP_SVN_CHECKSUM}" != "${CLI_ZIP_GITHUB_CHECKSUM}" ]; then
    echo "❌ Checksum mismatch between SVN and GitHub wrapper zip files"
    exit 1
else
    echo "✅ Checksum matches between SVN and GitHub wrapper zip files"
fi