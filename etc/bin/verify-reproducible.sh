#!/usr/bin/env bash
#
#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# This file assumes the gnu version of coreutils is installed, which is not installed by default on a mac
set -e

DOWNLOAD_LOCATION="${3:-downloads}"
DOWNLOAD_LOCATION=$(realpath "${DOWNLOAD_LOCATION}")
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

CWD=$(pwd)

cd "${DOWNLOAD_LOCATION}/grails"

mkdir -p "${DOWNLOAD_LOCATION}/grails/etc/bin/results"
if [[ -f "${DOWNLOAD_LOCATION}/grails/PUBLISHED_ARTIFACTS" ]]; then
  echo "✅ File 'PUBLISHED_ARTIFACTS' exists."
else
  echo "❌ File 'PUBLISHED_ARTIFACTS' not found. Grails Source Distributions should have a PUBLISHED_ARTIFACTS file at the root..."
  exit 1
fi

if [[ -f "${DOWNLOAD_LOCATION}/grails/BUILD_DATE" ]]; then
  echo "✅ File 'BUILD_DATE' exists."
else
  echo "❌ File 'BUILD_DATE' not found. Grails Source Distributions should have a BUILD_DATE file at the root..."
  exit 1
fi
export SOURCE_DATE_EPOCH=$(cat "${DOWNLOAD_LOCATION}/grails/BUILD_DATE")

if [[ -d "${DOWNLOAD_LOCATION}/grails/etc/bin/results/published" ]]; then
  echo "✅ Directory 'published' exists."
else
  echo "❌ Directory 'published' not found. Please place the PUBLISHED jar files under ${DOWNLOAD_LOCATION}/grails/etc/bin/results/published..."
  exit 1
fi

killall -e java || true
cd grails-gradle
./gradlew build --rerun-tasks -PskipTests --no-build-cache
cd ..
./gradlew build --rerun-tasks -PskipTests --no-build-cache
"${SCRIPT_DIR}/generate-build-artifact-hashes.groovy" > "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt"
mkdir -p "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second"
find . -path ./etc -prune -o -type f -path '*/build/libs/*.jar' -exec cp -t "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second/" -- {} +

cd "${DOWNLOAD_LOCATION}/grails/etc/bin/results"

# diff -u PUBLISHED_ARTIFACTS second.txt
DIFF_RESULTS=$(comm -3 <(cut -d' ' -f1 ../../../PUBLISHED_ARTIFACTS | sort) <(sort second.txt) | cut -d' ' -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | uniq | sort)
echo "Differing artifacts:"
echo "$DIFF_RESULTS" > diff.txt
cat diff.txt

printf '%s\n' "$DIFF_RESULTS" | sed 's|^etc/bin/results/||' > toPurge.txt
find published -type f -name '*.jar' -print | sed 's|^published/||' | grep -F -x -v -f toPurge.txt |
  while IFS= read -r f; do
    rm -f "./published/$f"
  done
find second -type f -name '*.jar' -print | sed 's|^second/||' | grep -F -x -v -f toPurge.txt |
  while IFS= read -r f; do
    rm -f "./second/$f"
  done
rm toPurge.txt
find . -type d -empty -delete
cd "$CWD"
