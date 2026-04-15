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

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.codehaus.groovy.runtime.InvokerHelper

import com.github.dockerjava.api.model.ContainerNetwork
import geb.Browser
import geb.Configuration
import geb.ConfigurationLoader
import geb.spock.SpockGebTestManagerBuilder
import geb.test.GebTestManager
import geb.waiting.Wait
import org.openqa.selenium.SessionNotCreatedException
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.ContainerFetchException
import org.testcontainers.containers.PortForwardingContainer
import org.testcontainers.containers.SeleniumUtils
import org.testcontainers.containers.VncRecordingContainer
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName

import grails.plugin.geb.serviceloader.ServiceRegistry
import grails.util.Holders

import static GrailsGebSettings.DEFAULT_AT_CHECK_WAITING
import static GrailsGebSettings.DEFAULT_TIMEOUT_IMPLICITLY_WAIT
import static GrailsGebSettings.DEFAULT_TIMEOUT_PAGE_LOAD
import static GrailsGebSettings.DEFAULT_TIMEOUT_SCRIPT

/**
 * Responsible for initializing a {@link org.testcontainers.containers.BrowserWebDriverContainer}
 * per the Spec's {@link grails.plugin.geb.ContainerGebConfiguration}. This class will try to
 * reuse the same container if the configuration matches the current container.
 *
 * @author James Daugherty
 * @since 4.1
 */
@Slf4j
@CompileStatic
class WebDriverContainerHolder {

    private static final String DEFAULT_HOSTNAME_FROM_HOST = 'localhost'
    private static final String REMOTE_ADDRESS_PROPERTY = 'webdriver.remote.server'
    private static final String DEFAULT_BROWSER = 'chrome'

    GrailsGebSettings settings
    GebTestManager testManager
    Browser browser
    BrowserWebDriverContainer container
    WebDriverContainerConfiguration containerConf

    WebDriverContainerHolder(GrailsGebSettings settings) {
        this.settings = settings
    }

    boolean isInitialized() {
        container != null
    }

    void stop() {
        container?.stop()
        container = null
        browser = null
        testManager = null
        containerConf = null
    }

    boolean matchesCurrentContainerConfiguration(WebDriverContainerConfiguration specConf) {
        specConf == containerConf &&
        settings.recordingMode == BrowserWebDriverContainer.VncRecordingMode.SKIP
    }

    private static int findServerPort(IMethodInvocation methodInvocation) {
        try {
            return (int) methodInvocation.instance.metaClass.getProperty(
                    methodInvocation.instance,
                    'serverPort'
            )
        } catch (ignored) {
            // Test class is annotated with @Integration.
            // This has been verified in GrailsContainerGebExtension.visitSpec().
            throw new IllegalStateException(
                    'The `serverPort` property that should have been ' +
                    'injected by the @Integration annotation was not found.'
            )
        }
    }

    private static String findServerContextPath() {
        try {
            def applicationContext = Holders.findApplicationContext()
            return applicationContext?.environment?.getProperty('server.servlet.context-path', '/')
        } catch (ignored) {
            return '/'
        }
    }

