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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.PackageScope

import com.github.dockerjava.api.model.Ulimit
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

/**
 * Starts one MongoDB container for each thread that asks for one, and stops all of them together.
 */
class MongoContainerHolder {

    // by thread rather than in a ThreadLocal: stop() is called from another thread than the ones that run the specs
    private final Map<Thread, GenericContainer> containers = new ConcurrentHashMap<Thread, GenericContainer>()
    final DockerImageName desiredImage

    /**
     * Docker's default soft limit of 1024 open files is easily exhausted by WiredTiger, and a crashed mongod leaves
     * the test worker waiting for server selection indefinitely
     */
    private static final long OPEN_FILES_LIMIT = 65536L

    MongoContainerHolder(DockerImageName desiredImage) {
        this.desiredImage = desiredImage
    }

    @PackageScope
    static GenericContainer startMongoContainer(DockerImageName dockerImageName) {
        GenericContainer dbContainer = new MongoDBContainer(dockerImageName)
                .withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig.withUlimits([new Ulimit('nofile', OPEN_FILES_LIMIT, OPEN_FILES_LIMIT)])
                }
        try {
            dbContainer.start()
        }
        catch (Throwable e) {
            // a start that failed can leave the container it created behind
            dbContainer.stop()
            throw e
        }
        dbContainer.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger('testcontainers')))
        dbContainer
    }

    /**
     * @return The container of the calling thread, started if the thread has not asked for it yet
     */
    GenericContainer getContainer() {
        Thread thread = Thread.currentThread()
        GenericContainer foundContainer = containers.get(thread)
        if (foundContainer) {
            return foundContainer
        }

        GenericContainer startedContainer = startMongoContainer(desiredImage)
        containers.put(thread, startedContainer)
        startedContainer
    }

    /**
     * Stops every container this holder started, whichever thread it was started for. A thread that asks for a
     * container after a successful stop is given a new one. If a stop fails, the remaining containers are still
     * stopped before the first failure is rethrown, with later failures suppressed. Failed containers are kept
     * so that a subsequent call can retry their stops.
     */
    void stop() {
        Throwable failure = null
        Iterator<GenericContainer> started = containers.values().iterator()
        while (started.hasNext()) {
            GenericContainer container = started.next()
            try {
                container.stop()
                started.remove()
            }
            catch (Throwable e) {
                if (failure == null) {
                    failure = e
                }
                else if (!failure.is(e)) {
                    failure.addSuppressed(e)
                }
            }
        }
        if (failure != null) {
            throw failure
        }
    }
}
