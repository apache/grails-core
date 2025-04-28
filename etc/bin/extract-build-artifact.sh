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
set -e

ARTIFACT_NAME=$1

if [ -z "${ARTIFACT_NAME}" ]; then
  echo "Usage: $0 <artifact-name>"
  exit 1
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [ -z "${SCRIPT_DIR}/results/first/${ARTIFACT_NAME}" ]; then
    echo "First Artifact Not found: $ARTIFACT_NAME could not be found under ${SCRIPT_DIR}/results/first/${ARTIFACT_NAME}"
    exit 1;
else
  echo "First Artifact Found @ ${SCRIPT_DIR}/results/first/${ARTIFACT_NAME}"
fi
if [ -z "${SCRIPT_DIR}/results/second/${ARTIFACT_NAME}" ]; then
    echo "Second Artifact Not found: $ARTIFACT_NAME could not be found under ${SCRIPT_DIR}/results/second/${ARTIFACT_NAME}"
    exit 1;
else
  echo "Second Artifact Found @ ${SCRIPT_DIR}/results/first/${ARTIFACT_NAME}"
fi

rm -rf "${SCRIPT_DIR}/results/firstArtifact" || true
rm -rf "${SCRIPT_DIR}/results/secondArtifact" || true

unzip "${SCRIPT_DIR}/results/first/${ARTIFACT_NAME}" -d "${SCRIPT_DIR}/results/firstArtifact"
unzip "${SCRIPT_DIR}/results/second/${ARTIFACT_NAME}" -d "${SCRIPT_DIR}/results/secondArtifact"
