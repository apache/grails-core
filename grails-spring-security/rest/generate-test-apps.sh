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

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PREVIOUS_DIR=$(pwd)
cd "${SCRIPT_DIR}"

rm -rf ./build || true
mkdir ./build
export GRAILS_SECURITY_VERSION=`cat ${SCRIPT_DIR}/../gradle.properties | grep "^projectVersion=" | sed -n 's/^projectVersion=//p'`
export GRAILS_VERSION=`cat ${SCRIPT_DIR}/../gradle.properties | grep "^grailsVersion=" | sed -n 's/^grailsVersion=//p'`

for project in `find . -maxdepth 1 -type d ! -name . -name 'spring-security-rest*'`; do
    cd "${SCRIPT_DIR}/${project}"
    echo "Publishing ${project} to maven local"
    ../../gradlew clean publishToMavenLocal
done
cd "${SCRIPT_DIR}/../plugin-core/plugin"
echo "Publishing Spring Security Core plugin to maven local"
../../gradlew clean publishToMavenLocal

echo "Plugin version: ${GRAILS_SECURITY_VERSION}. Grails version for test apps: ${GRAILS_VERSION}"
source "$HOME/.sdkman/bin/sdkman-init.sh"

[[ -d  ~/.sdkman/candidates/grails/${GRAILS_VERSION} ]] || sdk install grails ${GRAILS_VERSION}
sdk use grails ${GRAILS_VERSION}
cd "${SCRIPT_DIR}/build"

# TODO: only set ~/.m2/repository once fixed in Grails 7.0.8
export GRAILS_REPO_URL='mavenLocal()'
for feature in `ls "${SCRIPT_DIR}/spring-security-rest-testapp-profile/features/"`; do
     grails create-app -profile org.apache.grails.profiles:spring-security-rest-testapp-profile:${GRAILS_SECURITY_VERSION} -features ${feature} ${feature}
done

cd "$PREVIOUS_DIR"
