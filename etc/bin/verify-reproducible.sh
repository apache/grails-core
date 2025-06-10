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

DOWNLOAD_LOCATION="${1:-downloads}"
DOWNLOAD_LOCATION=$(realpath "${DOWNLOAD_LOCATION}")
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

CWD=$(pwd)

cleanup() {
  echo "❌ Verification failed. ❌"
}
trap cleanup ERR

cd "${DOWNLOAD_LOCATION}/grails"
echo "Searching under ${DOWNLOAD_LOCATION}"

mkdir -p "${DOWNLOAD_LOCATION}/grails/etc/bin/results"
if [[ -f "${DOWNLOAD_LOCATION}/grails/CHECKSUMS" ]]; then
  echo "✅ File 'CHECKSUMS' exists."
else
  echo "❌ File 'CHECKSUMS' not found. Grails Source Distributions should have a CHECKSUMS file at the root..."
  exit 1
fi

if [[ -f "${DOWNLOAD_LOCATION}/grails/BUILD_DATE" ]]; then
  echo "✅ File 'BUILD_DATE' exists."
else
  echo "❌ File 'BUILD_DATE' not found. Grails Source Distributions should have a BUILD_DATE file at the root..."
  exit 1
fi
export SOURCE_DATE_EPOCH=$(cat "${DOWNLOAD_LOCATION}/grails/BUILD_DATE")
export TEST_BUILD_REPRODUCIBLE='true'

if [[ -d "${DOWNLOAD_LOCATION}/grails/etc/bin/results/first" ]]; then
  echo "✅ Directory 'first' exists."
else
  echo "❌ Directory 'first' not found. Please place the published jar files under ${DOWNLOAD_LOCATION}/grails/etc/bin/results/first..."
  exit 1
fi

killall -e java || true
cd grails-gradle
./gradlew publishToMavenLocal --rerun-tasks -PskipTests --no-build-cache
cd ..
./gradlew publishToMavenLocal --rerun-tasks -PskipTests --no-build-cache
echo "Generating Checksums for Built Jars"
"${SCRIPT_DIR}/generate-build-artifact-hashes.groovy" "${DOWNLOAD_LOCATION}/grails" > "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt"
if [ -e "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt" ] && [ ! -s "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt" ]; then
  echo "❌ Error: Could not find any checksums for built jar files!"
  exit 1
fi

echo "Flattening Checksum file"
## Flatten the jar files since our published artifacts are flat
tmpfile=$(mktemp)
while read -r filepath checksum; do
  printf '%s %s\n' "$(basename "$filepath")" "$checksum"
done < "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt" > "$tmpfile" && mv "$tmpfile" "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt"

echo "Filtering non-published jars"
# filter to only published jars to compare against
cut -d' ' -f1 "${DOWNLOAD_LOCATION}/grails/CHECKSUMS" | grep -Ff - "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt" > "${DOWNLOAD_LOCATION}/grails/etc/bin/results/filtered.txt"
rm -f "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt"
mv "${DOWNLOAD_LOCATION}/grails/etc/bin/results/filtered.txt" "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second.txt"

mkdir -p "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second"
find . -path ./etc -prune -o -type f -path '*/build/libs/*.jar' ! -name "buildSrc.jar" -exec cp -t "${DOWNLOAD_LOCATION}/grails/etc/bin/results/second/" -- {} +

cd "${DOWNLOAD_LOCATION}/grails/etc/bin/results"

echo "Checking for differences in checksums"
# diff -u CHECKSUMS second.txt
DIFF_RESULTS=$(comm -3 <(sort ../../../CHECKSUMS) <(sort second.txt) | cut -d' ' -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | uniq | sort)
echo "$DIFF_RESULTS" > diff.txt

if [ -s diff.txt ]; then
  echo "❌ Differences Found ❌"
  cat diff.txt
  echo "❌ Differences Found ❌"
else
  echo "✅ No Differences Found. ✅"
  exit 0
fi

printf '%s\n' "$DIFF_RESULTS" | sed 's|^etc/bin/results/||' > toPurge.txt
find first -type f -name '*.jar' -print | sed 's|^first/||' | grep -F -x -v -f toPurge.txt |
  while IFS= read -r f; do
    rm -f "./first/$f"
  done
find second -type f -name '*.jar' -print | sed 's|^second/||' | grep -F -x -v -f toPurge.txt |
  while IFS= read -r f; do
    rm -f "./second/$f"
  done
rm toPurge.txt
find . -type d -empty -delete
cd "$CWD"
exit 1