    @PackageScope
    boolean reinitialize(IMethodInvocation methodInvocation) {
        def specConf = new WebDriverContainerConfiguration(
                methodInvocation.spec
        )
        if (matchesCurrentContainerConfiguration(specConf)) {
            return false
        }

        if (initialized) {
            stop()
        }

        def gebConf = new ConfigurationLoader().conf
        def gebConfigExists = gebConf.rawConfig.size() != 0
        def dockerImageName = createDockerImageName(DEFAULT_BROWSER)
        def customBrowser = gebConf.rawConfig.containerBrowser as String

        if (gebConfigExists) {
            validateDriverConf(gebConf)
            if (customBrowser) {
                // Prepare for creating a container matching
                // the GebConfig `containerBrowser` property.
                dockerImageName = createDockerImageName(customBrowser)
            } else {
                log.info(
                        'No `containerBrowser` property found in GebConfig. ' +
                        'Using default [{}] container image.',
                        DEFAULT_BROWSER
                )
            }
        }

        containerConf = specConf
        container = new BrowserWebDriverContainer(dockerImageName).withRecordingMode(
                settings.recordingMode,
                settings.recordingDirectory,
                settings.recordingFormat
        )

        container.with {
            withEnv('SE_ENABLE_TRACING', settings.tracingEnabled.toString())
            withAccessToHost(true)
            withImagePullPolicy(PullPolicy.ageBased(Duration.of(1, ChronoUnit.DAYS)))
        }
        startContainer(container, dockerImageName, customBrowser)

        if (hostnameChanged) {
            container.execInContainer(
                    '/bin/sh', '-c',
                    "echo '$hostIp\t$containerConf.hostName' | sudo tee -a /etc/hosts"
            )
        }

        // Ensure that the driver points to the re-initialized container with the correct host.
        // The driver is explicitly quit by us in stop() method, to fulfill our resulting responsibility.
        gebConf.cacheDriver = false

        // As we don't cache, this will have been defaulted to true. We override to false.
        gebConf.quitDriverOnBrowserReset = false

        gebConf.baseUrl = container.seleniumAddress
        if (containerConf.reporting) {
            gebConf.reportsDir = settings.reportingDirectory
            gebConf.reporter = (methodInvocation.sharedInstance as ContainerGebSpec).createReporter()
        }

        if (gebConf.driverConf) {
            // As a custom `GebConfig` cannot know the `remoteAddress` of the container beforehand,
            // the `RemoteWebDriver` will be instantiated using the `webdriver.remote.server`
            // system property. We set that property to inform the driver of the container address.
            gebConf.driverConf = ClosureDecorators.withSystemProperty(
                    gebConf.driverConf as Closure,
                    REMOTE_ADDRESS_PROPERTY,
                    container.seleniumAddress
            )
        } else {
            // If no driver was set in GebConfig, create a Chrome driver
            gebConf.driverConf = { ->
                log.info('Using default Chrome RemoteWebDriver for {}', container.seleniumAddress)
                new RemoteWebDriver(container.seleniumAddress, new ChromeOptions().tap {
                    // See https://issues.chromium.org/issues/42323769
                    setExperimentalOption('prefs', [
                            'credentials_enable_service': false,
                            'profile.password_manager_enabled': false,
                            'profile.password_manager_leak_detection': false
                    ])
                })
            }
        }

        browser = createBrowser(gebConf)
        applyFileDetector(browser, containerConf)
        applyTimeouts(browser, settings)

        // There's a bit of a chicken and egg problem here: the container and browser are initialized
        // when the static/shared fields are initialized, which is before the grails server has started
        // so the real url cannot be set (it will be checked as part of the geb test manager startup in
        // reporting mode). We set the url to localhost, which the selenium server should respond to
        // (albeit with an error that will be ignored).
        browser.baseUrl = 'http://localhost'

        testManager = createTestManager()

        return true
    }

    private static Browser createBrowser(Configuration gebConf) {
        def browser = new Browser(gebConf)
        try {
            browser.driver
        }
        catch (SessionNotCreatedException e) {
            throw new IllegalStateException(
                    'Failed to create a remote browser session. ' +
                    'Did you set a `containerBrowser` property ' +
                    'corresponding to the `driver` in GebConfig?',
                    e
            )
        }
        browser
    }

    private static void applyFileDetector(Browser browser, WebDriverContainerConfiguration conf) {
        if (conf.fileDetector != NullContainerFileDetector) {
            ServiceRegistry.setInstance(ContainerFileDetector, conf.fileDetector)
        }
        ((RemoteWebDriver) browser.driver).fileDetector = ServiceRegistry.getInstance(
                ContainerFileDetector,
                DefaultContainerFileDetector
        )
    }

