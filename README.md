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

# Grails
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.grails.org/scans)
[![Java CI](https://github.com/apache/grails-core/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/apache/grails-core/actions/workflows/gradle.yml)
[![Groovy Joint Validation Build](https://github.com/apache/grails-core/actions/workflows/groovy-joint-workflow.yml/badge.svg?event=push)](https://github.com/apache/grails-core/actions/workflows/groovy-joint-workflow.yml)

### Getting help
Join the discussions on [Slack](https://slack.grails.org/)  
Ask questions on [Stack Overflow](https://stackoverflow.com/questions/tagged/grails)

## What is Grails?
[Grails](https://grails.org/) is a framework used to build web applications with the [Groovy](https://groovy-lang.org/) programming language. The core framework is very extensible and there are numerous [plugins](https://plugins.grails.org/) available that provide easy integration of add-on features.

[Commercial support](https://grails.org/support.html) options are available.

## Getting Started

You need a Java Development Kit (JDK) installed, but it is not necessary to install Groovy because it's bundled with the Grails distribution.

It's recommended to use the [SDKMAN!](https://sdkman.io/) tool to install & manage multiple Grails version.
Alternatively, visit https://grails.org/download.html for other install options. If downloading the binary, the only
requirement is it be added to your path.

To create your first Grails Application, you can use [Grails Forge](https://start.grails.org) or you can use the command
line. For legacy reasons, there exists 2 ways to generate Grails applications.  `Grails Forge` and `Grails Shell`. The
grails binary allows you to use either to generate an app.

For example purposes, using the legacy shell:

	grails create-app sampleapp
	cd sampleapp
	./gradlew bootRun

To build Grails, clone this GitHub repository and execute the build Gradle target:

    git clone https://github.com/apache/grails-core.git
    cd grails-core
    ./gradlew build -PskipTests

If you encounter out of memory errors when trying to run the build, try adjusting Gradle build settings. For example:

    export GRADLE_OPTS="-Xmx2G -Xms2G -XX:NewSize=512m -XX:MaxNewSize=512m"

Please note that a valid container runtime is required to run Grails Tests. The example above omits the tests so the
build will pass.

## Performing a Release

See [RELEASE.md](RELEASE.md).

## Friends of Apache Grails Open Collective
As an independent initiative, community members have set up an open collective for Apache Grails:

https://opencollective.com/friends-of-grails

This initiative is designed to complement the Apache project and the many contributions we get from our great community and supporters.


## License

Grails and Groovy are licensed under the terms of the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).

***

YourKit is kindly supporting Grails open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
[YourKit Java Profiler](https://www.yourkit.com/java/profiler/features/) and
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/features/).

