<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Apache Grails Release Process

This document outlines the steps to release a new version of Apache Grails. It is important to follow these steps carefully to ensure a smooth release process. The release process can be divided into 3 stages across 2 repositories: `grails-core` & `grails-forge`.

## Prerequisites

Prior to starting the release process, ensure that any other dependent library is set to a non-snapshot version in `dependencies.gradle`. Per the [Apache Release Policy](https://www.apache.org/legal/release-policy.html), all dependencies must be official releases and cannot be snapshots. The build will fail if any snapshot dependencies are present.

## 1. Staging 

During the staging step, we must create a source distribution & stage any binary artifacts that end users will consume - including the grails-cli & grails-forge-cli located in the grails-forge repository.
1. Create a release in `grails-core`:
   * Click "Draft a new release" here: https://github.com/apache/grails-core/releases
   * On the draft new release screen, we execute the following steps:
     * The tag will have the prefix 'v', so for our release it would be 'v7.0.0-M4'
     * The "Target" will be the branch we build out of (this case it's the default, 7.0.x)
     * Previous tag will be auto
     * Click "Generate Release Notes"
       * This will then scan our commit history and we adjust the release notes per project agreement.
     * Check "pre-release"
     * Click "Publish Release"
2. (grails-core) The Github workflow 'release.yml' will kick off, which will run the `publish` job steps. This job will:
   * checkout the project
   * setup gradle
   * extract the version # from the tag
   * run the pre-release workflow (updates gradle.properties to be the version specified by the user)
   * extract signing secrets from github action variables 
   * build the project, sign the jar files, and stage them to the necessary location
3. Create a matching release in `grails-forge`:
   * Follow the same steps to create a release in grails-forge. Update any release notes specific to the delegating cli & grails forge.
4. (grails-forge)
   * The Github workflow 'release.yml' will kick off, which will run the `publish` job steps. This job will perform the similar steps as above, but will perform these additional steps: 
     * download the tagged grails source
     * generate a source distribution for apache

Once `grails-forge` & `grails-core` are published the end source distribution should be staged. 

## 2. Voting

TODO

## 3. Releasing

TODO