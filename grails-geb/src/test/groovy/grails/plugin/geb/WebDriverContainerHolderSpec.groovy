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
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode

@RestoreSystemProperties
class WebDriverContainerHolderSpec extends Specification {

    WebDriverContainerHolder holder

    def setup() {
        // GrailsGebSettings' constructor parses live `grails.geb.*` system properties
        // (forwarded from `grails.geb.*` project properties by gradle/test-config.gradle), so
        // clear them to keep an externally supplied value from breaking or skewing this spec.
        // @RestoreSystemProperties puts them back after each feature.
        System.properties.stringPropertyNames()
                .findAll { it.startsWith('grails.geb.') }
                .each { System.clearProperty(it) }
        holder = Spy(WebDriverContainerHolder, constructorArgs: [new GrailsGebSettings(LocalDateTime.now())])
    }

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

    void 'restartVncRecordingContainer() stops the current recording container and starts a replacement'() {
        given: 'a browser container with an active VNC recording container'
        enablePerTestRecording()
        def container = Mock(BrowserWebDriverContainer)
        holder.container = container
        def current = Mock(VncRecordingContainer)
        setRecordingContainer(container, current)
        def replacement = Mock(VncRecordingContainer)
        holder.createVncRecordingContainer() >>> [replacement, Mock(VncRecordingContainer)]

        when:
        holder.restartVncRecordingContainer()

        then: 'the current recording container is stopped first'
        1 * current.stop()

        then: 'the replacement is started'
        1 * replacement.start()

        and:
        holder.recordingContainerAvailable

        when: 'restarting before the following test'
        holder.restartVncRecordingContainer()

        then: 'it is the replacement that is stopped, so it had taken the place of the original'
        1 * replacement.stop()
        0 * current.stop()
    }

    void 'restartVncRecordingContainer() stops a replacement that fails to start and leaves no recording container behind'() {
        given: 'a browser container with an active VNC recording container'
        enablePerTestRecording()
        def container = Mock(BrowserWebDriverContainer)
        holder.container = container
        def current = Mock(VncRecordingContainer)
        setRecordingContainer(container, current)

        and: 'a replacement that is created, but fails to start'
        def replacement = Mock(VncRecordingContainer)
        replacement.start() >> { throw new IllegalStateException('Timed out waiting for log output') }
        holder.createVncRecordingContainer() >> replacement

        when:
        holder.restartVncRecordingContainer()

        then: 'the failure is logged and swallowed rather than breaking test execution'
        noExceptionThrown()

        and: 'the replacement is stopped so it is not left running'
        1 * current.stop()
        1 * replacement.stop()

        and: 'neither the stopped nor the failed container is left as the recording container'
        !holder.recordingContainerAvailable
    }

    void 'restartVncRecordingContainer() starts a new recording container after a previous restart failed'() {
        given: 'a browser container with an active VNC recording container'
        enablePerTestRecording()
        def container = Mock(BrowserWebDriverContainer)
        holder.container = container
        def current = Mock(VncRecordingContainer)
        setRecordingContainer(container, current)

        and: 'a first replacement that fails to start, and a second one that starts'
        def failing = Mock(VncRecordingContainer)
        failing.start() >> { throw new IllegalStateException('Timed out waiting for log output') }
        def working = Mock(VncRecordingContainer)
        holder.createVncRecordingContainer() >>> [failing, working]

        when: 'restarting before two consecutive tests'
        holder.restartVncRecordingContainer()
        holder.restartVncRecordingContainer()

        then: 'the original recording container is stopped exactly once'
        1 * current.stop()

        and: 'the second restart starts a new recording container instead of giving up'
        1 * working.start()
        holder.recordingContainerAvailable
    }

    void 'isRecordingContainerAvailable() is false without an initialized container'() {
        expect:
        !holder.recordingContainerAvailable
    }

    void 'isRecordingContainerAvailable() is false when the container has no recording container'() {
        given: 'a container created with recording disabled'
        holder.container = Mock(BrowserWebDriverContainer)

        expect:
        !holder.recordingContainerAvailable
    }

    private void enablePerTestRecording() {
        holder.settings.recordingMode = VncRecordingMode.RECORD_ALL
        holder.settings.restartRecordingContainerPerTest = true
    }

    // No public API sets BrowserWebDriverContainer's private `vncRecordingContainer` field.
    // WebDriverContainerHolder resorts to the same reflection as a workaround for
    // https://github.com/testcontainers/testcontainers-java/issues/3998.
    private static void setRecordingContainer(BrowserWebDriverContainer container, VncRecordingContainer vncContainer) {
        BrowserWebDriverContainer.getDeclaredField('vncRecordingContainer').tap {
            accessible = true
            set(container, vncContainer)
        }
    }
}
