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

rm -rf build/
mkdir build
export pluginVersion=`cat build.gradle | grep "version \"" | sed -n 's/^[ \t]*version\ "//pg' | sed -n 's/"//pg'`
export grailsVersion=`cat spring-security-rest-testapp-profile/gradle.properties | grep grailsVersion | sed -n 's/^grailsVersion=//p'`
./gradlew clean install

echo "Plugin version: $pluginVersion. Grails version for test apps: $grailsVersion"
source "$HOME/.sdkman/bin/sdkman-init.sh"

[[ -d  ~/.sdkman/candidates/grails/$grailsVersion ]] || sdk install grails $grailsVersion
sdk use grails $grailsVersion
cd build

for feature in `ls ../spring-security-rest-testapp-profile/features/`; do
     grails create-app -profile org.grails.plugins:spring-security-rest-testapp-profile:$pluginVersion -features $feature $feature
done