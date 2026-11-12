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

import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode

@RestoreSystemProperties
class GebRecordingTestListenerSpec extends Specification {

    WebDriverContainerHolder containerHolder
    GebRecordingTestListener listener
    BrowserWebDriverContainer container = Mock()

    def setup() {
        // See WebDriverContainerHolderSpec: GrailsGebSettings' constructor parses live
        // `grails.geb.*` system properties, so clear them to keep this spec hermetic.
        System.properties.stringPropertyNames()
                .findAll { it.startsWith('grails.geb.') }
                .each { System.clearProperty(it) }
        containerHolder = Spy(WebDriverContainerHolder, constructorArgs: [new GrailsGebSettings(LocalDateTime.now())])
        containerHolder.container = container
        listener = new GebRecordingTestListener(containerHolder)
    }

    void 'afterIteration() saves the recording when a recording container is available'() {
        given:
        containerHolder.settings.recordingMode = VncRecordingMode.RECORD_ALL
        containerHolder.isRecordingContainerAvailable() >> true

        when: 'the iteration completes'
        listener.afterIteration(specificationContext.currentIteration)

        then: 'the container is told which test passed, so it can name the recording after it'
        1 * container.afterTest(
                { it.filesystemFriendlyName.startsWith('GebRecordingTestListenerSpec_afterIteration_saves') },
                Optional.empty()
        )
    }

    void 'afterIteration() skips saving when no recording container is available after a failed restart'() {
        given: 'restarting the recording container before this test failed'
        containerHolder.settings.recordingMode = VncRecordingMode.RECORD_ALL
        containerHolder.isRecordingContainerAvailable() >> false

        when: 'the iteration completes'
        listener.afterIteration(specificationContext.currentIteration)

        then: 'there is nothing to save a recording from, so the container is not asked to'
        0 * container.afterTest(_, _)
        noExceptionThrown()
    }

    void 'afterIteration() still reports to the container when recording is disabled'() {
        given:
        containerHolder.settings.recordingMode = VncRecordingMode.SKIP

        when: 'the iteration completes'
        listener.afterIteration(specificationContext.currentIteration)

        then:
        1 * container.afterTest(_, _)
    }

    void 'afterIteration() propagates unexpected failures from saving the recording'() {
        given:
        containerHolder.settings.recordingMode = VncRecordingMode.RECORD_ALL
        containerHolder.isRecordingContainerAvailable() >> true
        container.afterTest(_, _) >> { throw new NullPointerException() }

        when: 'the iteration completes'
        listener.afterIteration(specificationContext.currentIteration)

        then: 'the failure is not hidden'
        thrown(NullPointerException)
    }
}
