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
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-$VERSION-incubating-src.zip" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-$VERSION-incubating-src.zip"
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-$VERSION-incubating-src.zip.asc" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-$VERSION-incubating-src.zip.asc"
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-$VERSION-incubating-src.zip.sha512" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-$VERSION-incubating-src.zip.sha512"

# wrapper
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-wrapper-$VERSION-incubating-bin.zip" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-wrapper-$VERSION-incubating-bin.zip"
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-wrapper-$VERSION-incubating-bin.zip.asc" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-wrapper-$VERSION-incubating-bin.zip.asc"
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-wrapper-$VERSION-incubating-bin.zip.sha512" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-wrapper-$VERSION-incubating-bin.zip.sha512"

# sdkman delegating cli
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-$VERSION-incubating-bin.zip" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-$VERSION-incubating-bin.zip"
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-$VERSION-incubating-bin.zip.asc" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-$VERSION-incubating-bin.zip.asc"
curl -L -o "${DOWNLOAD_LOCATION}/apache-grails-$VERSION-incubating-bin.zip.sha512" "https://github.com/apache/grails-core/releases/download/$RELEASE_TAG/apache-grails-$VERSION-incubating-bin.zip.sha512"
