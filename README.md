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
    implementation 'org.grails.plugins:geb'
    
    // This is needed to compile and run the tests
    integrationTestImplementation testFixtures('org.grails.plugins:geb')
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

#### Custom Host Configuration

The annotation `ContainerGebConfiguration` exists to customize the connection the container will use to access the application under test. The annotation is not required and `ContainerGebSpec` will use the default values in this annotation if it's not present.

#### Recording
By default, no test recording will be performed.  Here are the system properties available to change the recording behavior:

* `grails.geb.recording.mode`
  * purpose: which tests to record
  * possible values: `SKIP`, `RECORD_ALL`, or `RECORD_FAILING`
  * defaults to `SKIP`


* `grails.geb.recording.directory`
    * purpose: the directory to save the recordings relative to the project directory
    * defaults to `build/recordings`


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
2. Call [`ContainerFileDetectorServiceLoader.setInstance()`](./src/testFixtures/groovy/grails/plugin/geb/serviceloader/ContainerFileDetectorServiceLoader.groovy)
   in a Spock `setupSpec` method to apply your naming convention (And use a `cleanupSpec` to limit this to one class).
3. Set the `fileDetector` Property on your `ContainerGebConfiguration` annotation.

Alternatively, you can access the `BrowserWebDriverContainer` instance via
the `container` from within your ContainerGebSpec to for example call `.copyFileToContainer`,
as done in [ContainerSupport#createFileInputSource utility method](./src/testFixtures/groovy/grails/plugin/geb/support/ContainerSupport.groovy).

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
