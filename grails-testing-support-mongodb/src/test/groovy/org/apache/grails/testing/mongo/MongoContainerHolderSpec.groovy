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

import org.testcontainers.containers.GenericContainer
import spock.lang.Requires
import spock.lang.Specification

class MongoContainerHolderSpec extends Specification {

    MongoContainerHolder holder = new MongoContainerHolder(AbstractMongoGrailsExtension.desiredMongoDockerName)

    void cleanup() {
        holder.stop()
    }

    @Requires({ os.linux || !System.getenv().containsKey('CI') })
    void 'a thread is given the same running container each time it asks'() {
        when: 'the same thread asks twice'
        GenericContainer first = holder.container
        GenericContainer second = holder.container

        then: 'one container was started'
        first.running
        second.is(first)
    }

    @Requires({ os.linux || !System.getenv().containsKey('CI') })
    void 'stop, called from another thread, stops the container and the next request starts a new one'() {
        given: 'a container started for this thread'
        GenericContainer container = holder.container

        when: 'another thread stops the holder, as the thread that ends a test run does'
        Thread stopper = new Thread({ holder.stop() } as Runnable)
        stopper.start()
        stopper.join()

        then: 'the container no longer runs'
        !container.running

        when: 'this thread asks again'
        GenericContainer next = holder.container

        then: 'it is given a new, running container'
        !next.is(container)
        next.running
    }

    void 'stop does nothing when no container was started'() {
        when:
        holder.stop()

        then:
        noExceptionThrown()
    }
}
