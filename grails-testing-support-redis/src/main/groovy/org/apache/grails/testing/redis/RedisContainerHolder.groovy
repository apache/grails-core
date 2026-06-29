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

package org.apache.grails.testing.redis

import com.redis.testcontainers.RedisContainer
import groovy.transform.PackageScope
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

class RedisContainerHolder {

    private ThreadLocal<RedisContainer> containers = new ThreadLocal<RedisContainer>()
    final DockerImageName desiredImage

    RedisContainerHolder(DockerImageName desiredImage) {
        this.desiredImage = desiredImage
    }

    @PackageScope
    static RedisContainer startRedisContainer(DockerImageName dockerImageName) {
        RedisContainer redisContainer = new RedisContainer(dockerImageName)
        redisContainer.start()
        redisContainer.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger('testcontainers')))
        redisContainer
    }

    RedisContainer getContainer() {
        RedisContainer foundContainer = containers.get()
        if (foundContainer) {
            return foundContainer
        }

        RedisContainer startedContainer = startRedisContainer(desiredImage)
        containers.set(startedContainer)
        startedContainer
    }

    void stop() {
        containers.get()?.stop()
        containers.remove()
    }
}
