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
PROJECT_NAME='grails-redis'
RELEASE_TAG=$1
DOWNLOAD_LOCATION="${2:-downloads}"
DOWNLOAD_LOCATION=$(realpath "${DOWNLOAD_LOCATION}")

if [ -z "${RELEASE_TAG}" ]; then
  echo "Usage: $0 [release-tag] <optional download location>"
  exit 1
fi

VERSION=${RELEASE_TAG#v}

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
CWD=$(pwd)

cd "${DOWNLOAD_LOCATION}"
rm *.zip *.asc *.sha512
cd "${PROJECT_NAME}"
find . -mindepth 1 -path ./etc -prune -o -exec rm -rf {} +
cd etc
find . -mindepth 1 -path ./bin -prune -o -exec rm -rf {} +
cd bin
find . -mindepth 1 -path ./results -prune -o -exec rm -rf {} +
cd "${CWD}"