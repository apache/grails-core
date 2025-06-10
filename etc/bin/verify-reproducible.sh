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

# This file assumes the gnu version of coreutils is installed, which is not installed by default on a mac
set -e

PROJECT_NAME='grails-spring-security'
DOWNLOAD_LOCATION="${1:-downloads}"
DOWNLOAD_LOCATION=$(realpath "${DOWNLOAD_LOCATION}")
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

CWD=$(pwd)

cleanup() {
  echo "❌ Verification failed. ❌"
}
trap cleanup ERR

cd "${DOWNLOAD_LOCATION}/${PROJECT_NAME}"
echo "Searching under ${DOWNLOAD_LOCATION}"

mkdir -p "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results"
if [[ -f "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/CHECKSUMS" ]]; then
  echo "✅ File 'CHECKSUMS' exists."
else
  echo "❌ File 'CHECKSUMS' not found. ${PROJECT_NAME} Source Distributions should have a CHECKSUMS file at the root..."
  exit 1
fi

if [[ -f "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/BUILD_DATE" ]]; then
  echo "✅ File 'BUILD_DATE' exists."
else
  echo "❌ File 'BUILD_DATE' not found. ${PROJECT_NAME} Source Distributions should have a BUILD_DATE file at the root..."
  exit 1
fi
export SOURCE_DATE_EPOCH=$(cat "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/BUILD_DATE")
export TEST_BUILD_REPRODUCIBLE='true'

if [[ -d "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/first" ]]; then
  echo "✅ Directory containing downloaded jar files exists ('first')."
else
  echo "❌ Directory 'first' not found. Please place the published jar files under ${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/first..."
  exit 1
fi

killall -e java || true
./gradlew publishToMavenLocal --rerun-tasks -PskipTests --no-build-cache
echo "Generating Checksums for Built Jars"
"${SCRIPT_DIR}/generate-build-artifact-hashes.groovy" "${DOWNLOAD_LOCATION}/${PROJECT_NAME}" > "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second.txt"
if [ -e "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second.txt" ] && [ ! -s "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second.txt" ]; then
  echo "❌ Error: Could not find any checksums for built jar files!"
  exit 1
fi

echo "Flattening Checksum file"
## Flatten the jar files since our published artifacts are flat
tmpfile=$(mktemp)
while read -r filepath checksum; do
  printf '%s %s\n' "$(basename "$filepath")" "$checksum"
done < "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second.txt" > "$tmpfile" && mv "$tmpfile" "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second.txt"

echo "Filtering non-published jars"
# filter to only published jars to compare against
cut -d' ' -f1 "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/CHECKSUMS" | grep -Ff - "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second.txt" > "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/filtered.txt"
rm -f "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second.txt"
mv "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/filtered.txt" "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second.txt"

mkdir -p "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second"
find . -path ./etc -prune -o -type f -path '*/build/libs/*.jar' ! -name "buildSrc.jar" -exec cp -t "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results/second/" -- {} +

cd "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results"

echo "Checking for differences in checksums"
# diff -u CHECKSUMS second.txt
DIFF_RESULTS=$(comm -3 <(sort ../../../CHECKSUMS) <(sort second.txt) | cut -d' ' -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | grep -v '^$' | uniq | sort)
echo "${DIFF_RESULTS}" > diff.txt

if [ -n "${DIFF_RESULTS}" ]; then
  echo "${DIFF_RESULTS}" > diff.txt
else
  > diff.txt  # Empty the file explicitly
fi

if [ -s diff.txt ]; then
  echo "Differences were found, diffing jar files ..."
  if [[ ! -f "vineflower.jar" ]]; then
      echo "Downloading Vineflower decompiler..."
      curl -sL -o "vineflower.jar" https://github.com/Vineflower/vineflower/releases/download/1.11.1/vineflower-1.11.1.jar
      if [[ $? -ne 0 ]]; then
          echo "❌ Failed to download vineflower.jar ❌"
          exit 1
      fi
  fi

  while IFS= read -r jar_file; do
      echo "Checking jar '${jar_file}'..."

      echo "Extracting ${jar_file}"
      "${SCRIPT_DIR}/extract-build-artifact.sh" "${jar_file}" "${DOWNLOAD_LOCATION}/${PROJECT_NAME}/etc/bin/results"
      echo "✅ Extracted ${jar_file} to firstArtifact and secondArtifact directories."

      # Check extraction success
      if [[ ! -d "firstArtifact" || ! -d "secondArtifact" ]]; then
          echo "❌ Missing extracted artifacts for ${jar_file} ❌"
          echo "${jar_file}" >> diff_purged.txt
          continue
      fi

      rm -rf "firstSource" "secondSource" || true
      mkdir -p "firstSource" "secondSource"

      echo "Decompiling ${jar_file} class files..."
      java -jar vineflower.jar firstArtifact firstSource > /dev/null 2>&1
      java -jar vineflower.jar secondArtifact secondSource > /dev/null 2>&1
      echo "✅ Decompiled ${jar_file}"

      set +e
      DIFF_RESULT=$(diff -r -q "firstSource" "secondSource")
      set -e

      if [[ -z "${DIFF_RESULT}" ]]; then
          echo "✅ No differences remain for ${jar_file}. Removing from diff.txt."
      else
          echo "❌ Differences still found in ${jar_file}."
          echo "${jar_file}" >> diff_purged.txt
      fi

  done < diff.txt
  mv diff_purged.txt diff.txt
  rm -rf firstArtifact secondArtifact firstSource secondSource || true

  if [ -s diff.txt ]; then
  echo "❌ Differences Found ❌"
  cat diff.txt
  echo "❌ Differences Found ❌"
  else
    echo "✅ Differences were resolved via decompilation. ✅"
  fi
else
  echo "✅ No Differences Found. ✅"
  exit 0
fi

printf '%s\n' "${DIFF_RESULTS}" | sed 's|^etc/bin/results/||' > toPurge.txt
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