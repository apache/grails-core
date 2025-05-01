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

while getopts "l:" flag; do
  if [ "${flag}" = "l" ]; then
    PROJECT_LOCATION=${OPTARG}
  fi
done

if [ -z "$PROJECT_LOCATION" ]; then
    echo "Project location is required"
    exit 1;
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [ "$PROJECT_LOCATION" = "." ]; then
    echo "Assuming current directory as project location"
    PROJECT_LOCATION=$(pwd)
else
    cd "$SCRIPT_DIR/../../$PROJECT_LOCATION"
    echo "Project location: $(pwd)"
fi

git clean -xdf
killall -e java || true
$SCRIPT_DIR/../../gradlew build --rerun-tasks -PskipTests --no-build-cache
FIRST_BUILD=$("$SCRIPT_DIR/generate-build-artifact-hashes.groovy" "$PROJECT_LOCATION")

git clean -xdf
killall -e java || true
$SCRIPT_DIR/../../gradlew build --rerun-tasks -PskipTests --no-build-cache
SECOND_BUILD=$("$SCRIPT_DIR/generate-build-artifact-hashes.groovy" "$PROJECT_LOCATION")

cd -
echo "$FIRST_BUILD" > first.txt
echo "$SECOND_BUILD" > second.txt

# diff -u first.txt second.txt
echo "Differing artifacts:"
comm -3 first.txt second.txt | cut -d' ' -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | uniq | sort