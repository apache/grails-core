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

# Grails Application Forge

[![Maven Central](https://img.shields.io/maven-central/v/org.apache.grails.forge/grails-forge-core.svg?label=Maven%20Central)](https://search.maven.org/artifact/org.apache.grails.forge/grails-forge-core)

Generates Grails applications.

Forge is itself built with Grails: `grails-forge-core` is a Grails plugin holding the feature catalogue, the
project generator and the templates, `grails-forge-web` is the Grails web application serving the hosted
generator's HTTP API, and `grails-forge-cli` is a Grails application without a web server that runs the
same generator from the command line. Both applications consume the framework the way a generated
application does, by Maven coordinates, so building Forge is also the first use of the Grails that was just
built. The `grails` launcher (`grails-cli`) is the distribution of the CLI: the `grails` command and the jar the Grails wrapper downloads.

The files the generator writes are rendered from GSPs under `grails-app/views/forge` of `grails-forge-core`
(and, for the CLI's code generation commands, of `grails-forge-cli`). Each page declares the model it is
rendered with and is compiled with its module, so a page that does not type check fails the build. The pages
are rendered by the GSP engine alone, without a web request, and declare `trimLogicLines="true"`: a line
holding only a scriptlet, a directive or a comment writes nothing, so the line breaks of a generated file are
the ones its page shows as text. A feature renders a page through `GspView.of(uri, model)`, written as a file
of the generated project by `GspTemplate`.

## Building

Forge is a separate Gradle build next to the framework build, run with its own wrapper from this directory:

```bash
$ ./gradlew build
```

The framework it consumes is substituted from the root build (`includeBuild('..')`), so a change to the
framework is visible here without publishing. Specs that build generated applications publish the framework to
`build/local-maven` first, which is why a first `test` run takes a while. That publish holds what a generated
build resolves: the jars, the POMs and the Gradle module metadata, without the sources and javadoc jars and
the checksums of a release (pass `-PcompleteTestRepository=true` for those). The jars are the jars of a release.
To shorten the publish further, `-PtestRepositoryWithoutSbom=true` (or the environment variable
`ORG_GRADLE_PROJECT_testRepositoryWithoutSbom=true`) leaves the SBOM out of them, which spares the CycloneDX
task of every module (156 tasks, about a tenth of the summed task time of the publish from a clean tree; little wall
time on a machine with many cores, more on one with few). That is opt-in because the SBOM is packed into the jar: the
specs then run against jars that differ from the released ones (no `META-INF/sbom.json`, no `Sbom-Location`
manifest attribute), and the license check the SBOM tasks make does not run. The property reaches the
included framework builds from the Forge command line, and it is ignored, with a message, by any build that
is asked for more than the publish for the tests. To start the hosted generator locally
run `./gradlew :grails-forge-web:bootRun`; to run the CLI from this build, `./gradlew :grails-cli:installDist`
and use `grails-cli/build/install/apache-grails-bin/bin/grails`.

Use `-PskipForgeTests` to skip Forge tests and their local-Maven publication prerequisites.

### Running the specs without building the framework

Because the framework is an included build, every run of the specs first brings its modules up to date, and
the specs that build generated applications (`grails-forge-cli`, `test-core`) publish all of them. When the
work is on Forge alone that is time spent on nothing that changed. `-PprebuiltTestRepository` says that the
framework is already published and is not to be built:

```bash
# once, and again whenever the framework changes (from the top directory of the checkout)
$ ./gradlew publishAllPublicationsToTestCaseMavenRepoRepository
$ (cd grails-gradle && ./gradlew publishAllPublicationsToTestCaseMavenRepoRepository)

# then, as often as you like (from grails-forge)
$ ./gradlew :grails-forge-cli:test --tests '*CreateControllerCommandSpec' -PprebuiltTestRepository
```

With the property the framework build is not part of the Forge build at all: Forge's own modules resolve the
framework from `build/local-maven` as the generated applications do, the specs are given that repository as it
is (`GRAILS_REPO_URL`), and no task of the framework or Grails Gradle plugin builds runs. Forge applies the
published Gradle plugins too; only the shared `build-logic` remains an included build. The POMs of
the BOMs and the Grails wrapper, which Forge otherwise takes from the build directories of the framework, are
read from the copies the publish leaves under `build/local-maven/grails-test-repository/framework`. A run of
the specs without the property publishes the same repository, so it can also be the "once".

The value may also be the path of a repository (`-PprebuiltTestRepository=/path/to/local-maven`; a relative
path is taken from the directory Gradle is started in), which is how a workflow uses one that another job
published; `false` is as if the property were not given. An empty value or `true` selects the default path.
`ORG_GRADLE_PROJECT_prebuiltTestRepository` accepts the same values. The property takes precedence over an
inherited `GRAILS_REPO_URL`. Resolution is exclusive for framework coordinates, so a missing module cannot
silently come from a remote snapshot repository.

The price is that the jars no longer follow the sources, so the repository is checked before Forge's
projects are configured, and the build fails, with the reason and the commands above, when the repository

- is not there, or holds no `grails-bom` or Grails Gradle plugin of the version of the build (the message
  names the versions it does hold and when they were published);
- has no record of a complete publish of both builds: each of the two publish tasks above ends by writing
  `build/local-maven/grails-test-repository/<build>.properties` (version, time, call sites, sources), and
  the publish of a single module invalidates the previous record before writing anything, and a failed
  publish cannot leave the old record valid;
- holds a framework of the other kind of call site: a repository published with `-PgrailsIndy=false` is
  used with `-PgrailsIndy=false` and no other, because the Grails plugin modules are different jars then;
- was published from other framework sources than are checked out now. The record holds, for every entry of
  the top directory, a hash of its sorted file paths and Git blob identifiers, including staged, unstaged,
  new and deleted files. Git normalizes checkout line endings; absolute paths, file permissions, commit IDs
  and timestamps do not enter the fingerprint. Committing identical files does not invalidate it, and a
  shallow detached CI checkout compares the same way as a developer's checkout. The message names the
  entries that differ and how old the repository is;
- targets a newer Java release than the JVM running Forge. The normal Java 21 artifacts work on Java 25 too.

The last check is what keeps the specs from passing against old jars, and it is a failure and not a warning
for that reason. It does not ask anything of the work the property is for: `grails-forge`, `end-to-end`,
`grails-test-examples`, `grails-doc`, `.github` and the Markdown files of the top directory are not sources
of the framework, so changes there, committed or not, never make the repository old. Any other change
does, also one to the tests of a framework module, and then the repository is published again (an
incremental publish is quick) or the specs are run without the property.
`-PallowStaleTestRepository=true` turns the failure into a warning that every test task using the repository
repeats, for the case where you know the change does not matter. Where Git cannot say what the sources are
(a source release), the comparison is left out and the build says so.

`compilePackPages` still runs when Forge's own inputs change: it compiles Forge's bundled templates using
the framework jars in the repository. It does not require framework source compilation. The copied wrapper
script has its executable permission restored when it comes from a downloaded workflow artifact.

#### CI repositories

When the CI policy runs tests, the CI workflow publishes one Java 21 repository per call-site variant,
retains each artifact for one day, and downloads the matching repository in the three Linux Forge lanes
and the Windows lane. Builds that skip tests keep the ordinary composite: publishing the whole framework
would cost more than compiling the subset Forge uses. The Java 25
lane uses the classic Java 21 repository but builds Forge and generated applications on Java 25. The
current Micronaut test only generates files; compiling a generated Micronaut application would additionally
require the Java 25-only modules, which a baseline publish omits.

Coverage reuses the framework compiled by `coverage-core`: after its tests it publishes a repository for
`coverage-forge`. JaCoCo instruments classes at test runtime; Forge's report includes Forge's own source
sets and execution data, so no instrumented framework jars or framework execution data need transferring.
This saves a framework compilation but makes Forge coverage wait for core coverage. End-to-end has its own
workflow run and keeps its own publication, rather than depending on artifact lookup or polling across runs.
The default local build remains composite-based and publishes the repository before generated-app specs.

## Feature packs of Forge

A feature of the framework that is only dependencies, settings and files is a feature pack that Forge has in
itself, in the format a plugin uses for its own pack (see "Adding a Feature to Application Creation" in the
Grails guide). Each is a directory under
`grails-forge-core/src/main/resources/META-INF/grails-forge-bundled`, named for its feature, with a
`feature.yml` and the files it names under `templates`; the file `packs` beside the directories names the
packs, one on a line. What a pack gives is held by a file under
`grails-forge-core/src/test/resources/golden/bundled` (`BundledPackGoldenSpec`).

A file of a pack whose name ends with `.gsp` is a template. The templates of these packs are compiled when
Forge is built, not each time the command line generates an application: the task
`:grails-forge-core:compilePackPages` compiles every template of every pack `packs` names, with the code that
compiles a template when Forge runs, and the jar of `grails-forge-core` (and with it the jar of the `grails`
command) has the classes, in the package `org.apache.grails.forge.core.pack.pages`, which holds nothing else, each
named `PackPage_` and a SHA-256 of the feature and the text of the template, and
the index `META-INF/grails-forge-pages/index`. Whoever adds or changes a template has nothing to do for
this: the next build compiles it, a template that does not compile fails the build, and
`PrecompiledPackPagesSpec` fails when a template has no compiled page or writes anything else from it than
when it is compiled where it is rendered.

A compiled page is used only for the template it was compiled from, as it is parsed in the run. The
template of a pack that is not of Forge (of a plugin, a directory or a repository), a template whose text
is not the one that was compiled, and every template when the index or a class is missing are compiled when
they are rendered, as before, and nothing says so. To see what the compiled pages have to do with a problem
or with the time a command takes, run it with all templates compiled where they are rendered:
`JAVA_OPTS=-Dgrails.forge.pack.precompiled=false grails create-app ...`.

## Reproducibility checks for contributors

Generation order is phase then feature name; equal-order build plugins retain declaration order,
dependencies are sorted by order/configuration/group/artifact with declaration order for ties, and YAML
keys and baseline paths are canonicalized. Keep pack sources in UTF-8 with LF checkout endings.
Templates must use their model rather than the clock, random values or machine-specific paths.
The guide's "What Is the Same Every Time" section describes the public guarantees and resolution inputs.

`GenerationReproducibilitySpec` compares complete project trees and their records through the CLI in
fresh English, Turkish and German JVMs with different directories, time zones, encodings and line
separators. It covers feature permutations, successive additions, regeneration, migration and listings
without compiling each generated project. `GrailsPluginForgePackReproducibilitySpec` in the separate
`grails-gradle` build compares a plugin pack jar and published metadata from relocated TestKit builds
with different source order, modes and timestamps. `PrecompiledPackPagesSpec` compares compiled page
bytes and their index across compilations, and launcher tests check that those pages reach the shadow jar.
`ShadowJarMetadataSpec` checks both the intermediate and published shadow jars for timestamp-free
Groovy extension metadata; both packaging stages use the same reproducible transformer.

For release artifact verification, follow `RELEASE.md` with the release's exact JDK and
`SOURCE_DATE_EPOCH`. Developer and release SBOM timestamps deliberately differ; compare builds within
one mode with identical dependency bytes. A passing generation comparison alone does not establish
that every framework archive is byte-for-byte reproducible across operating systems.

## Installation

The CLI is a Java application distributed with Grails for Windows, Linux and macOS. Download it from the [releases page](https://github.com/apache/grails-core/releases). For installation see the [Grails documentation](https://grails.apache.org/docs/latest/guide/index.html#buildCLI).

The Forge CLI defaults to classpath application configuration (`spring.config.location=classpath:/`), so local `application.yml` and `config/` files are not loaded into the generator's context.

If you prefer not to install an application to create Grails applications you can do so with `curl` directly from the API:

```bash
$ curl 'https://latest.grails.org/demo.zip' -o demo.zip
$ unzip demo.zip -d demo
$ cd demo
$ ./gradlew run
```

Run `curl https://latest.grails.org/` for more information on how to use the API.

## UI

If you prefer a browser based user interface you can visit [Grails Forge](https://start.grails.org).

The user interface is [written in React](https://github.com/apache/grails-forge-ui/tree/main/app/launch) and is a static single page application. It lives at https://start.grails.org and calls the Forge APIs at `latest.grails.org`, `snapshot.grails.org`, `next.grails.org`, `next-snapshot.grails.org`, `prev.grails.org`, `prev-snapshot.grails.org`, and `older.grails.org`.

## API

The hosted generator answers `GET /` with a plain-text usage summary. Its OpenAPI description is derived from the application itself by the `grails-openapi` module, from the URL mappings and the controllers of `grails-forge-web` and the OpenAPI annotations they carry, and is served by springdoc with Swagger UI beside it.

API documentation for the production instance:

* [Swagger UI](https://latest.grails.org/swagger-ui/index.html)
* [OpenAPI document](https://latest.grails.org/v3/api-docs)
* [Plain-text usage](https://latest.grails.org/)

API documentation for the snapshot / development instance:

* [Swagger UI](https://snapshot.grails.org/swagger-ui/index.html)
* [OpenAPI document](https://snapshot.grails.org/v3/api-docs)
* [Plain-text usage](https://snapshot.grails.org/)

## Snapshots and Releases

Releases are published to SDKMan via the Release action on [Github Actions](https://github.com/apache/grails-core/actions).

A release is performed with the following steps:

* [Publish the draft release](https://github.com/apache/grails-core/releases). There should be already a draft release created, edit and publish it. The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/apache/grails-core/actions?query=workflow%3ARelease) to check it passed successfully.
* Celebrate!

## Distribution to AWS Elastic Beanstalk

The seven Forge API slots run on AWS Elastic Beanstalk behind one shared application load balancer. The UI remains at `https://start.grails.org`.

The API hosts are `latest.grails.org`, `snapshot.grails.org`, `next.grails.org`, `next-snapshot.grails.org`, `prev.grails.org`, `prev-snapshot.grails.org`, and `older.grails.org`. GitHub Actions authenticates to AWS through OIDC using the repository variable `AWS_FORGE_DEPLOY_ROLE_ARN`; it does not use static AWS access keys.

Deployments package the Grails 8 `grails-forge-web` Tomcat `bootJar` as `app.jar` in the `grails-forge-web-aws.zip` source bundle. The bundle also contains `Procfile` and `start.sh`. Analytics is not deployed.

For deployment, rollback, monitoring, and GCP decommissioning, see [AWS Elastic Beanstalk Deployment Runbook](docs/aws-elastic-beanstalk.md).
