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

# Apache Grails (Incubating)

[![Documentation](https://img.shields.io/badge/Documentation-595959)](https://docs.grails.org)
[![Develocity](https://img.shields.io/badge/Develocity-06A0CE?logo=Gradle&labelColor=06A0CE)](https://ge.grails.org/scans)
[![CI](https://github.com/apache/grails-core/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/apache/grails-core/actions/workflows/gradle.yml)
[![Groovy Joint Validation Build](https://github.com/apache/grails-core/actions/workflows/groovy-joint-workflow.yml/badge.svg?event=push)](https://github.com/apache/grails-core/actions/workflows/groovy-joint-workflow.yml)
[![Users Mailing List](https://img.shields.io/badge/Users_Mailing_List-feb571)](https://lists.apache.org/list.html?users@grails.apache.org)
[![Dev Mailing List](https://img.shields.io/badge/Dev_Mailing_List-feb571)](https://lists.apache.org/list.html?dev@grails.apache.org)
[![Slack](https://img.shields.io/badge/Join_Slack-e01d5a)](https://slack.grails.org/)
[![GitHub Discussions](https://img.shields.io/github/discussions/apache/grails-core)](https://github.com/apache/grails-core/discussions)

## Introduction

[Apache Grails](https://grails.org/) is a framework used to build web applications with
the [Apache Groovy](https://groovy-lang.org/) programming language. Releases prior to 7.0.0 were outside of the Apache
Software Foundation. The core framework is very extensible and there are numerous [plugins](https://plugins.grails.org/)
available that provide easy integration of add-on features. To assist in getting started, various Application generators
exist and are provided by the Apache Grails team.

## Using the source distribution
Please see the [INSTALL](INSTALL) document for instructions on how to build, use, and run Apache Grails CLIs from the source distribution. 

## Getting help

- Check the [Documentation](https://docs.grails.org/) for your preferred Apache Grails version.
- Check for a [Grails Guide](https://github.com/grails-guides/).
- Ask questions on the [Grails User Mailing List](https://lists.apache.org/list.html?users@grails.apache.org)
- Submit an issue: [Grails Issues](https://github.com/apache/grails-core/issues)
- Join the discussions on [Slack](https://slack.grails.org/)

Please note that the Apache Software Foundation does not offer commercial support for Apache Grails or related applications.

[Commercial support](https://grails.org/support.html) options are available.  The products and services listed at this link are provided for information use only to our users. The Apache Grails Project does not endorse or recommend any of the products or services.

## Application Generation

The only requirement for Apache Grails is the Java Development Kit (JDK). Once a JDK is installed there are many ways to
get started. The preferred way to get started is to use [Grails Forge](https://start.grails.org). Alternatively, offline
CLI applications exist to assist in Application generation. Instructions for them follow.

### Wrapper

The Apache Grails Wrapper is a tiny distribution (25KB) that can manage larger sized CLIs for Grails. It consists of a
`grailsw` shell script, a `grailsw.bat` batch script, and the jar file `grails-wrapper.jar`. It can be downloaded from
the latest [GitHub Release](https://github.com/apache/grails-core/releases) page starting with 7.0.0-M4 & it is included
in any created project. The wrapper is used to either create an Apache Grails Application or to run commands in an
existing Grails Application directory. The wrapper is generally meant to be forward compatible and downloads the Apache
Grails CLIs to the directory `$HOME/.grails/wrapper`.

#### Wrapper - Creating a Apache Grails Application

To create an Apache Grails Application with the wrapper follow these steps:

1. Extract the wrapper to your preferred location.
2. Set the environment variable `PREFERRED_GRAILS_VERSION` to the preferred version.
3. Run the wrapper command `grailsw -t forge create-app` to create a new Apache Grails Application.

Please note, that the wrapper supports either the legacy `Apache Grails Shell` or the newer `Apache Grails Forge` CLI.
For more detailed information about it, see the [documentation](https://docs.grails.org/snapshot/index.html).

#### Wrapper - Running Commands inside a Apache Grails Project

For running commands, the Grails Wrapper will always pull the Apache Grails version from `gradle.properties` and ignore
any environment variables. Type `grailsw -t shell help` to see the available commands. For more detailed information
about it, see the [documentation](https://docs.grails.org/snapshot/index.html).

### grails-shell-cli

The legacy Apache Grails CLI is the command line interface that IntelliJ uses to interact with existing Apache Grails
Applications. It can be used to generate applications or it can be used to interact with existing ones. To use it,
download it to your preferred location from the [grails-forge](https://github.com/apache/grails-forge/releases) release
page. You can use the command `./grails-shell-cli help` to see what's possible.

### grails-forge-cli

The newer Apache Grails CLI is available from the [grails-forge](https://github.com/apache/grails-forge/releases)
release page. You can use the command `./grails-forge-cli --help` to see what's possible.

### SDKMAN

If managing multiple, local copies of the Grails CLI, it is recommended to use [SDKMAN!](https://sdkman.io/). Assuming
SDKMAN is installed, this command would install the 7.0.0-M4 version:

     sdk install grails 7.0.0-M4

Apache Grails versions installed via SDKMAN! include the following commands `grails`, `grails-shell-cli`, &
`grails-forge-cli`. The grails command simply delegates to forge or the legacy shell. For further information on SDKMAN,
please see their [website](https://sdkman.io/).

## Starting your Grails Application

Once your Apache Grails Application is created, you can start it with the command:

    ./gradlew bootRun

For further information, please consult the [documentation](https://docs.grails.org).

## Licensing

Apache Grails is licensed under the Apache License, Version 2.0. For details, see
the [LICENSE](./LICENSE).

## Performing a Release

See [RELEASE.md](RELEASE.md).

## Friends of Apache Grails Open Collective
As an independent initiative, community members have set up an open collective for Apache Grails:

https://opencollective.com/friends-of-grails

This initiative is designed to complement the Apache project and the many contributions we get from our great community and supporters.

