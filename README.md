# Grails
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.grails.org/scans)
[![Java CI](https://github.com/grails/grails-core/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/grails/grails-core/actions/workflows/gradle.yml)
[![Groovy Joint Validation Build](https://github.com/grails/grails-core/actions/workflows/groovy-joint-workflow.yml/badge.svg?event=push)](https://github.com/grails/grails-core/actions/workflows/groovy-joint-workflow.yml)

### Getting help
Join the discussions on [Slack](https://slack.grails.org/)  
Ask questions on [Stack Overflow](https://stackoverflow.com/questions/tagged/grails)

## What is Grails?
[Grails](https://grails.org/) is a framework used to build web applications with the [Groovy](https://groovy-lang.org/) programming language. The core framework is very extensible and there are numerous [plugins](https://plugins.grails.org/) available that provide easy integration of add-on features.

[Commercial support](https://grails.org/support.html) options are available.

## Getting Started

You need a Java Development Kit (JDK) installed, but it is not necessary to install Groovy because it's bundled with the Grails distribution.

To install Grails, visit https://grails.org/download.html and download the version you would like to use. Set a `GRAILS_HOME` environment variable to point to the root of the extracted download and add `GRAILS_HOME/bin` to your executable `PATH`. Then in a shell, type the following:

	grails create-app sampleapp
	cd sampleapp
	grails run-app

To build Grails, clone this GitHub repository and execute the install Gradle target:

    git clone https://github.com/grails/grails-core.git
    cd grails-core
    ./gradlew install

If you encounter out of memory errors when trying to run the install target, try adjusting Gradle build settings. For example:

    export GRADLE_OPTS="-Xmx2G -Xms2G -XX:NewSize=512m -XX:MaxNewSize=512m"

## Performing a Release

See [RELEASE.md](RELEASE.md).

## License

Grails and Groovy are licensed under the terms of the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).

***

YourKit is kindly supporting Grails open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
[YourKit Java Profiler](https://www.yourkit.com/java/profiler/features/) and
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/features/).

