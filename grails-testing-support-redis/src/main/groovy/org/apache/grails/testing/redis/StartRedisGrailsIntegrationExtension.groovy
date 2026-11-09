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
import org.spockframework.runtime.extension.IGlobalExtension
import org.testcontainers.utility.DockerImageName

/**
 * Integration tests require a fully running server. This extension will start a single Redis instance for the life of
 * the entire test run if an existing instance is not detected, exposing it to the application via the {@code REDIS_HOST}
 * and {@code REDIS_PORT} system properties (which the Grails redis configuration reads).
 *
 * @since 7.0.0
 */
class StartRedisGrailsIntegrationExtension extends AbstractRedisGrailsExtension implements IGlobalExtension {

    static RedisContainer redisContainer

    @Override
    void start() {
        if (!isIntegrationTestRun()) {
            return
        }

        if (!isRedisAlreadyRunning()) {
            DockerImageName dockerImage = getDesiredRedisDockerName()
            System.out.println("Starting Redis container from image ${dockerImage}")
            redisContainer = RedisContainerHolder.startRedisContainer(dockerImage)

            System.setProperty('REDIS_HOST', redisContainer.getHost())
            System.setProperty('REDIS_PORT', redisContainer.getFirstMappedPort() as String)
        } else {
            // Assume the defaults, for consistency, set the same variables
            System.out.println("Redis is already running on localhost:${DEFAULT_REDIS_PORT}")

            System.setProperty('REDIS_HOST', 'localhost')
            System.setProperty('REDIS_PORT', DEFAULT_REDIS_PORT as String)
        }
    }

    @Override
    void stop() {
        redisContainer?.stop()
    }
}
