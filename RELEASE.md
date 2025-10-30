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

This document outlines the steps to release a new version of Apache Grails. It is important to follow these steps
carefully to ensure a smooth release process. The release process can be divided into 4 stages across the `grails-core`
repository. The history & requirements of our release setup can be found
in [Appendix: Release Setup Requirements & History](#appendix-release-setup-requirements--history).

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
   * generate project checksums & artifact lists to make verification easier
4. (no approval required) The `source` job will: 
     * download the tagged grails source
     * generate a source distribution meeting the ASF requirements
     * upload the source distribution to the Github `grails-core` release
   * remove any temporary artifacts needed for the source distribution creation
5. (no approval required) The `upload` job will:
     * Download the source distribution
     * Download the binary distributions (wrapper & grails clis)
     * upload the source distribution to https://dist.apache.org/repos/dist/dev/grails/core/VERSION/sources
     * upload the grails-wrapper binary distribution to https://dist.apache.org/repos/dist/dev/grails/core/VERSION/distribution
     * upload the grails binary distribution to https://dist.apache.org/repos/dist/dev/grails/core/VERSION/distribution (note: this is the sdkman artifact)
   * upload a file containing the SVN revision for the uploaded artifacts
   * generate vote email template for the Grails PMC

## 2. Verifying Artifacts are Authentic

Prior to releasing a vote, we need to verify the staged artifacts. The below sections detail all of the necessary steps to ensure the source & binary distributions are authentic and have not been changed. To verify all of these at once, use the script: 

```bash
    verify.sh <release tag> <download location>
```

For Example:
```bash
    verify.sh v7.0.0-M4 /tmp/grails-verify
```

Please note that this script will perform steps that will require rebuilding the project & comparing the built artifacts
to the staged artifacts. Due to OS differences, this can result in reproducibility issues. For this reason, it's advised
to run these scripts from an environment similar to the GitHub actions environment. See the section
[Appendix: Verification from a Container](RELEASE.md#appendix-verification-from-a-container) for how to launch a container that closely resembles the GitHub actions
environment.

If manual verification is desired, the steps below can be followed.

### Manual Verification: Verify the KEYS file matches the file checked into grails-core

Use `etc/bin/verify-keys.sh` to verify the KEYS file. This script will download the KEYS file
from https://dist.apache.org/repos/dist/release/grails/KEYS and compare it to the KEYS file in this repository. If
verifying from a downloaded source distribution, and running the verification script from that distribution, it will
verify the one inside of the source distribution.

### Manual Verification: Download the Staged Artifacts

Use `etc/bin/download-release-artifacts.sh` to download the staged artifacts. This script will download the source distribution, wrapper binary distribution, and sdkman binary distribution. The distribution should come from [https://dist.apache.org/repos/dist/dev/grails/core/version](https://dist.apache.org/repos/dist/dev/grails/core).

### Manual Verification: Source Distribution Verification

The following are the source distribution artifacts:
   * `apache-grails-<version>-src.zip` - the source distribution
   * `apache-grails-<version>-src.zip.asc` - the generated signature of the source distribution
   * `apache-grails-<version>-src.zip.sha512` - the checksum to verify the source distribution

Use `etc/bin/verify-source-distribution.sh` to verify the source distribution. This script performs the following:

Verifies the source distribution checksum via the command:
   ```bash
   shasum -a 512 -c apache-grails-<version>-src.zip.sha512
   ```

Verifies the source distribution signature via the command:
   ```bash
    gpg --verify apache-grails-<version>-src.zip.asc apache-grails-<version>-src.zip
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

    gradle -p gradle-bootstrap

Further details on the building can be found in the [INSTALL](INSTALL) document.  Otherwise, run the `verify-reproducible.sh` shell script to compare the published jar files to a locally built version of them. 

If there are any jar file differences, confirm they are relevant by following the following steps: 
1. Extract the differing jar file using the `etc/bin/extract-build-artifact.sh <jarfilepath from diff.txt>`
2. In IntelliJ, under `etc/bin/results` there will now be a `firstArtifact` & `secondArtifact` folder. Select them both, right click, and select `Compared Directories`  

Please note that Grails is officially built on Linux so if there are differences they may be due to the OS platform.
There is a dockerfile checked into to assist building in an environment like GitHub actions. Please see the section
`Appendix: Verification from a Container` for more information.

### Manual Verification: Running RAT

The license audit can be triggered by running the gradle task `rat`. This will ensure that license requirements are met:

    ./gradlew rat

### Manual Verification: Binary Distribution Verification

Grails has 2 binary distributions:
   * `grailsw` - the Grails wrapper, which is a script that downloads the necessary jars to run Grails. This will exist inside of the generated applications, but can be optionally downloaded as a standalone binary distribution.
   * `grails` - the delegating CLI, which is a script that delegates to the Grails Forge CLI. This is the `sdkman` distribution.

#### Manual Verification: Verify Grails Wrapper Binary Distribution

The following are the Grails Wrapper distribution artifacts:
* `apache-grails-wrapper-<version>-bin.zip` - the wrapper distribution
* `apache-grails-wrapper-<version>-bin.zip.asc` - the generated signature of the wrapper distribution
* `apache-grails-wrapper-<version>-bin.zip.sha512` - the checksum to verify the wrapper distribution

Use `etc/bin/verify-wrapper-distribution.sh` to verify the wrapper distribution. This script performs the following:

Verifies the wrapper distribution checksum via the command:
   ```bash
   shasum -a 512 -c apache-grails-wrapper-<version>-bin.zip.sha512
   ```

Verifies the wrapper distribution signature via the command:
   ```bash
    gpg --verify apache-grails-wrapper-<version>-bin.zip.asc apache-grails-wrapper-<version>-bin.zip
   ```

Extracts the zip file and verifies the contents:
* Ensure the `LICENSE` & `NOTICE` files are present to ensure license compliance.

#### Manual Verification: Verify Grails Delegating CLI Binary Distribution

The following are the Grails distribution artifacts:
* `apache-grails-<version>-bin.zip` - the cli distribution that will be uploaded to sdkman
* `apache-grails-<version>-bin.zip.asc` - the generated signature of the cli distribution
* `apache-grails-<version>-bin.zip.sha512` - the checksum to verify the cli distribution

Use `etc/bin/verify-cli-distribution.sh` to verify the cli distribution. This script performs the following:

Verifies the cli distribution checksum via the command:
   ```bash
   shasum -a 512 -c apache-grails-<version>-bin.zip.sha512
   ```

Verifies the cli distribution signature via the command:
   ```bash
    gpg --verify apache-grails-<version>-bin.zip.asc apache-grails-<version>-bin.zip
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

After all artifacts are uploaded, verified, and tested, we can vote on the release.  As an ASF project, we follow the voting guidelines set forth by the [Apache Voting Process](https://www.apache.org/foundation/voting.html).  

### Apache Grails PMC

The release vote is conducted on the [Grails dev mailing list](https://lists.apache.org/list.html?dev@grails.apache.org). Only votes from members of the Apache Grails PMC are binding, but the community is welcome to participate to express their support.

The vote runs for a minimum of 72 hours.  
The vote email is automatically generated as part of the release process — see the `upload` job in the `Release`
workflow for details.

## 5. Releasing

After voting has passed, several steps must be completed to finalize the release. To make it easier, each of these steps
is encapsulated by the remaining jobs in the `Release` workflow. For reference, those steps are documented below. Either
way, the `release` job should be approved & reviewed before proceeding.

### Confirm Vote Passed

Confirm the Grails PMC votes passed with a +1 from at least 3 PMC members. The `release` job in the
`Release` workflow will remind you of these steps.

### Release the Staged Jar files

The `release` job in the `Release` workflow has a step entitled `🚀 Release JAR files - MANUAL`. You can release the jar
files by one of 2 ways:

1. On https://repository.apache.org/#stagingRepositories, the staged artifacts must be released by opening the `grails-core` staging repository and
   clicking the `Release` button. It took almost 2 hours for the initial ASF release to publish these jars to Maven
   Central.
2. Alternatively, the `release` job in the `Release` workflow will output an example command line to release the staging
   repository via the scripts checked into grails-core. Execute the script & set the placeholder values to release the
   jar files.

### Move the distributions from `dev` to `release`

On dist.apache.org, the staged source distribution & binary distributions must be moved from `https://dist.apache.org/repos/dist/dev/grails/` to `https://dist.apache.org/repos/dist/release/grails/`. Per ASF
infrastructure, this must be performed manually, and we are not allowed to automate it via a gated approval workflow.
Either move them via your SVN client or use the checked in script to perform these actions as your user.

The `release` job in the `Release` workflow has a step entitled `🚀 Move Distributions - MANUAL`. This step will output
an example call to the checked in script to move the distributions.

### Update ASF Reporter

After moving the distributions, you will receive an email from the ASF reporter. Click the link in the email to mark the
release as published or go to https://reporter.apache.org/addrelease.html?grails. The `release` job in the `Release` workflow has a step to remind you of this.

For example, if the release is out of core with version `7.0.0-M4`, then the release name with be `CORE-7.0.0-M4`. Enter
the date you moved the distribution artifacts and report the release.

### Deploy grails-forge so the release is accessible on start.grails.org

On the `grails-core` repository, using the release tag, deploy the grails-forge-web-netty docker container to Google Cloud Run using one of the GCP Deploy actions.

On https://start.grails.org, versions are listed in the following order:  RELEASE, NEXT, SNAPSHOT, PREV and PREV-SNAPSHOT.  Use the corresponding action to deploy to each location.

RELEASE - GA releases only

NEXT - Milestones and Release Candidate

SNAPSHOT - current or next version snapshot

PREV - previous release version

PREV-SNAPSHOT - previous version snapshot

The `release` job in the `Release` workflow has a step entitled `🚀 Deploy grails-forge - MANUAL` that will remind you of
this step.

### Publish `grails-core` documentation

Open the release workflow in `grails-core` and approve the `Publish Documentation` step. Wait until finished, and a
workflow should eventually kick off in `grails-doc` to publish to https://github.com/apache/grails-website/tree/asf-site-production/docs and https://grails.apache.org/docs/.

### Advertise the release via SDKMAN

In `grails-core`, kick off the step `Release to SDKMAN!` in the release workflow. This will cause SDKMAN to pull the new
version from Maven Central.

### Close out the `grails-core` release

The last step in the `grails-core` release workflow is to run the `Close Release` step.  This will create a merge branch for the original tag with version number and then open a PR to merge back into the next branch.  You will need to merge this PR into the branch after correcting any merge conflict.

### Update the `grails-static-website`

On the `grails-static-website` repository:

Run the release action (https://github.com/apache/grails-static-website/actions/workflows/release.yml) with the new version to update https://github.com/apache/grails-static-website/blob/HEAD/conf/releases.yml

Then run the publish action (https://github.com/apache/grails-static-website/actions/workflows/publish.yml) to update https://github.com/apache/grails-website/tree/asf-site-production/ and publish to https://grails.apache.org

Create a new `.md` file in the `/posts` directory announcing the release.  When the PR is merged it will deploy to https://grails.apache.org

### Flag release in `grails-core` as latest

Update the release in `grails-core` to be flagged as 'latest'

### Announce the release

Announcements should come from your apache email address (see https://infra.apache.org/committer-email.html) and have an
expected format. The announcement should be sent to `dev@grails.apache.org`, `dev@groovy.apache.org`, `users@grails.apache.org`, &
`announce@apache.org`. See the `close` job for a generated email.

# Rollback

In the event a staged artifact needs rolled back, kick off the `Release - Abort Release` workflow. This can only be done for steps prior to the VOTE.

This rollback will perform the following:

1. Drop the configured staging repository based on the release tag. (See `Appendix: Rollback a Nexus Artifacts (Jars)`
   for how to do this separately from the abort workflow)
2. Remove the staged artifacts
   from [https://dist.apache.org/repos/dist/dev/grails/core](https://dist.apache.org/repos/dist/dev/grails/core)
3. Cancel the GitHub Action `Release` (this may need to be manually cancelled depending on the workflow state)
4. Remove the GitHub Release from GitHub
5. Remove the Git tag

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

**macOS/Linux**
```bash
    docker build -t grails:testing -f etc/bin/Dockerfile . && docker run -it --rm -v $(pwd):/home/groovy/project -p 8080:8080 grails:testing bash
    cd grails-verify
    verify.sh <git-tag-of-release> .
```

**Windows**
```bash
    docker build -t grails:testing -f etc/bin/Dockerfile . && docker run -it --rm -v "%CD%:/home/groovy/project" -p 8080:8080 grails:testing bash
    cd grails-verify
    verify.sh <git-tag-of-release> .
```

Please note that the argument `-p 8080:8080` is used to expose the port 8080 of the container to the host machine's port 8080 (fromContainerPort:toHostPort). This allows you to access any running Grails application in the container from your host. If you have another application on port 8080, you can change the port mapping to avoid conflicts, e.g., `-p 8080:8081`. 

In the event that artifacts differ, simply copy them to your project directory and work on your local machine instead of the docker image: 

```bash
    cd ~/project
    rsync -av grails-verify/grails/etc/bin/results/ etc/bin/results/
```

# Appendix: Versioning

As Apache Grails is built on the Spring Framework(Spring Boot), we follow the Spring release versioning scheme and also release soon after a Spring Boot release.

The versioning scheme format:

- MAJOR.MINOR.PATCH[-MODIFIER]

Where:

- MAJOR: Indicates significant changes that may include breaking changes. If incremented, may involve a significant amount of work to upgrade.
- MINOR: Represents new features that are backward compatible. If incremented, should involve little to no work to upgrade.
- PATCH: Refers to backward-compatible bug fixes. If incremented, should involve no work.
- MODIFIER: is an optional modifier such that `<COUNT>` is an incremented 1-based number:
  - For milestones, we will use `-M<COUNT>`.
  - For release candidates, we will use `-RC<COUNT>`.
  - For snapshots, we will use `-SNAPSHOT`.
  - For final releases, there will be no modifier.

Snapshot versions are used for ongoing development and can be updated frequently.

Milestones are used for progress before the release is considered feature complete and Release Candidates are when the features are considered complete.  

These release type definitions differ from the official [ASF definitions](https://apache.org/legal/release-policy.html#release-types) in that our Milestone and Release Candidates go through the same release process as the final release and are official project releases for public use.

After an RC release has been vetted by the community, then a final release will follow without a modifier.

## Appendix: Rollback a Nexus Artifacts (Jars)

If a Nexus Repo needs to drop the staging jars, there exists a workflow `Release - Drop Nexus Staging` in `grails-core`.
The input of this workflow can be obtained by logging
into [repository.apache.org](https://repository.apache.org/index.html#stagingRepositories) and finding the staging
repository name to drop. 

# Appendix: Release Setup Requirements & History

Due to GitHub Actions being
considered [untrusted hardware](https://www.apache.org/legal/release-policy.html#owned-controlled-hardware), there are
several requirements the ASF Security Team places on ASF projects for projects to release via GitHub Actions. To
summarize the main requirement: a build must be verifiable so that code built in GitHub Actions can be compared to code
built elsewhere and proven to be equivalent. The most common way to ensure a build is verifiable is to ensure the build
is reproducible. This section documents the Grails Development Team's efforts to meet these requirements & the process
we followed to ensure we are compliant.

## Step 1: Ensuring we are reproducible

For Grails, significant work has been done to ensure the framework is reproducible. This work ensures it's documentation
artifacts, source artifacts, & binary artifacts are all verifiable. End Grails applications can also be made to be
reproducible as a result of this work. From our experience, reproducibility is first best addressed building on the same
machine and then secondarily building in GitHub actions and comparing the results.

The Grails Development Team created several shell scripts that test reproducibility. Each script is enumerated:

1. etc/bin/test-reproducible-build.sh - a script that targets making a single Gradle project (in a multi-project build)
   reproducible. Built results are stored in /etc/bin/results and ignored via `.gitignore`.
2. etc/bin/test-reproducible-builds.sh - a script that targets making the entire Grails multi-project build reproducible
   by building all projects twice, and comparing the results. Built results are stored in /etc/bin/results and ignored
   via `.gitignore`.
3. etc/bin/generate-build-artifact-hashes.groovy - a Groovy script that generates hashes for all built artifacts. This
   allows quicker comparison for which files differ.
4. etc/bin/extract-build-artifact.sh - a script that extracts a built jar file to allow for easier comparison of
   differences. Executing this script will create a `firstArtifact` & `secondArtifact` directory in `/etc/bin/results`
   that can be compared via IntelliJ (right click the 2 selected directories to compare). Since IntelliJ uses the Fern
   Decompiler, this allows for isolating why the files are different - if the decompiled code is the same, it means a
   timestamp or file ordering issue exists.

To test reproducibility locally, running etc/bin/test-reproducible-builds.sh will execute the necessary gradle commands
to build the three gradle projects Grails uses. The artifacts are then saved off, and built again. Finally, the hashes
are generated to ensure the artifacts are the same.

Some common gotchas with Java build reproducibility problems:

1. Most tools support a `SOURCE_DATE_EPOCH` environment variable that can be set to a fixed time to ensure timestamps
   are reproducible. Grails sets this based on the last Git Commit and it's release process preserves the date chosen.
2. The date set by `SOURCE_DATE_EPOCH` will need to be passed to various Gradle configuration to ensure the same date is
   used. Grails performs this configuration in it's root `build.gradle` file.
3. Properties files are not reproducible because they write out the current time. Grails publishes a utility to mutate
   property files so they're reproducible. As part of our build process, we mutate any property file we generate and do
   not ship property files with dynamic timestamps.
4. Gradle builds are not reproducible by default because Gradle does not guarantee file ordering. Grails configures all
   tasks that create archive files to ensure file ordering is reproducible. This configuration can be found in
   `gradle/java-config.gradle`
5. Grails makes heavy use of Groovy AST transforms. These transforms often lookup methods on a class, and the methods
   returned by Java will vary by operating system. Any code using reflection to lookup methods or fields must sort the
   results to ensure reproducibility.
6. Javadoc / Groovydoc will by default write out dates in it's documentation headers. Grails configures these settings
   in `gradle/java-config.gradle`.

After a build is made reproducible on the same machine, the next step is to ensure it's reproducible in GitHub actions.
OS differences will exist and even exist between different Docker Runtimes. To help test reproducible builds, we found
it best to create a docker container that closely resembles the GitHub actions environment. This docker file is checked
in under bin. The docker image will mount the local project directory into the container so that builds can be performed
in the container, but compared on the host machine. To run our docker file, the process is documented in the
section [Appendix: Verification from a Container](#appendix-verification-from-a-container).

## Step 2: Obtaining Secrets for GitHub Actions

After the build has been shown to be reproducible & a document similar to this file covering the expected release steps
has been created, the next step is to obtain the necessary secrets to sign & stage artifacts. The Grails Development
Team worked with the ASF Security Team to obtain the necessary secrets by creating an infrastructure issue.

Secrets we use for our builds include:

1. GPG Signing Key - secret name: `GPG_PRIVATE_KEY`
   ASF Infra needs to generate a GPG Key that will be used to sign artifacts. As part of that key setup, infra should
   ask for another key so a revocation cert can be established in the event the key needs to be revoked too. A PMC
   member used their key for the Grails key, but multiple can be used if asked. The public key will also need added to
   the project's KEYS file so external users can verify artifacts.

2. GPG Signing Key ID - secret name: `GPG_KEY_ID`
   The ID of the GPG key that was created. This is used to tell GPG which key to use for signing. This can be extracted
   from the GPG key with the following command: `gpg --list-keys --keyid-format short`. We use Gradle to sign our
   artifacts, and https://docs.gradle.org/current/userguide/signing_plugin.html says it's the last 8 digits of the key,
   but I've found the previously mentioned GPG command more reliable.

3. Nexus Credentials to Publish Snapshot Artifacts
   Credentials to publish to https://repository.apache.org for snapshots need provided. We had these credentials placed
   in the following secrets:
    * `NEXUS_USERNAME` - the username
    * `NEXUS_PASSWORD` - the password

4. Nexus Credentials to Stage Release Artifacts
   Credentials to publish to https://repository.apache.org for staging need provided. We had these credentials placed in
   the following secrets:
    * `NEXUS_STAGE_DEPLOYER_USER` - the username
    * `NEXUS_STAGE_DEPLOYER_PW` - the password

5. The Nexus Staging Profile ID
   Grails uses the nexus-publish plugin in it's own publishing plugin (grails-publish). This plugin requires the staging
   profile ID. We added this ID as a secret named `NEXUS_STAGING_PROFILE_ID`. This ID can be found by logging
   into https://repository.apache.org, clicking on "Staging Profiles" and then clicking on the profile used to stage
   artifacts. The ID is in the URL at the end.

6. SVN Credentials
   The built artifacts will need uploaded to the https://dist.apache.org SVN repository. Infrastructure will provide
   credentials that can publish to `dev` only. The credentials to do so were placed in the following secrets:
    * `SVC_DIST_GRAILS_USERNAME` - the SVN username
    * `SVC_DIST_GRAILS_PASSWORD` - the SVN password

7. Develocity Access Key - secret name: `GRAILS_DEVELOCITY_ACCESS_KEY `
   Grails uses Gradle's Develocity with it's Gradle builds. This secret contains the access key to publish / use the
   cache in Develocity.

Note: A caution about secrets, GitHub action secrets will often need entered with a new line for secrets like the GPG
key. If errors are encountered in the build after Infrastructure setups secrets, it's likely because the new line after
the secret value was not added.

## Step 3: Workflow Setup

Once the secrets have been added to the GitHub repository, the next step is to create the GitHub workflows. Grails uses
the following workflows:

1. `codeql.yml` - Configures Code Quality Scans
2. `codestyle.yml` - Runs checkstyle on our build to ensure code style requirements are met against any submitted code.
3. `forge-*.yml` - Workflows to build & publish our public App Generation website.
4. `gradle.yml` - Our main CI workflow & snapshot publishing.
5. `groovy-joint-workflow.yml` - A workflow that runs with the latest snapshot of Groovy to ensure we are forward
   compatible and give the Groovy team early feedback.
6. `rat.yml` - A workflow that runs the Apache RAT license audit to ensure license compliance. We use the Gradle plugin
   org.nosphere.apache.rat` to perform the audit.
7. `release.yml` - The main release workflow that performs the release steps documented earlier in this document.
8. `release-abort.yml` - A workflow that can be used to abort a release prior to voting. See the
   section [Rollback](#rollback) for more details.
9. `release-drop-staging.yml` - A workflow that can be used to drop a staging repository. We rarely use this now that we
   have the `release-abort.yml` workflow.
10. `release-notes.yml` - A workflow that configures Release Drafter so that release notes are automatically generated
    as pull requests are merged.

The key workflows above are: `gradle.yml`, `release.yml`, and `release-abort.yml`.

The workflows have been gradually made to be more generic, but they still contain specific folder names / project names.
If you wish to fork these workflows, please ensure you update project specific information.

## Step 4 - Next Steps

After the workflows are setup, they should be tested per this Release document. GitHub releases can be deleted and
recreated as needed to test the release process up to voting. Use the `Release Abort` workflow to rollback as needed.
