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

rm -rf "${SCRIPT_DIR}/results" || true
mkdir -p "${SCRIPT_DIR}/results"

git clean -xdf --exclude='etc/bin' --exclude='.idea' --exclude='.gradle'
killall -e java || true
./gradlew build --rerun-tasks -PskipTests --no-build-cache
"${SCRIPT_DIR}/generate-build-artifact-hashes.groovy" > "${SCRIPT_DIR}/results/first.txt"
mkdir -p "${SCRIPT_DIR}/results/first"
find . -path ./etc -prune -o -type f -path '*/build/libs/*.jar' -print0 | xargs -0 cp --parents -t "${SCRIPT_DIR}/results/first/"

git clean -xdf --exclude='etc/bin' --exclude='.idea' --exclude='.gradle'
killall -e java || true
./gradlew build --rerun-tasks -PskipTests --no-build-cache
"${SCRIPT_DIR}/generate-build-artifact-hashes.groovy" > "${SCRIPT_DIR}/results/second.txt"
mkdir -p "${SCRIPT_DIR}/results/second"
find . -path ./etc -prune -o -type f -path '*/build/libs/*.jar' -print0 | xargs -0 cp --parents -t "${SCRIPT_DIR}/results/second/"

cd "${SCRIPT_DIR}/results"

# diff -u first.txt second.txt
DIFF_RESULTS=$(comm -3 first.txt second.txt | cut -d' ' -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | grep -v '^$' | uniq | sort)
echo "Differing artifacts:"
echo "$DIFF_RESULTS" > diff.txt
cat diff.txt

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
