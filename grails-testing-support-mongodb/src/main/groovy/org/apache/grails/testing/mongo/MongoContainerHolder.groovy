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

import groovy.transform.PackageScope
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

class MongoContainerHolder {
    private ThreadLocal<GenericContainer> containers = new ThreadLocal<GenericContainer>()
    final DockerImageName desiredImage

    MongoContainerHolder(DockerImageName desiredImage) {
        this.desiredImage = desiredImage
    }

    @PackageScope
    static GenericContainer startMongoContainer(DockerImageName dockerImageName) {
        GenericContainer dbContainer = new MongoDBContainer(dockerImageName)
        dbContainer.start()
        dbContainer.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("testcontainers")))
        dbContainer
    }

    GenericContainer getContainer() {
        GenericContainer foundContainer = containers.get()
        if(foundContainer) {
            return foundContainer
        }

        GenericContainer startedContainer = startMongoContainer(desiredImage)
        containers.set(startedContainer)
        startedContainer
    }

    void stop() {
        containers.get()?.stop()
        containers.remove()
    }
}
