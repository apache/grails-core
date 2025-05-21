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

This document outlines the steps to release a new version of Apache Grails. It is important to follow these steps carefully to ensure a smooth release process. The release process can be divided into 4 stages across 2 repositories: `grails-core` & `grails-forge`.

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

## 2. Verifying

Prior to releasing a vote, we need to verify the staged artifacts. At a high level, the following must be completed to verify these artifacts:
1. Download the zipped source distribution artifacts from the grails-core release page:
   * `apache-grails-<version>-incubating-src.zip` - the source distribution
   * `apache-grails-<version>-incubating-src.zip.asc` - the public key to verify the source distribution
   * `apache-grails-<version>-incubating-src.zip.sha512` - the checksum to verify the source distribution
2. Verify the distribution checksum via the command:
   ```bash
   shasum -a 512 -c apache-grails-<version>-incubating-src.zip.sha512
   ```
3. Verify the distribution signature via the command:
   ```bash
    gpg --verify apache-grails-<version>-incubating-src.zip.asc apache-grails-<version>-incubating-src.zip
    ```
4. Extract the zip file and verify the contents:
   * Ensure the `LICENSE` & `NOTICE` files are present to ensure license compliance.
   * Ensure `README.md` & `CONTRIBUTING.md` are present to ensure project build & usage instructions are present.
   * Ensure the `PUBLISHED` file is present so we know how to pull the various jar files.
5. Verify the signed jar files by downloading all jars inside of the `PUBLISHED` file.
   * For each jar, verify the jar file matches the published one:
   ```bash
    gpg --verify <jar>.asc <jar>
    ```
6. Run the `verify-distribution.sh` shell script to compare the published jar files to a locally built version of them.
7. For any differences, extract the jar files, use IntelliJ to compare each differing file. Assuming differences are ordering related, we can continue with the verification.
8. Download the cli's: `grailsw` (wrapper), `grails` (delegating), `grails-forge-cli`, and `grails-shell-cli`.  For each CLI, verify the published signature in the `PUBLISHED` file:
   ```bash
    gpg --verify <cli>.asc <cli>
   ```
10. testing `grailsw`:
    * set GRAILS_REPO_URL to the staging repository
    * run `grailsw` and ensure it downloads the correct jars to `.grails` (verify the checksums of the jars)
11. testing `grails-forge-cli`:
    * create a forge project
    * modify the application to include the staging repo
    * start the project
12. testing `grails-shell-cli`:
    * create a basic app:
    ```bash
    grails-shell-cli create-app test
    ```
    * modify the application to include the staging repo
    * start the app:
    ```bash
    grails-shell-cli run-app
    ```
13. Perform the same tests as 11. & 12, but use the delegating cli `grails`


## 3. Voting

TODO

## 3. Releasing

TODO