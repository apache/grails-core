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

## Installation

The CLI application comes in various flavours from a universal Java applications to native applications for Windows, Linux and OS X. These are available for direct download on the [releases page](https://github.com/apache/grails-core/releases). For installation see the [Grails documentation](https://grails.apache.org/docs/latest/guide/index.html#buildCLI).

If you prefer not to install an application to create Grails applications you can do so with `curl` directly from the API:

```bash
$ curl 'https://latest.grails.org/demo.zip' -o demo.zip
$ unzip demo.zip -d demo
$ cd demo
$ ./gradlew run
```

Run `curl https://latest.grails.org/` for more information on how to use the API or see the API documentation referenced below.

## UI

If you prefer a browser based user interface you can visit [Grails Forge](https://start.grails.org).

The user interface is [written in React](https://github.com/apache/grails-forge-ui/tree/main/app/launch) and is a static single page application. It lives at https://start.grails.org and calls the Forge APIs at `latest.grails.org`, `snapshot.grails.org`, `next.grails.org`, `next-snapshot.grails.org`, `prev.grails.org`, `prev-snapshot.grails.org`, and `older.grails.org`.

## API

API documentation for the production instance can be found at:

* [Swagger / OpenAPI Doc](https://latest.grails.org/swagger-ui/index.html)
* [RAPI Doc](https://latest.grails.org/rapidoc/index.html)

API documentation for the snapshot / development instance can be found at:

* [Swagger / OpenAPI Doc](https://snapshot.grails.org/swagger-ui/index.html)
* [RAPI Doc](https://snapshot.grails.org/rapidoc/index.html)

## Snapshots and Releases

Releases are published to SDKMan via the Release action on [Github Actions](https://github.com/apache/grails-core/actions).

A release is performed with the following steps:

* [Publish the draft release](https://github.com/apache/grails-core/releases). There should be already a draft release created, edit and publish it. The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/apache/grails-core/actions?query=workflow%3ARelease) to check it passed successfully.
* Celebrate!

## Architecture

![Grails Forge Architecture](grailsforgearchitecture.jpeg)

## Distribution to AWS Elastic Beanstalk

The seven Forge API slots run on AWS Elastic Beanstalk behind one shared application load balancer. The UI remains at `https://start.grails.org`.

The API hosts are `latest.grails.org`, `snapshot.grails.org`, `next.grails.org`, `next-snapshot.grails.org`, `prev.grails.org`, `prev-snapshot.grails.org`, and `older.grails.org`. GitHub Actions authenticates to AWS through OIDC using the repository variable `AWS_FORGE_DEPLOY_ROLE_ARN`; it does not use static AWS access keys.

Deployments package the normal Forge executable JAR in a ZIP source bundle. Analytics is not deployed. When its endpoint and analytics environment variables are absent, reporting is disabled without affecting application generation. The unused server-side GitHub create / OAuth integration is also omitted.

For deployment, rollback, monitoring, and GCP decommissioning, see [AWS Elastic Beanstalk Deployment Runbook](docs/aws-elastic-beanstalk.md).


