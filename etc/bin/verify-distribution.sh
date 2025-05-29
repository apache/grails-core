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

export SOURCE_DATE_EPOCH=$(git log -1 --pretty=%ct)

CWD=$(pwd)
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd "${SCRIPT_DIR}/../.."

mkdir -p "${SCRIPT_DIR}/results"
if [[ -f "${SCRIPT_DIR}/results/PUBLISHED" ]]; then
  echo "✓ File 'PUBLISHED' exists."
else
  echo "✗ File 'PUBLISHED' not found. Please place the PUBLISHED distribution file under ${SCRIPT_DIR}/results/PUBLISHED..."
  exit 1
fi

if [[ -d "${SCRIPT_DIR}/results/published_artifacts" ]]; then
  echo "✓ Directory 'published_artifacts' exists."
else
  echo "✗ Directory 'published_artifacts' not found. Please place the PUBLISHED jar files under ${SCRIPT_DIR}/results/published_artifacts..."
  exit 1
fi

git clean -xdf --exclude='etc/bin' --exclude='.idea' --exclude='.gradle'
killall -e java || true
cd grails-gradle
./gradlew build --rerun-tasks -PskipTests --no-build-cache
cd ..
./gradlew build --rerun-tasks -PskipTests --no-build-cache
"${SCRIPT_DIR}/generate-build-artifact-hashes.groovy" > "${SCRIPT_DIR}/results/second.txt"
mkdir -p "${SCRIPT_DIR}/results/second"
find . -path ./etc -prune -o -type f -path '*/build/libs/*.jar' -exec cp -t "${SCRIPT_DIR}/results/second/" -- {} +

cd "${SCRIPT_DIR}/results"

# diff -u PUBLISHED second.txt
DIFF_RESULTS=$(comm -3 PUBLISHED second.txt | cut -d' ' -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | uniq | sort)
echo "Differing artifacts:"
echo "$DIFF_RESULTS" > diff.txt
cat diff.txt

printf '%s\n' "$DIFF_RESULTS" | sed 's|^etc/bin/results/||' > toPurge.txt
find published_artifacts -type f -name '*.jar' -print | sed 's|^published_artifacts/||' | grep -F -x -v -f toPurge.txt |
  while IFS= read -r f; do
    rm -f "./published_artifacts/$f"
  done
find second -type f -name '*.jar' -print | sed 's|^second/||' | grep -F -x -v -f toPurge.txt |
  while IFS= read -r f; do
    rm -f "./second/$f"
  done
rm toPurge.txt
find . -type d -empty -delete
cd "$CWD"
