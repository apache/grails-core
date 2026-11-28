/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.testing.mongo

import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

import org.testcontainers.containers.GenericContainer
import spock.lang.Requires
import spock.lang.Specification

class StartMongoGrailsUnitExtensionSpec extends Specification {

    StartMongoGrailsUnitExtension extension = new StartMongoGrailsUnitExtension()

    void cleanup() {
        extension.stop()
    }

    @Requires({ os.linux || !System.getenv().containsKey('CI') })
    void 'stop stops the container the specifications of the run shared'() {
        given: 'a started extension whose container a specification used'
        extension.start()
        GenericContainer container = extension.containerHolder.container

        when: 'the test run ends'
        extension.stop()

        then: 'the container no longer runs'
        !container.running
    }

    @Requires({ os.linux || !System.getenv().containsKey('CI') })
    void 'the shared container is the running container the specifications of this thread are given'() {
        when: 'test support asks for the shared container twice'
        GenericContainer first = StartMongoGrailsUnitExtension.sharedContainer
        GenericContainer second = StartMongoGrailsUnitExtension.sharedContainer

        then: 'one container runs'
        first.running
        second.is(first)

        when: 'another extension instance is started and stopped'
        extension.start()
        extension.stop()

        then: 'the container of the test run is not affected'
        first.running
        StartMongoGrailsUnitExtension.sharedContainer.is(first)
    }

    @Requires({ os.linux || !System.getenv().containsKey('CI') })
    void 'test support on another thread reuses the container of that thread through the active extension'() {
        when: 'a helper on another thread asks the public accessor twice'
        FutureTask<List<GenericContainer>> request = new FutureTask<List<GenericContainer>>({
            [StartMongoGrailsUnitExtension.sharedContainer, StartMongoGrailsUnitExtension.sharedContainer]
        } as Callable<List<GenericContainer>>)
        new Thread(request).start()
        List<GenericContainer> containers = request.get(120, TimeUnit.SECONDS)

        then: 'both requests use the active extension and share one running container'
        containers[0].running
        containers[1].is(containers[0])

        and: 'the specification thread has its own container'
        !StartMongoGrailsUnitExtension.sharedContainer.is(containers[0])
    }

    void 'starting and stopping an unused extension does not need a container'() {
        when: 'a test run starts and ends without a specification requesting MongoDB'
        extension.start()
        extension.stop()

        then:
        noExceptionThrown()
    }

    void 'stop does nothing when the extension was never started'() {
        when:
        extension.stop()

        then:
        noExceptionThrown()
    }
}
