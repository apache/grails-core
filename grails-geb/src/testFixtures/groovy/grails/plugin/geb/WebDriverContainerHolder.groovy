/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.plugin.geb

import com.github.dockerjava.api.model.ContainerNetwork
import geb.Browser
import geb.Configuration
import geb.spock.SpockGebTestManagerBuilder
import geb.test.GebTestManager
import grails.plugin.geb.serviceloader.ServiceRegistry
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.PortForwardingContainer
import org.testcontainers.images.PullPolicy

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.function.Supplier

/**
 * Responsible for initializing a {@link org.testcontainers.containers.BrowserWebDriverContainer BrowserWebDriverContainer}
 * per the Spec's {@link grails.plugin.geb.ContainerGebConfiguration ContainerGebConfiguration}.  This class will try to
 * reuse the same container if the configuration matches the current container.
 *
 * @author James Daugherty
 * @since 4.1
 */
@Slf4j
@CompileStatic
class WebDriverContainerHolder {

    private static final String DEFAULT_HOSTNAME_FROM_HOST = 'localhost'

    GrailsGebSettings grailsGebSettings
    GebTestManager testManager
    Browser currentBrowser
    BrowserWebDriverContainer currentContainer
    WebDriverContainerConfiguration currentConfiguration

    WebDriverContainerHolder(GrailsGebSettings grailsGebSettings) {
        this.grailsGebSettings = grailsGebSettings
    }

    boolean isInitialized() {
        currentContainer != null
    }

    void stop() {
        currentContainer?.stop()
        currentContainer = null
        currentBrowser = null
        testManager = null
        currentConfiguration = null
    }

    boolean matchesCurrentContainerConfiguration(WebDriverContainerConfiguration specConfiguration) {
        specConfiguration == currentConfiguration && grailsGebSettings.recordingMode == BrowserWebDriverContainer.VncRecordingMode.SKIP
    }

    private static int getPort(IMethodInvocation invocation) {
        try {
            return (int) invocation.instance.metaClass.getProperty(invocation.instance, 'serverPort')
        } catch (ignored) {
            throw new IllegalStateException('Test class must be annotated with @Integration for serverPort to be injected')
        }
    }

    @PackageScope
    boolean reinitialize(IMethodInvocation invocation) {
        WebDriverContainerConfiguration specConfiguration = new WebDriverContainerConfiguration(
                invocation.getSpec()
        )
        if (matchesCurrentContainerConfiguration(specConfiguration)) {
            return false
        }

        if (initialized) {
            stop()
        }

        currentConfiguration = specConfiguration
        currentContainer = new BrowserWebDriverContainer().withRecordingMode(
                grailsGebSettings.recordingMode,
                grailsGebSettings.recordingDirectory,
                grailsGebSettings.recordingFormat
        )

        Map prefs = [
                "credentials_enable_service"             : false,
                "profile.password_manager_enabled"       : false,
                "profile.password_manager_leak_detection": false
        ]

        ChromeOptions chromeOptions = new ChromeOptions()
        // TODO: guest would be preferred, but this causes issues with downloads
        // see https://issues.chromium.org/issues/42323769
        // chromeOptions.addArguments("--guest")
        chromeOptions.setExperimentalOption("prefs", prefs)

        currentContainer.tap {
            withEnv('SE_ENABLE_TRACING', grailsGebSettings.tracingEnabled)
            withAccessToHost(true)
            withImagePullPolicy(PullPolicy.ageBased(Duration.of(1, ChronoUnit.DAYS)))
            withCapabilities(chromeOptions)
            start()
        }
        if (hostnameChanged) {
            currentContainer.execInContainer('/bin/sh', '-c', "echo '$hostIp\t${currentConfiguration.hostName}' | sudo tee -a /etc/hosts")
        }

        ConfigObject configObject = new ConfigObject()
        if (currentConfiguration.reporting) {
            configObject.reportsDir = grailsGebSettings.reportingDirectory
            configObject.reporter = (invocation.sharedInstance as ContainerGebSpec).createReporter()
        }
        if (currentConfiguration.fileDetector != NullContainerFileDetector) {
            ServiceRegistry.setInstance(ContainerFileDetector, currentConfiguration.fileDetector)
        }

        currentBrowser = new Browser(new Configuration(configObject, new Properties(), null, null))

        WebDriver driver = new RemoteWebDriver(currentContainer.seleniumAddress, chromeOptions)
        ContainerFileDetector fileDetector = ServiceRegistry.getInstance(ContainerFileDetector, DefaultContainerFileDetector)
        ((RemoteWebDriver) driver).setFileDetector(fileDetector)
        driver.manage().timeouts().with {
            implicitlyWait(Duration.ofSeconds(grailsGebSettings.implicitlyWait))
            pageLoadTimeout(Duration.ofSeconds(grailsGebSettings.pageLoadTimeout))
            scriptTimeout(Duration.ofSeconds(grailsGebSettings.scriptTimeout))
        }

        currentBrowser.driver = driver

        // There's a bit of a chicken and egg problem here: the container & browser are initialized when
        // the static/shared fields are initialized, which is before the grails server has started so the
        // real url cannot be set (it will be checked as part of the geb test manager startup in reporting mode)
        // set the url to localhost, which the selenium server should respond to (albeit with an error that will be ignored)

        currentBrowser.baseUrl = 'http://localhost'

        testManager = createTestManager()

        return true
    }

