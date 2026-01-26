#!/bin/bash
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
EXIT_STATUS=0

start=`date +%s`

echo "Starting client and server"
./gradlew bootRun -parallel --console=plain &
PID1=$!

echo "Waiting 50 seconds  for client and server to start"

sleep 50

echo "Executing tests"

./gradlew --rerun-tasks -Dgeb.env=chromeHeadless functional-tests:test --console=plain || EXIT_STATUS=$?

if [ $EXIT_STATUS -ne 0 ]; then
  exit $EXIT_STATUS
fi

./gradlew --rerun-tasks -Dgeb.env=firefoxHeadless functional-tests:test --console=plain || EXIT_STATUS=$?

if [ $EXIT_STATUS -ne 0 ]; then
  exit $EXIT_STATUS
fi

killall -9 java
killall node

end=`date +%s`
runtime=$((end-start))
echo "execution took $runtime seconds"