    private static void applyTimeouts(Browser browser, GrailsGebSettings settings) {
        // Overwrite `GebConfig` timeouts with values explicitly set in
        // `GrailsGebSettings` (via system properties)
        if (settings.implicitlyWait != DEFAULT_TIMEOUT_IMPLICITLY_WAIT)
            browser.driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(settings.implicitlyWait))
        if (settings.pageLoadTimeout != DEFAULT_TIMEOUT_PAGE_LOAD)
            browser.driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(settings.pageLoadTimeout))
        if (settings.scriptTimeout != DEFAULT_TIMEOUT_SCRIPT)
            browser.driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(settings.scriptTimeout))
        if (settings.atCheckWaiting != DEFAULT_AT_CHECK_WAITING)
            browser.config.atCheckWaiting = settings.atCheckWaiting
        if (settings.timeout != Wait.DEFAULT_TIMEOUT)
            browser.config.defaultWaitTimeout = settings.timeout
        if (settings.retryInterval != Wait.DEFAULT_RETRY_INTERVAL)
            browser.config.defaultWaitRetryInterval = settings.retryInterval
    }

    private static void startContainer(BrowserWebDriverContainer container, DockerImageName dockerImageName, String customBrowser) {
        try {
            container.start()
        } catch (ContainerFetchException e) {
            if (customBrowser) {
                throw new IllegalStateException(
                        "Could not find the Docker image [$dockerImageName] " +
                        "with the browser name from the 'containerBrowser' [$customBrowser] " +
                        'property specified in GebConfig. ' +
                        'See https://hub.docker.com/u/selenium for a list of available images.',
                        e
                )
            }
            throw e
        }
    }

    void setupBrowserUrl(IMethodInvocation methodInvocation) {
        if (!browser) return
        int hostPort = findServerPort(methodInvocation)
        String contextPath = findServerContextPath()
        Testcontainers.exposeHostPorts(hostPort)
        String baseUrl = "$containerConf.protocol://$containerConf.hostName:$hostPort"
        if (contextPath && contextPath != '/') {
            if (!contextPath.startsWith('/')) {
                contextPath = "/$contextPath"
            }
            if (!contextPath.endsWith('/')) {
                contextPath = "$contextPath/"
            }
            baseUrl += contextPath
        }
        browser.baseUrl = baseUrl
    }

    private GebTestManager createTestManager() {
        new SpockGebTestManagerBuilder()
                .withReportingEnabled(containerConf.reporting)
                .withBrowserCreator(
                        new Supplier<Browser>() {
                            @Override
                            Browser get() {
                                browser
                            }
                        }
                )
                .build()
    }

    private boolean isHostnameChanged() {
        containerConf.hostName != ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
    }

    private static String getHostIp() {
        try {
            PortForwardingContainer.getDeclaredMethod('getNetwork').with {
                accessible = true
                (invoke(PortForwardingContainer.INSTANCE) as Optional<ContainerNetwork>)
                        .get()
                        .ipAddress
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    'Could not access network from PortForwardingContainer',
                    e
            )
        }
    }

    /**
     * Returns the hostname that the server under test is available on from the host.
     * <p>This is useful when using any of the {@code download*()} methods as they will
     * connect from the host, and not from within the container.
     *
     * <p>Defaults to {@code localhost}. If the value returned by {@code webDriverContainer.getHost()}
     * is different from the default, this method will return the same value same as
     * {@code webDriverContainer.getHost()}.
     *
     * @return the hostname for accessing the server under test from the host
     */
    String getHostNameFromHost() {
        hostNameChanged ? container.host : DEFAULT_HOSTNAME_FROM_HOST
    }

    private boolean isHostNameChanged() {
        container.host != ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
    }

    private static DockerImageName createDockerImageName(String browserName) {
        DockerImageName.parse(
                "selenium/standalone-$browserName:$seleniumVersion"
        )
    }

    private static void validateDriverConf(Configuration gebConf) {
        if (gebConf.driverConf && !(gebConf.driverConf instanceof Closure)) {
            throw new IllegalStateException(
                    'The `driver` property of GebConfig must be a ' +
                    'Closure that returns an instance of RemoteWebDriver.'
            )
        }
    }

    private static String getSeleniumVersion() {
        SeleniumUtils.determineClasspathSeleniumVersion()
    }

    @CompileStatic
    @EqualsAndHashCode
    private static class WebDriverContainerConfiguration {

        String protocol
        String hostName
        boolean reporting
        Class<? extends ContainerFileDetector> fileDetector

        WebDriverContainerConfiguration(SpecInfo spec) {

            ContainerGebConfiguration conf

            // Check if the class implements the interface
            if (IContainerGebConfiguration.isAssignableFrom(spec.reflection)) {
                conf = spec.reflection.getConstructor().newInstance() as ContainerGebConfiguration
            } else {
                // Check for the annotation
                conf = spec.annotations.find {
                    it.annotationType() == ContainerGebConfiguration
                } as ContainerGebConfiguration
            }

            protocol = conf?.protocol() ?: ContainerGebConfiguration.DEFAULT_PROTOCOL
            hostName = conf?.hostName() ?: ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
            reporting = conf?.reporting() ?: false
            fileDetector = conf?.fileDetector() ?: ContainerGebConfiguration.DEFAULT_FILE_DETECTOR
        }
    }

    /**
     * Workaround for https://github.com/testcontainers/testcontainers-java/issues/3998
     * <p>
     * Restarts the VNC recording container to enable separate recording files for each
     * test method. This method uses reflection to access the VNC recording container
     * field in BrowserWebDriverContainer. Should be called BEFORE each test starts.
     */
    void restartVncRecordingContainer() {
        if (!settings.recordingEnabled || !settings.restartRecordingContainerPerTest || !container) {
            return
        }
        try {
            // Use reflection to access the VNC recording container field
            def field = BrowserWebDriverContainer.getDeclaredField('vncRecordingContainer').tap {
                accessible = true
            }

            def vncContainer = field.get(container) as VncRecordingContainer
            if (vncContainer) {
                // Stop the current VNC recording container
                vncContainer.stop()
                // Create and start a new VNC recording container for the next test
                def newVncContainer = new VncRecordingContainer(container)
                        .withVncPassword('secret')
                        .withVncPort(5900)
                        .withVideoFormat(settings.recordingFormat)
                field.set(container, newVncContainer)
                newVncContainer.start()

                log.debug('Successfully restarted VNC recording container')
            }
        } catch (Exception e) {
            log.warn("Failed to restart VNC recording container: $e.message", e)
            // Don't throw the exception to avoid breaking the test execution
        }
    }

    @CompileStatic
    private static class ClosureDecorators {

        /**
         * Wraps a closure so that during its execution, System.getProperty(key)
         * returns a custom value instead of what is actually in the system properties.
         */
        static Closure withSystemProperty(Closure target, String key, Object value) {
            Closure wrapped = { Object... args ->
                SysPropScope.withProperty(key, value.toString()) {
                    InvokerHelper.invokeClosure(target, args)
                }
            }

            // keep original closure semantics
            wrapped.rehydrate(target.delegate, target.owner, target.thisObject).tap {
                resolveStrategy = target.resolveStrategy
            }
        }

        @CompileStatic
        private static class SysPropScope {

            private static final ThreadLocal<Map<String,String>> OVERRIDDEN_SYSTEM_PROPERTIES =
                    ThreadLocal.withInitial { [:] as Map<String,String> }

            @Lazy // Thread-safe wrapping of system properties
            private static Properties propertiesWrappedOnFirstAccess = {
                new InterceptingProperties().tap {
                    putAll(System.getProperties())
                    System.setProperties(it)
                }
            }()

            // Helper method for Groovy 5 static type checking compatibility
            private static Map<String, String> getOverriddenProperties() {
                OVERRIDDEN_SYSTEM_PROPERTIES.get()
            }

            static <T> T withProperty(String key, String value, Closure<T> body) {
                propertiesWrappedOnFirstAccess // Access property to trigger property wrapping
                def map = OVERRIDDEN_SYSTEM_PROPERTIES.get()
                def prev = map.put(key, value)
                try {
                    return body.call()
                } finally {
                    if (prev == null) map.remove(key) else map[key] = prev
                    if (map.isEmpty()) OVERRIDDEN_SYSTEM_PROPERTIES.remove()
                }
            }

            @CompileStatic
            private static class InterceptingProperties extends Properties {
                @Override
                String getProperty(String key) {
                    Map<String, String> overrides = getOverriddenProperties()
                    def v = overrides.get(key)
                    v != null ? v : super.getProperty(key)
                }
            }
        }
    }
}
