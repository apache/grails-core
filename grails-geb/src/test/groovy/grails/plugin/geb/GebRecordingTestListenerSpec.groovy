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
import org.spockframework.runtime.model.IterationInfo

import spock.lang.Shared
import spock.lang.Specification

class GebRecordingTestListenerSpec extends Specification {

    private static final String GEB_SYSTEM_PROPERTY_PREFIX = 'grails.geb.'

    // See WebDriverContainerHolderSpec: GrailsGebSettings' constructor parses live
    // `grails.geb.*` system properties, so clear them for the duration of this spec to
    // keep the `containerHolder` field initializer below hermetic.
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

    WebDriverContainerHolder containerHolder = new WebDriverContainerHolder(new GrailsGebSettings(LocalDateTime.now()))
    GebRecordingTestListener listener = new GebRecordingTestListener(containerHolder)

    void 'afterIteration() swallows a NullPointerException from a missing VNC recording container when per-test restart is enabled'() {
        given: 'restarting the recording container before this test failed, leaving no recording container available'
        containerHolder.settings.restartRecordingContainerPerTest = true
        def container = Mock(BrowserWebDriverContainer)
        container.afterTest(_, _) >> { throw new NullPointerException() }
        containerHolder.container = container

        when: 'the iteration completes'
        listener.afterIteration(Mock(IterationInfo))

        then: 'the failure is swallowed rather than reported as a test error'
        noExceptionThrown()
    }

    void 'afterIteration() re-throws a NullPointerException from a missing VNC recording container when per-test restart is disabled'() {
        given: 'per-test restart is not in play, so a missing recording container is unexpected'
        containerHolder.settings.restartRecordingContainerPerTest = false
        def container = Mock(BrowserWebDriverContainer)
        container.afterTest(_, _) >> { throw new NullPointerException() }
        containerHolder.container = container

        when: 'the iteration completes'
        listener.afterIteration(Mock(IterationInfo))

        then: 'the failure propagates so the underlying bug is not hidden'
        thrown(NullPointerException)
    }
}
