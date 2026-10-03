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

echo "Setting new version in GrailsUtilsTests.java: ${RELEASE_VERSION}"
sed -i "s/assertEquals(\".*$/assertEquals(\"${RELEASE_VERSION}\", GrailsUtil.getGrailsVersion());/" "${GITHUB_WORKSPACE}/grails-core/src/test/groovy/grails/util/GrailsUtilTests.java"
sed -n "/assertEquals(\".*/p" "${GITHUB_WORKSPACE}/grails-core/src/test/groovy/grails/util/GrailsUtilTests.java"
git add "${GITHUB_WORKSPACE}/grails-core/src/test/groovy/grails/util/GrailsUtilTests.java"

# The documentation links each page to its source on this branch. Record the branch in the
# release commit so a build from the source distribution renders the same links. A branch name
# can contain '/' but never ':', so ':' delimits the substitution.
echo "Setting githubBranch in gradle.properties: ${TARGET_BRANCH:?TARGET_BRANCH must be set}"
sed -i "s:^githubBranch=.*$:githubBranch=${TARGET_BRANCH}:" "${GITHUB_WORKSPACE}/gradle.properties"
sed -n "/^githubBranch=/p" "${GITHUB_WORKSPACE}/gradle.properties"
if ! grep -Fxq "githubBranch=${TARGET_BRANCH}" "${GITHUB_WORKSPACE}/gradle.properties"; then
  echo "ERROR: gradle.properties does not set githubBranch=${TARGET_BRANCH}" >&2
  exit 1
fi
git add "${GITHUB_WORKSPACE}/gradle.properties"
