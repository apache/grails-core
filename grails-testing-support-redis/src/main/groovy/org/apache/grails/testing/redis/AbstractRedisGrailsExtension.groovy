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

import org.spockframework.runtime.model.SpecInfo
import org.testcontainers.utility.DockerImageName

/**
 * Abstract class for Redis Grails extensions.
 *
 * @since 7.0.0
 */
abstract class AbstractRedisGrailsExtension {

    final static int DEFAULT_REDIS_PORT = 6379
    final static String DEFAULT_REDIS_VERSION = '7.4'
    final static String REDIS_VERSION_PROPERTY = 'redisContainerVersion'

    /**
     * Returns the desired redis version. If not set, it will be defaulted to {@code DEFAULT_REDIS_VERSION}.
     */
    static String getDesiredRedisVersion() {
        System.getProperty(REDIS_VERSION_PROPERTY, DEFAULT_REDIS_VERSION)
    }

    /**
     * Returns the configured docker image name.
     */
    static DockerImageName getDesiredRedisDockerName() {
        DockerImageName.parse("redis:${getDesiredRedisVersion()}")
    }

    /**
     * Integration tests have a special property added by the Grails Gradle plugin that ensures they can be detected
     * so different extensions can be applied on Unit vs Integration.
     */
    boolean isIntegrationTestRun() {
        Boolean.getBoolean('is.grails.integration.test') as boolean
    }

    /**
     * Determines if Redis is already running. In the event that it is, extensions will not override it.
     */
    boolean isRedisAlreadyRunning() {
        try (Socket ignored = new Socket('localhost', DEFAULT_REDIS_PORT)) {
            return true
        } catch (IOException ignored) {
            return false
        }
    }

    boolean isIntegrationSpec(SpecInfo spec) {
        try {
            Class<?> integrationSpec = Class.forName('grails.testing.mixin.integration.Integration')
            return spec.annotations.any { integrationSpec.isAssignableFrom(it.annotationType()) }
        } catch (ClassNotFoundException ignored) {
            return false
        }
    }
}
