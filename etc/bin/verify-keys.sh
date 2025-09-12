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

DOWNLOAD_LOCATION="${1:-downloads}"
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

mkdir -p "${DOWNLOAD_LOCATION}"

echo "Downloading KEYS file ..."
curl -f -L -o "${DOWNLOAD_LOCATION}/SVN_KEYS" "https://dist.apache.org/repos/dist/release/incubator/grails/KEYS"
echo "✅ KEYS Downloaded"

echo "Comparing checked in KEYS file with SVN KEYS file"
SVN_CHECKSUM=$(shasum -a 512 "${DOWNLOAD_LOCATION}/SVN_KEYS" | awk '{print $1}')
GITHUB_CHECKSUM=$(shasum -a 512 "${SCRIPT_DIR}/../../KEYS" | awk '{print $1}')
if [ "${SVN_CHECKSUM}" != "${GITHUB_CHECKSUM}" ]; then
    echo "❌ Checksum mismatch between SVN and GitHub KEYS file"
    exit 1
else
    echo "✅ Checksum matches between SVN and GitHub KEYS file"
fi
