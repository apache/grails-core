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
package grails.gorm.tests

import com.github.dockerjava.api.exception.NotFoundException
import org.testcontainers.containers.ContainerFetchException
import org.testcontainers.containers.ContainerLaunchException
import org.testcontainers.containers.GenericContainer
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException

import spock.lang.Specification

class DatabaseContainersSpec extends Specification {

    private static final String IMAGE = 'registry.example/db:1'

    void 'a row without a container, or with one that is running, starts nothing'() {
        given:
        final GenericContainer running = Mock()

        when:
        DatabaseContainers.start(null)
        DatabaseContainers.start(running)

        then:
        1 * running.isRunning() >> true
        0 * running._
    }

    void 'the container of a row other than Db2 is started without fetching its image first'() {
        given:
        final GenericContainer container = Mock()

        when:
        DatabaseContainers.start(container)

        then:
        1 * container.isRunning() >> false

        then:
        1 * container.start()
        0 * container._
    }

    void 'a container that fails to start fails the row'() {
        given:
        final GenericContainer container = Mock()

        when:
        DatabaseContainers.start(container)

        then:
        1 * container.isRunning() >> false
        1 * container.start() >> { throw new ContainerLaunchException('Container startup failed') }
        thrown(ContainerLaunchException)
    }

    void 'an image that is fetched is not fetched again'() {
        given:
        final GenericContainer container = Mock()
        final fetch = new DatabaseContainers.ImageFetch(container, IMAGE)

        when:
        fetch.fetch()
        fetch.fetch()

        then:
        1 * container.getDockerImageName() >> IMAGE
        noExceptionThrown()
    }

    void 'when the fetch fails, every later row fails with that failure without fetching again'() {
        given:
        final GenericContainer container = Mock()
        final fetch = new DatabaseContainers.ImageFetch(container, IMAGE)
        final failure = new ContainerFetchException("Can't get Docker image", timeout())

        when:
        fetch.fetch()

        then:
        1 * container.getDockerImageName() >> { throw failure }
        final first = thrown(ContainerFetchException)
        first.cause.is(failure)

        when:
        fetch.fetch()

        then:
        0 * container.getDockerImageName()
        final later = thrown(ContainerFetchException)
        later.cause.is(failure)
    }

    void 'a failed fetch names the image and #what'() {
        given:
        final failure = new ContainerFetchException("Can't get Docker image", cause)
        final GenericContainer container = Stub() {
            getDockerImageName() >> { throw failure }
        }
        final fetch = new DatabaseContainers.ImageFetch(container, IMAGE)

        when:
        fetch.fetch()

        then:
        final e = thrown(ContainerFetchException)
        e.message == message
        e.cause.is(failure)

        where:
        what                                      | cause      || message
        'that the registry did not answer'        | timeout()  || "Pulling ${IMAGE} failed: not fulfilled within 2 minutes"
        'that the image is not there'             | notFound() || "Pulling ${IMAGE} failed: Status 404: not found"
        'the failure itself when it has no cause' | null       || "Pulling ${IMAGE} failed: Can't get Docker image"
    }

    /**
     * How Testcontainers gives up on a registry that does not answer: it retries the pull for two minutes.
     */
    private static Exception timeout() {
        new ConditionTimeoutException('not fulfilled within 2 minutes')
    }

    /**
     * How the Docker daemon reports a registry that answers that the image is not there. The exception puts the status
     * before the message.
     */
    private static Exception notFound() {
        new NotFoundException('not found')
    }
}
