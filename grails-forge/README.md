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