    void setupBrowserUrl(IMethodInvocation invocation) {
        if (!currentBrowser) {
            return
        }
        int port = getPort(invocation)
        Testcontainers.exposeHostPorts(port)

        currentBrowser.baseUrl = "${currentConfiguration.protocol}://${currentConfiguration.hostName}:${port}"
    }

    private GebTestManager createTestManager() {
        new SpockGebTestManagerBuilder()
                .withReportingEnabled(currentConfiguration.reporting)
                .withBrowserCreator(new Supplier<Browser>() {
                    @Override
                    Browser get() {
                        currentBrowser
                    }
                })
                .build()
    }

    private boolean getHostnameChanged() {
        currentConfiguration.hostName != ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
    }

    private static String getHostIp() {
        try {
            PortForwardingContainer.getDeclaredMethod('getNetwork').with {
                accessible = true
                Optional<ContainerNetwork> network = invoke(PortForwardingContainer.INSTANCE) as Optional<ContainerNetwork>
                return network.get().ipAddress
            }
        } catch (Exception e) {
            throw new RuntimeException('Could not access network from PortForwardingContainer', e)
        }
    }

    /**
     * Returns the hostname that the server under test is available on from the host.
     * <p>This is useful when using any of the {@code download*()} methods as they will connect from the host,
     * and not from within the container.
     * <p>Defaults to {@code localhost}. If the value returned by {@code webDriverContainer.getHost()}
     * is different from the default, this method will return the same value same as {@code webDriverContainer.getHost()}.
     *
     * @return the hostname for accessing the server under test from the host
     */
    String getHostNameFromHost() {
        return hostNameChanged ? currentContainer.host : DEFAULT_HOSTNAME_FROM_HOST
    }

    private boolean isHostNameChanged() {
        return currentContainer.host != ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
    }

    @CompileStatic
    @EqualsAndHashCode
    private static class WebDriverContainerConfiguration {

        String protocol
        String hostName
        boolean reporting
        Class<? extends ContainerFileDetector> fileDetector

        WebDriverContainerConfiguration(SpecInfo spec) {
            ContainerGebConfiguration configuration

            // Check if the class implements the interface
            if (IContainerGebConfiguration.isAssignableFrom(spec.reflection)) {
                configuration = spec.reflection.getConstructor().newInstance() as ContainerGebConfiguration
            } else {
                // Check for the annotation
                configuration = spec.annotations.find {
                    it.annotationType() == ContainerGebConfiguration
                } as ContainerGebConfiguration
            }

            protocol = configuration?.protocol() ?: ContainerGebConfiguration.DEFAULT_PROTOCOL
            hostName = configuration?.hostName() ?: ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
            reporting = configuration?.reporting() ?: false
            fileDetector = configuration?.fileDetector() ?: ContainerGebConfiguration.DEFAULT_FILE_DETECTOR
        }
    }
}

