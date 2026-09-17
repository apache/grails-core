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

import java.time.LocalDateTime

import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.VncRecordingContainer

import org.openqa.selenium.WebDriver

import geb.Browser
import geb.test.GebTestManager
import spock.lang.Shared
import spock.lang.Specification

import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode

class WebDriverContainerHolderSpec extends Specification {

    private static final String GEB_SYSTEM_PROPERTY_PREFIX = 'grails.geb.'

    // GrailsGebSettings' constructor parses live `grails.geb.*` system properties (forwarded
    // from `grails.geb.*` project properties by gradle/test-config.gradle), so an externally
    // supplied value - e.g. a malformed `grails.geb.recording.mode` in a developer's gradle
    // properties - could otherwise make the `holder` field initializer below throw before any
    // feature method runs. Clearing them for the duration of this spec keeps it hermetic.
    @Shared
    private Map<String, String> savedGebSystemProperties

    def setupSpec() {
        savedGebSystemProperties = System.getProperties().stringPropertyNames()
                .findAll { it.startsWith(GEB_SYSTEM_PROPERTY_PREFIX) }
                .collectEntries { [(it): System.getProperty(it)] }
        savedGebSystemProperties.keySet().each { System.clearProperty(it) }
    }

    def cleanupSpec() {
        savedGebSystemProperties.each { key, value -> System.setProperty(key, value) }
    }

    WebDriverContainerHolder holder = new WebDriverContainerHolder(new GrailsGebSettings(LocalDateTime.now()))

    void 'stop() resets container, browser and testManager on the happy path'() {
        given: 'a holder with an initialized container'
        def container = Mock(BrowserWebDriverContainer)
        def driver = Mock(WebDriver)
        def browser = Mock(Browser)
        browser.driver >> driver
        holder.container = container
        holder.browser = browser
        holder.testManager = Mock(GebTestManager)

        when: 'the holder is stopped'
        holder.stop()

        then: 'the driver session is quit while the container backing it is still up'
        1 * driver.quit()

        and: 'the underlying container is stopped'
        1 * container.stop()

        and: 'all held state is cleared'
        holder.container == null
        holder.browser == null
        holder.testManager == null
        !holder.initialized
    }

    void 'stop() still resets all held state when container.stop() throws'() {
        given: 'a holder whose container fails to stop cleanly'
        def container = Mock(BrowserWebDriverContainer)
        container.stop() >> { throw new IllegalStateException('boom') }
        holder.container = container
        holder.browser = Mock(Browser)
        holder.testManager = Mock(GebTestManager)

        when: 'the holder is stopped'
        holder.stop()

        then: 'the exception from stop() propagates'
        thrown(IllegalStateException)

        and: 'held state is still cleared, so a broken container is never reported as initialized'
        holder.container == null
        holder.browser == null
        holder.testManager == null
        !holder.initialized
    }

    void 'stop() still quits the driver and stops the container when quitting the driver throws'() {
        given: 'a holder whose driver refuses to quit cleanly'
        def container = Mock(BrowserWebDriverContainer)
        def driver = Mock(WebDriver)
        driver.quit() >> { throw new IllegalStateException('driver refused to quit') }
        def browser = Mock(Browser)
        browser.driver >> driver
        holder.container = container
        holder.browser = browser
        holder.testManager = Mock(GebTestManager)

        when: 'the holder is stopped'
        holder.stop()

        then: "the driver failure doesn't stop the container from being stopped or state from being reset"
        noExceptionThrown()
        1 * container.stop()
        holder.container == null
        holder.browser == null
        holder.testManager == null
        !holder.initialized
    }

    void 'restartVncRecordingContainer() does nothing when recording is disabled'() {
        given:
        holder.settings.recordingMode = VncRecordingMode.SKIP
        holder.settings.restartRecordingContainerPerTest = true
        def container = Mock(BrowserWebDriverContainer)
        holder.container = container

        when:
        holder.restartVncRecordingContainer()

        then:
        0 * container._
    }

    void 'restartVncRecordingContainer() does nothing when per-test restart is disabled'() {
        given:
        holder.settings.recordingMode = VncRecordingMode.RECORD_ALL
        holder.settings.restartRecordingContainerPerTest = false
        def container = Mock(BrowserWebDriverContainer)
        holder.container = container

        when:
        holder.restartVncRecordingContainer()

        then:
        0 * container._
    }

    void 'restartVncRecordingContainer() does nothing when no container has been initialized'() {
        given:
        holder.settings.recordingMode = VncRecordingMode.RECORD_ALL
        holder.settings.restartRecordingContainerPerTest = true
        holder.container = null

        expect: 'no exception is thrown even though there is nothing to restart'
        holder.restartVncRecordingContainer()
    }

    void 'restartVncRecordingContainer() swallows a failure starting the replacement container, and clears the field instead of leaving a stale reference'() {
        given: 'a container whose active VNC recording container is in place'
        holder.settings.recordingMode = VncRecordingMode.RECORD_ALL
        holder.settings.restartRecordingContainerPerTest = true
        // Left otherwise unconfigured: VncRecordingContainer#start() inspects the browser
        // container it's attached to (e.g. getNetworkAliases()) before touching Docker, so a
        // bare mock makes the replacement container fail to start deterministically, without
        // needing a real Docker daemon.
        def container = Mock(BrowserWebDriverContainer)
        holder.container = container

        // No public API sets BrowserWebDriverContainer's private `vncRecordingContainer`
        // field - production code (restartVncRecordingContainer) resorts to the same
        // reflection to read it, as a documented workaround for
        // https://github.com/testcontainers/testcontainers-java/issues/3998.
        def vncContainer = Mock(VncRecordingContainer)
        def vncField = BrowserWebDriverContainer.getDeclaredField('vncRecordingContainer')
        vncField.accessible = true
        vncField.set(container, vncContainer)

        when: 'restarting the recording container twice'
        holder.restartVncRecordingContainer()
        holder.restartVncRecordingContainer()

        then: 'the failure starting the replacement is logged and swallowed rather than breaking test execution'
        noExceptionThrown()

        and: 'the old container is stopped only once - observable proof that the field was ' +
                'cleared on the first (failed) restart rather than left pointing at it, since a ' +
                'stale reference would have made the second restart stop it again'
        1 * vncContainer.stop()
    }
}
