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

import geb.Browser
import geb.test.GebTestManager
import spock.lang.Specification

import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode

class WebDriverContainerHolderSpec extends Specification {

    WebDriverContainerHolder holder = new WebDriverContainerHolder(new GrailsGebSettings(LocalDateTime.now()))

    void 'stop() resets container, browser and testManager on the happy path'() {
        given: 'a holder with an initialized container'
        def container = Mock(BrowserWebDriverContainer)
        holder.container = container
        holder.browser = Mock(Browser)
        holder.testManager = Mock(GebTestManager)

        when: 'the holder is stopped'
        holder.stop()

        then: 'the underlying container is stopped'
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

    void 'restartVncRecordingContainer() swallows a failure from the current recording container instead of propagating it'() {
        given: 'a container whose active VNC recording container fails to stop'
        holder.settings.recordingMode = VncRecordingMode.RECORD_ALL
        holder.settings.restartRecordingContainerPerTest = true
        def container = Mock(BrowserWebDriverContainer)
        holder.container = container

        def vncContainer = Mock(VncRecordingContainer)
        vncContainer.stop() >> { throw new IllegalStateException('vnc container refused to stop') }
        def vncField = BrowserWebDriverContainer.getDeclaredField('vncRecordingContainer')
        vncField.accessible = true
        vncField.set(container, vncContainer)

        when: 'restarting the recording container'
        holder.restartVncRecordingContainer()

        then: 'the failure is logged and swallowed rather than breaking test execution'
        noExceptionThrown()

        and: "the field is left untouched - it still points at the container that just failed to stop, not a container that never started"
        vncField.get(container).is(vncContainer)
    }
}
