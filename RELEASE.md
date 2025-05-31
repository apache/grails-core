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
2. (grails-core) The Github workflow from `release.yml`, titled `Release - Create grails-core`, will kick off.  The `publish` job will:
   * checkout the project
   * setup gradle
   * extract the version # from the tag
   * run the pre-release workflow (updates gradle.properties to be the version specified by the user)
   * extract signing secrets from github action variables 
   * build the project, sign the jar files, and stage them to the necessary locations
   * add the grails wrapper to the `grails-core` release
   * close the staging repository so the `grails-core` artifacts can be accessed
3. Create a matching release in `grails-forge`:
   * Follow the same steps to create a release in grails-forge. Update any release notes specific to the delegating cli & grails forge.
4. (grails-forge) The Github workflow `release.yml`, titled `Release - Create grails-forge`, will kick off.  This workflow will run a `publish` job that will complete similar steps to the `grails-core` publish job.
5. Kick off the `Release - Source Distribution` workflow from `release-source-distribution.yml` in `grails-core`. This job will: 
     * download the tagged grails source
     * download the tagged grails-forge source
     * generate a source distribution meeting the ASF requirements
     * upload the source distribution to the Github `grails-core` release
6. Kick off the `Release - Upload to dist.apache.org` workflow from `release-upload.yml` in `grails-core`. This job will:
     * update the KEYS file under https://dist.apache.org/repos/dist/release/grails/KEYS with the version in the tagged source code
     * upload the source distribution to https://dist.apache.org/repos/dist/dev/grails/VERSION/sources
     * upload the grails-wrapper binary distribution to https://dist.apache.org/repos/dist/dev/grails/VERSION/distribution
     * upload the grails binary distribution to https://dist.apache.org/repos/dist/dev/grails/VERSION/distribution (note: this is the sdkman artifact)

## 2. Verifying Artifacts are Authentic

Prior to releasing a vote, we need to verify the staged artifacts. The below sections detail all of the necessary steps to ensure the source & binary distributions are authentic and have not been changed. To verify all of these at once, use the script: 

```bash
    verify.sh <staging repo id> <release tag> <download location>
```

For Example:
```bash
    verify.sh orgapachegrails-1030 v7.0.0-M4 /tmp/grails-verify
```

### Download the Staged Artifacts

