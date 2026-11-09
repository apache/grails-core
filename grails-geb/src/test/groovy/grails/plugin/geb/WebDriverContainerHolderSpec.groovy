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

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.LogContainerCmd
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.ContainerLaunchException
import org.testcontainers.containers.Network
import org.testcontainers.containers.VncRecordingContainer
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget

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

    void 'the recording container for a test is not started while its recorder has only connected'() {
        given: 'the recording container that is started before a test'
        def waitStrategy = createRecordingContainer().waitStrategy
                .withStartupTimeout(Duration.ofSeconds(1))

        and: 'a recorder that has connected, but is still receiving the screen'
        def recorder = recorderLogging(RECORDER_CONNECTED_LOG)

        when:
        waitStrategy.waitUntilReady(recorder)

        then: 'the recording container is not ready, as its recording would only show a black screen'
        thrown(ContainerLaunchException)
    }

    void 'the recording container for a test is started once its recording shows the screen'() {
        given: 'the recording container that is started before a test'
        def waitStrategy = createRecordingContainer().waitStrategy

        and: 'a recorder that has received the screen and written it to the recording'
        def recorder = recorderLogging(RECORDER_CONNECTED_LOG + RECORDER_SCREEN_RECORDED_LOG)

        when:
        waitStrategy.waitUntilReady(recorder)

        then: 'the recording container is ready'
        noExceptionThrown()
    }

    // Debug output of the VNC recorder (flvrec.py of testcontainers/vnc-recorder:1.3.0, as run by
    // VncRecordingContainer) connecting to selenium/standalone-chrome:4.49.0
    private static final List<String> RECORDER_CONNECTED_LOG = [
            'start recording',
            'Connected: browser:5900',
            'protocol_version: 3.8',
            'server_result: 0',
            'Server Encoding:',
            " width=1920, height=1080, name='browser:99.0'",
            "init_screen: 1920x1080, name='browser:99.0'",
            'start: 0,0 (1920x1080)',
            'set_screen_size: 1920x1080',
            'FrameBufferUpdateRequest',
            'write_video_frame: timestamp=0, data=55085',
            'FrameBufferUpdate: nrects=1',
            ' 1920 x 1080 at (0,0), enc=0',
    ]
    private static final List<String> RECORDER_SCREEN_RECORDED_LOG = [
            ' RawEncoding: received=8294400',
            'write_video_frame: timestamp=266, data=28035',
            'FrameBufferUpdateRequest',
    ]

    private VncRecordingContainer createRecordingContainer() {
        holder.container = Stub(BrowserWebDriverContainer) {
            getNetwork() >> Stub(Network)
            getNetworkAliases() >> ['browser']
        }
        holder.createVncRecordingContainer()
    }

    /**
     * A container whose log, as read by a {@link org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy},
     * consists of the given lines.
     */
    private WaitStrategyTarget recorderLogging(List<String> lines) {
        def logCommand = Stub(LogContainerCmd)
        logCommand.withFollowStream(_) >> logCommand
        logCommand.withSince(_) >> logCommand
        logCommand.withStdOut(_) >> logCommand
        logCommand.withStdErr(_) >> logCommand
        logCommand.exec(_) >> { ResultCallback<Frame> callback ->
            lines.each { callback.onNext(new Frame(StreamType.STDERR, (it + '\n').getBytes(StandardCharsets.UTF_8))) }
            callback
        }
        def dockerClient = Stub(DockerClient) {
            logContainerCmd(_) >> logCommand
        }
        Stub(WaitStrategyTarget) {
            getDockerClient() >> dockerClient
        }
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
