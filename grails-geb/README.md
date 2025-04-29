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

# Grails Geb Plugin

[![Maven Central](https://img.shields.io/maven-central/v/org.grails.plugins/geb.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.grails.plugins/geb)
[![Java CI](https://github.com/grails/geb/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/grails/geb/actions/workflows/gradle.yml)

## Geb Functional Testing for the Grails® framework

This plugin integrates [Geb](https://www.gebish.org) with [Grails](https://www.grails.org) to make it easy to write functional tests for your Grails applications.

## Examples

If you are looking for examples on how to write Geb tests, check:

[Geb/Grails example project](https://github.com/grails-samples/geb-example-grails) or [Grails functional test suite](https://github.com/grails/grails-functional-tests) where Geb tests are used extensively.
For further reference please see the [Geb documentation](https://www.gebish.org).

## Usage

To use the plugin, add the following dependencies to your `build.gradle` file:
```groovy
dependencies {
    
    // This is only needed to if you want to use the
    // create-functional-test command (see below)
    implementation 'org.apache.grails:grails-geb'
    
    // This is needed to compile and run the tests
    integrationTestImplementation testFixtures('org.apache.grails:grails-geb')
}
```

To get started, you can use the `create-functional-test` command to generate a new functional test using Geb:

```console
./grailsw create-functional-test com.example.MyFunctionalSpec
```

This will create a new Geb test named `MyFunctionalSpec` in the `src/integration-test/groovy/com/example` directory.

There are two ways to use this plugin. Either extend your test classes with the `ContainerGebSpec` class or with the `GebSpec` class.

### ContainerGebSpec (recommended)

By extending your test classes with `ContainerGebSpec`, your tests will automatically use a containerized browser using [Testcontainers](https://java.testcontainers.org/).
This requires a [compatible container runtime](https://java.testcontainers.org/supported_docker_environment/) to be installed, such as:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [OrbStack](https://orbstack.dev/) - macOS only
- [Rancher Desktop](https://rancherdesktop.io/)
- [podman desktop](https://podman-desktop.io/)
- [Colima](https://github.com/abiosoft/colima) - macOS and Linux

If you choose to use the `ContainerGebSpec` class, as long as you have a compatible container runtime installed, you don't need to do anything else.
Just run `./gradlew integrationTest` and a container will be started and configured to start a browser that can access your application under test.

#### Parallel Execution

Parallel execution of `ContainerGebSpec` specifications is not currently supported.

#### Custom Host Configuration

The annotation `ContainerGebConfiguration` exists to customize the connection the container will use to access the application under test.
The annotation is not required and `ContainerGebSpec` will use the default values in this annotation if it's not present.

The interface `IContainerGebConfiguration` exists as an inheritable version of the annotation.

#### Reporting

To configure reporting, enable it using the `recording` property on the annotation `ContainerGebConfiguration`.  The following system properties exist for reporting configuration:

* `grails.geb.reporting.directory`
  * purpose: if the test enables reporting, the directory to save the reports relative to the project directory
  * defaults to `build/gebContainer/reports`

#### Recording

By default, no test recording will be performed.  Various system properties exist to change the recording behavior.  To set them, you can set them in your `build.gradle` file like so:

```groovy
tasks.withType(Test).configureEach {
    useJUnitPlatform()
    systemProperty('grails.geb.recording.mode', 'RECORD_ALL')
}
```

* `grails.geb.recording.mode`
  * purpose: which tests to record
  * possible values: `SKIP`, `RECORD_ALL`, or `RECORD_FAILING`
  * defaults to `SKIP`


* `grails.geb.recording.directory`
    * purpose: the directory to save the recordings relative to the project directory
    * defaults to `build/gebContainer/recordings`


* `grails.geb.recording.format`
    * purpose: sets the format of the recording
    * possible values are `FLV` or `MP4`
    * defaults to `MP4`

#### Uploads

Uploading a file is more complicated for Remote WebDriver sessions because the file you want to upload
is likely on the host executing the tests and not in the container running the browser.
For this reason, this plugin will setup a Local File Detector by default.

To customize the default, either:

1. Create a class that implements [`ContainerFileDetector`](./src/testFixtures/groovy/grails/plugin/geb/ContainerFileDetector.groovy)
   and specify its fully qualified class name in a `META-INF/services/grails.plugin.geb.ContainerFileDetector` file
   on the classpath (e.g., `src/integration-test/resources`).
2. Use the `ContainerGebConfiguration` annotation and set its `fileDetector` property to your `ContainerFileDetector` implementation class.

[//]: # (3. Call [`ServiceRegistry.setInstance&#40;&#41;`]&#40;./src/testFixtures/groovy/grails/plugin/geb/serviceloader/ServiceRegistry.groovy&#41;)
[//]: # (   in a Spock `setupSpec&#40;&#41;` method to apply your naming convention &#40;And use a `cleanupSpec&#40;&#41;` to limit this to one class&#41;.)

Alternatively, you can access the `BrowserWebDriverContainer` instance via
the `container` from within your `ContainerGebSpec` to, for example, call `.copyFileToContainer()`.
An Example of this can be seen in [ContainerSupport#createFileInputSource utility method](./src/testFixtures/groovy/grails/plugin/geb/support/ContainerSupport.groovy).

#### Timeouts

* `grails.geb.timeouts.implicitlyWait`
  * purpose: amount of time the driver should wait when searching for an element if it is not immediately present.
  * defaults to `0` seconds, which means that if an element is not found, it will immediately return an error.
  * Warning: Do not mix implicit and explicit waits. Doing so can cause unpredictable wait times.
    Consult the [Geb](https://www.gebish.org/manual/current/#implicit-assertions-waiting) 
    and/or [Selenium](https://www.selenium.dev/documentation/webdriver/waits/) documentation for details.
* `grails.geb.timeouts.pageLoad`
  * purpose: amount of time to wait for a page load to complete before throwing an error.
  * defaults to `300` seconds
* `grails.geb.timeouts.script`
  * purpose: amount of time to wait for an asynchronous script to finish execution before throwing an error.
  * defaults to `30` seconds

#### Observability and Tracing
Selenium integrates with [OpenTelemetry](https://opentelemetry.io) to support observability and tracing out of the box. By default, Selenium [enables tracing](https://www.selenium.dev/blog/2021/selenium-4-observability).

This plugin, however, **disables tracing by default** since most setups lack an OpenTelemetry collector to process the traces.

To enable tracing, set the following system property:
* `grails.geb.tracing.enabled`
  * possible values are `true` or `false`
  * defaults to `false`
  
This allows you to opt in to tracing when an OpenTelemetry collector is available.

### GebSpec

If you choose to extend `GebSpec`, you will need to have a [Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/browsers/) installed that matches a browser you have installed on your system.
This plugin comes with the `selenium-chrome-driver` java bindings pre-installed, but you can also add additional browser bindings.

To set up additional bindings, you need to add them to your `build.gradle` for example:
```groovy
dependencies {
    integrationTestImplementation 'org.seleniumhq.selenium:selenium-firefox-driver'
    integrationTestImplementation 'org.seleniumhq.selenium:selenium-edge-driver'
}
```

You also need to add a `GebConfig.groovy` file in the `src/integration-test/resources/` directory. For example:
```groovy
/*
    This is the Geb configuration file.

    See: http://www.gebish.org/manual/current/#configuration
*/

/* ... */
import org.openqa.selenium.edge.EdgeDriver
import org.openqa.selenium.firefox.FirefoxDriver

environments {
    
    /* ... */
    edge {
        driver = { new EdgeDriver() }
    }
    firefox {
        driver = { new FirefoxDriver() }
    }
}
```

And pass on the `geb.env` system property if running your tests via Gradle:
```groovy
// build.gradle
tasks.withType(Test) {
    useJUnitPlatform()
    systemProperty 'geb.env', System.getProperty('geb.env')
}
```

Now you can run your tests with the browsers installed on your system by specifying the Geb environment you have set up in your `GebConfig.groovy` file. For example:
```console
./gradlew integrationTest -Dgeb.env=edge
```
