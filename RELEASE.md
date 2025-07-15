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

This document outlines the steps to release a new version of Apache Grails. It is important to follow these steps carefully to ensure a smooth release process. The release process can be divided into 4 stages across the `grails-core` repository.

## Prerequisites

Prior to starting the release process, ensure that any other dependent library is set to a non-snapshot version in `dependencies.gradle`. Per the [Apache Release Policy](https://www.apache.org/legal/release-policy.html), all dependencies must be official releases and cannot be snapshots. The build will fail if any snapshot dependencies are present. The verification process will also now check for SNAPSHOT versions.

Due to a limitation with GitHub, private groups cannot be used as approvers for an environment.  For this reason, prior to performing the release, add GitHub username to asf.yaml in the environment section for approvers. Only 6 approvers may exist on a given environment.

## 1. Staging 

During the staging step, we must create a source distribution & stage any binary artifacts that end users will consume.

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
2. The Github workflow from `release.yml`, titled `Release`, will kick off. This workflow contains many jobs. The first 3 jobs will run without delay.  Further jobs, require approval to proceed.
3. (no approval required) The `publish` job will:
   * checkout the project
   * setup gradle
   * extract the version # from the tag
   * run the pre-release workflow (updates gradle.properties to be the version specified by the user)
   * extract signing secrets from github action variables 
   * build the project, sign the jar files, and stage them to the necessary locations
   * add the grails wrapper to the `grails-core` release
   * add the grails binary distribution (grails, grails-shell-cli, and grails-forge-cli) to the `grails-core` release
   * close the staging repository so the `grails-core` artifacts can be accessed
4. (no approval required) The `source` job will: 
     * download the tagged grails source
     * generate a source distribution meeting the ASF requirements
     * upload the source distribution to the Github `grails-core` release
5. (no approval required) The `upload` job will:
     * Download the source distribution
     * Download the binary distributions (wrapper & grails clis)
     * upload the source distribution to https://dist.apache.org/repos/dist/dev/incubator/grails/core/VERSION/sources
     * upload the grails-wrapper binary distribution to https://dist.apache.org/repos/dist/incubator/dev/grails/core/VERSION/distribution
     * upload the grails binary distribution to https://dist.apache.org/repos/dist/incubator/dev/grails/core/VERSION/distribution (note: this is the sdkman artifact)

## 2. Verifying Artifacts are Authentic

Prior to releasing a vote, we need to verify the staged artifacts. The below sections detail all of the necessary steps to ensure the source & binary distributions are authentic and have not been changed. To verify all of these at once, use the script: 

```bash
    verify.sh <release tag> <download location>
```

For Example:
```bash
    verify.sh v7.0.0-M4 /tmp/grails-verify
```

Otherwise, you may follow the subsequent steps in this section to verify each step individually.

### Manual Verification: Download the Staged Artifacts

Use `etc/bin/download-release-artifacts.sh` to download the staged artifacts. This script will download the source distribution, wrapper binary distribution, and sdkman binary distribution. The distribution should come from [https://dist.apache.org/repos/dist/dev/incubator/grails/core/version](https://dist.apache.org/repos/dist/dev/incubator/grails/core).

### Manual Verification: Source Distribution Verification

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

### Manual Verification: Jar file Signature Verification (Nexus Staging Repositories)

As part of uploading to repository.apache.org the signatures are verified to match a KEY that has been distributed, but RAO does not verify that the jar files are built with a key trusted by the Grails project.

To ensure checksums match the server & signatures match, run the script `etc/bin/verify-jar-artifacts.sh` in the `grails-core` repository. This script will download the jar files from the staging repository, verify their signatures, and ensure they match the checksums provided in the source distribution.

Example: 
```bash
    ./etc/bin/verify-jar-artifacts.sh v7.0.0-M4 <grailsdownloadlocation>
```

### Manual Verification: Reproducible Jar Files
After all jar files are verified to be signed by a valid Grails key, we need to build a local copy to ensure the file was built with the right code base. The `very-reproducible.sh` script handles this check, but if the bootstrap needs to be manually bootstrapped, perform the following step: 

    gradle -p gradle bootstrap

Further details on the building can be found in the [INSTALL](INSTALL) document.  Otherwise, run the `verify-reproducible.sh` shell script to compare the published jar files to a locally built version of them. 

If there are any jar file differences, confirm they are relevant by following the following steps: 
1. Extract the differing jar file using the `etc/bin/extract-build-artifact.sh <jarfilepath from diff.txt>`
2. In IntelliJ, under `etc/bin/results` there will now be a `firstArtifact` & `secondArtifact` folder. Select them both, right click, and select `Compared Directories`  

Please note that Grails is officially built on Linux so if there are differences they may be due to the OS platform.
There is a dockerfile checked into to assist building in an environment like GitHub actions. Please see the section
`Appendix: Verification from a Container` for more information.

### Manual Verification: Binary Distribution Verification

Grails has 2 binary distributions:
   * `grailsw` - the Grails wrapper, which is a script that downloads the necessary jars to run Grails. This will exist inside of the generated applications, but can be optionally downloaded as a standalone binary distribution.
   * `grails` - the delegating CLI, which is a script that delegates to the Grails Forge CLI. This is the `sdkman` distribution.

#### Manual Verification: Verify Grails Wrapper Binary Distribution

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

#### Manual Verification: Verify Grails Delegating CLI Binary Distribution

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

## 3. Verifying the CLIs are Functional

The CLI distribution consists of various CLI's: `grailsw` (wrapper), `grails` (delegating), `grails-forge-cli`, and
`grails-shell-cli`. Each CLI needs tested to ensure it's functional prior to release. The `verify.sh` script will output
example commands to perform this verification. However, it if manually verifying, these are the minimum suggested steps:

* Testing `grailsw`:
    * set GRAILS_REPO_URL to the staging repository (https://repository.apache.org/content/groups/staging)
    * run `grailsw` and ensure it downloads the correct jars to `.grails` (verify the checksums of the jars)
  * Please note that the Grails Wrapper (`grailsw`) writes to the `.grails` directory in the user's home directory. If
    not run in an isolated environment, it may attempt to use already downloaded artifacts. If the wrapper fails, try
    removing the `.grails` directory to confirm it isn't a transient state issue.
* Testing `grails-shell-cli`:
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

After all artifacts are uploaded, verified, and tested, we can vote on the release.  As an ASF project, we follow the voting guidelines set forth by the [Apache Voting Process](https://www.apache.org/foundation/voting.html). We are also an incubating ASF software project, so there are 2 votes.  Details for each follow: 

### Apache Grails Incubating PPMC

The first vote is conducted on the [Grails dev mailing list](https://lists.apache.org/list.html?dev@grails.apache.org).  The only valid votes are those on the Apache Grails Incubating PPMC, but the community is welcome to participate to express their support. The vote template is generated as part of the release process.  See the source upload job for the generated email.

### Apache Groovy PMC

As an incubating project under Apache Groovy, after 72 hours & a successful Grails PPMC vote, the Groovy PMC must vote to approve the Apache Grails (incubating) release. This vote is held on the [Groovy dev mailing list](https://lists.apache.org/list.html?dev@groovy.apache.org). See the source upload job for the generated email.

## 5. Releasing

After voting has passed, several steps must be completed to finalize the release. Please complete these steps in teh order listed below.

### Release the Staged Artifacts

In repository.apache.org, the staged artifacts must be released by opening the `grails-core` staging repository and clicking the `Release` button. It took almost 2 hours for the initial ASF release to publish these jars to Maven Central.

### Publish `grails-core` documentation

Open the release workflow in `grails-core` and approve the `Publish Documentation` step.  Wait until finished, and a workflow should eventually kick off in `grails-doc` to publish the gh-pages branch that was updated.

### Move the distributions from `dev` to `release`

On dist.apache.org, the staged source distribution & binary distributions must be moved from `dev` to `release`. Per ASF infrastructure, this must be performed manually, and we are not allowed to automate it via a gated apprvoal workflow.

### Upon move, you will get an email from the ASF reporter

Click the link in this email and mark the release published. For example, if the release is out of core with version `7.0.0-M4`, then the release name with be `CORE-7.0.0-M4`.  Enter the date you moved the distribution artifacts and report the release.

### Advertise the release via SDKMAN

In `grails-core`, kick off the step `Release to SDKMAN!` in the release workflow.  This will cause SDKMAN to pull the new version from Maven Central.

### Deploy grails-forge-web-netty docker container to Google Cloud Run

On the `grails-core` repository, using the release tag, deploy the grails-forge-web-netty docker container to Google Cloud Run using one of the GCP Deploy actions.

On https://start.grails.org, versions are listed in the following order:  RELEASE, NEXT, SNAPSHOT, PREV and PREV-SNAPSHOT.  Use the corresponding action to deploy to each location.

RELEASE - GA releases only

NEXT - Milestones and Release Candidate

SNAPSHOT - current or next version snapshot

PREV - previous release version

PREV-SNAPSHOT - previous version snapshot

### Close out the `grails-core` release

The last step in the `grails-core` release workflow is to run the `Close Release` step.  This will create a merge branch for the original tag with version number and then open a PR to merge back into the next branch.  You will need to merge it into the branch after correcting any merge conflict.

### Update the `grails-static-website`

On the `grails-static-website` repository:

Run the release action (https://github.com/apache/grails-static-website/actions/workflows/release.yml) with the new version to update https://github.com/apache/grails-static-website/blob/HEAD/conf/releases.yml.  This will trigger publishing to the `asf-site-production` branch and https://grails.apache.org

Create a new `.md` file in the `/posts` directory announcing the release.  The PR will stage this change on https://grails.staged.apache.org/ and when the PR is merged it will deploy to https://grails.apache.org

### Flag release in `grails-core` as latest

Update the release in `grails-core` to be flagged as 'latest'

### Announce the release

Announcements should come from your apache email address (see https://infra.apache.org/committer-email.html) and have an expected format.  The announcement should be sent to `dev@grails.apache.org`, `dev@groovy.apache.org`, & `announce@apache.org`.  See the source upload job for the generated email.

# Rollback

In the event a staged artifact needs rolled back, kick off the `Release - Abort Release` workflow. This can only be done for steps prior to the VOTE.

## Rollback Nexus Artifacts (Jars)

If another Grails repo needs to drop the staging jars, there exists a workflow `Release - Drop Nexus Staging` in `grails-core`.  The input of this workflow can be obtained by logging into [repository.apache.org](https://repository.apache.org/index.html#stagingRepositories) and finding the staging repository name. 

## Manual Rollback Distribution

To remove the staged distribution, use your SVN credentials to remove the version directory at [https://dist.apache.org/repos/dist/dev/incubator/grails/core](https://dist.apache.org/repos/dist/incubator/dev/grails/core)

# Appendix: GPG Configuration
If you wish to verify any artifact manually, you must trust the key used to build Grails. To do so, it's best to download the KEYS file that was published to the official location:

Download the latest KEYS file and make sure it's imported into gpg:
```bash
    wget https://dist.apache.org/repos/dist/release/grails/KEYS
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

The Grails image is officially built on linux in a GitHub action using an Ubuntu container. To run a linux container
locally, you can use the following command (substitute `<git-tag-of-release` with the tag name):

```bash
    docker build -t grails:testing -f etc/bin/Dockerfile . && docker run -it --rm -v $(pwd):/home/groovy/project -p 8080:8080 grails:testing bash
    cd grails-verify
    verify.sh <git-tag-of-release> .
```

Please note that the argument `-p 8080:8080` is used to expose the port 8080 of the container to the host machine's port 8080 (fromContainerPort:toHostPort). This allows you to access any running Grails application in the container from your host. If you have another application on port 8080, you can change the port mapping to avoid conflicts, e.g., `-p 8080:8081`. 

In the event that artifacts differ, simply copy them to your project directory and work on your local machine instead of the docker image: 

```bash
    cd ~/project
    rsync -av grails-verify/grails/etc/bin/results/ etc/bin/results/
```
