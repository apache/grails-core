#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -euo pipefail

# The marker is written only after every task has completed, with task failures
# included. Never accept a marker left by an earlier invocation.
rm -f build/ci-build-finished.txt build/ci-build-finished.txt.tmp
set +e
timeout --kill-after=30s 90m ./gradlew bootJar check \
    --init-script .github/ci-exit-after-build.init.gradle \
    --no-daemon --continue --rerun-tasks --stacktrace \
    -PonlyNeo4jTests -PskipCodeStyle "$@"
code=$?
set -e

if [[ $code == 124 && -f build/ci-build-finished.txt ]] \
    && [[ "$(cat build/ci-build-finished.txt)" == SUCCESS ]]; then
    echo 'All Gradle tasks passed; stopped stalled build teardown.'
    exit 0
fi
exit "$code"