Use `etc/bin/download-release-artifacts.sh` to download the staged artifacts. This script will download the source distribution, wrapper binary distribution, and sdkman binary distribution. The distribution should come from [https://dist.apache.org/repos/dist/dev/grails/version](https://dist.apache.org/repos/dist/dev/grails).

### Source Distribution Verification

The following are the source distribution artifacts:
   * `apache-grails-<version>-incubating-src.zip` - the source distribution
   * `apache-grails-<version>-incubating-src.zip.asc` - the generated signature of the source distribution
   * `apache-grails-<version>-incubating-src.zip.sha512` - the checksum to verify the source distribution

Use `etc/bin/verify-source-distribution.sh` to verify the source distribution. This script performs the following:

Verifies the source distribution checksum via the command:
   ```bash
   shasum -a 512 -c apache-grails-<version>-incubating-src.zip.sha512
   ```

Verifies the source distribution signature via the command:
   ```bash
    gpg --verify apache-grails-<version>-incubating-src.zip.asc apache-grails-<version>-incubating-src.zip
   ```

Extracts the zip file and verifies the contents:
   * Ensure the `LICENSE` & `NOTICE` files are present to ensure license compliance.
   * Ensure `README.md` & `CONTRIBUTING.md` are present to ensure project build & usage instructions are present.
   * Ensure the `PUBLISHED_ARTIFACTS` file is present so we know how to pull the various jar files.
   * Ensure the `CHECKSUMS` file is present so we can ensure those checksums match the staged artifacts.

### Jar file Signature Verification (Nexus Staging Repositories)

As part of uploading to repository.apache.org the signatures are verified to match a KEY that has been distributed, but RAO does not verify that the jar files are built with a key trusted by the Grails project.

To ensure checksums match the server & signatures match, run the script `etc/bin/verify-jar-artifacts.sh` in the `grails-core` repository. This script will download the jar files from the staging repository, verify their signatures, and ensure they match the checksums provided in the source distribution.

Example: 
```bash
    ./etc/bin/verify-jar-artifacts.sh orgapachegrails-1026 v7.0.0-M4 <grailsdownloadlocation>
```

### Reproducible Jar File
After all jar files are verified to be signed by a valid Grails key, we need to build a local copy to ensure the file was built with the right code base.

Bootstrap the source distribution so that it can be built: 
    ```bash
    gradle wrapper
    cd grails-gradle
    gradlew wrapper
    cd -
    ```

Run the `verify-reproducible.sh` shell script to compare the published jar files to a locally built version of them. For any differences, extract the jar files, use IntelliJ to compare each differing file. Assuming differences are ordering related, we can continue with the verification.

### Binary Distribution Verification

Grails has 2 binary distributions:
   * `grailsw` - the Grails wrapper, which is a script that downloads the necessary jars to run Grails. This will exist inside of the generated applications, but can be optionally downloaded as a standalone binary distribution.
   * `grails` - the delegating CLI, which is a script that delegates to the Grails Forge CLI. This is the `sdkman` distribution.

#### Verify Grails Wrapper Binary Distribution

The following are the Grails Wrapper distribution artifacts:
* `apache-grails-wrapper-<version>-incubating-bin.zip` - the wrapper distribution
* `apache-grails-wrapper-<version>-incubating-bin.zip.asc` - the generated signature of the wrapper distribution
* `apache-grails-wrapper-<version>-incubating-bin.zip.sha512` - the checksum to verify the wrapper distribution

Use `etc/bin/verify-wrapper-distribution.sh` to verify the wrapper distribution. This script performs the following:

Verifies the wrapper distribution checksum via the command:
   ```bash
   shasum -a 512 -c apache-grails-wrapper-<version>-incubating-bin.zip.sha512
   ```

Verifies the wrapper distribution signature via the command:
   ```bash
    gpg --verify apache-grails-wrapper-<version>-incubating-bin.zip.asc apache-grails-wrapper-<version>-incubating-bin.zip
   ```

Extracts the zip file and verifies the contents:
* Ensure the `LICENSE` & `NOTICE` files are present to ensure license compliance.

#### Verify Grails Delegating CLI Binary Distribution

The following are the Grails distribution artifacts:
* `apache-grails-<version>-incubating-bin.zip` - the cli distribution that will be uploaded to sdkman
* `apache-grails-<version>-incubating-bin.zip.asc` - the generated signature of the cli distribution
* `apache-grails-<version>-incubating-bin.zip.sha512` - the checksum to verify the cli distribution

Use `etc/bin/verify-cli-distribution.sh` to verify the cli distribution. This script performs the following:

Verifies the cli distribution checksum via the command:
   ```bash
   shasum -a 512 -c apache-grails-<version>-incubating-bin.zip.sha512
   ```

Verifies the cli distribution signature via the command:
   ```bash
    gpg --verify apache-grails-<version>-incubating-bin.zip.asc apache-grails-<version>-incubating-bin.zip
   ```

Extracts the zip file and verifies the contents:
* Ensure the `LICENSE` & `NOTICE` files are present to ensure license compliance.

## 3. Verifying CLI Functional

The CLI distribution consists of various CLI's: `grailsw` (wrapper), `grails` (delegating), `grails-forge-cli`, and `grails-shell-cli`. Each CLI needs tested to ensure it's functional prior to release:

* testing `grailsw`:
    * set GRAILS_REPO_URL to the staging repository
    * run `grailsw` and ensure it downloads the correct jars to `.grails` (verify the checksums of the jars)
* testing `grails-shell-cli`:
    * create a basic app:
    ```bash
    grails-shell-cli create-app test
    ```
    * modify the application to include the staging repo
    * start the app:
    ```bash
    grails-shell-cli run-app
    ```
* Perform the same tests, but use the delegating cli `grails` with type forge
    * create a basic app:
    ```bash
    grails -t forge create-app test
    ```
    * modify the application to include the staging repo
    * start the app:
    ```bash
    gradlew bootRun
    ```

## 4. Voting

TODO

## 5. Releasing

TODO

# Rollback

In the event a staged artifact needs rolled back, follow the below steps:

## Rollback Nexus Artifacts (Jars)

To remove a Nexus staging repo, run the workflow `Release - Drop Nexus Staging` in `grails-core`.  The input of this task can be obtained by logging into [repository.apache.org](https://repository.apache.org/index.html#stagingRepositories) and finding the staging repository name. Please note that there will always be 2 staging repositories for a Grails release because we keep `grails-forge` separate from `grails-core`.

## Rollback Distribution

To remove the staged distribution, use your SVN credentials to remove the version directory at [https://dist.apache.org/repos/dist/dev/grails](https://dist.apache.org/repos/dist/dev/grails)

# Appendix: GPG Configuration
If you wish to verify any artifact manually, you must trust the key used to build Grails. To do so:

Download the latest KEYS file and make sure it's imported into gpg:
```bash
    wget https://github.com/apache/grails-core/blob/7.0.x/KEYS
    gpg --import KEYS
```

Setup the key for trust:
```bash
   gpg --edit-key <key id>
   gpg> trust
   gpg> 4
   gpg> quit
```

Setup the key for validity:
```bash
   gpg --lsign-key 08E2CEC47E38FE415F080AB62ADECADC11775306
```

# Appendix: Verification from a Container

The Grails image is officially built on linux in a GitHub action using an Ubuntu container. To run a linux container locally, you can use the following command:

```bash
    docker build -t grails:testing -f etc/bin/Dockerfile . && docker run -it --rm -v $(pwd):/home/groovy/project grails:testing bash
    cd grails-verify
    verify.sh orgapachegrails-1038 v7.0.0-M4 .
    cd grails 
    gradlew wrapper
    cd grails-gradle 
    gradlew wrapper
    cd ../..
    verify-reproducible.sh .
```