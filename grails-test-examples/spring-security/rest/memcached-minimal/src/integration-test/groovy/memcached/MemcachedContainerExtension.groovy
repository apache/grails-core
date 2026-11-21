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

package memcached

import groovy.transform.CompileStatic

import org.spockframework.runtime.extension.IGlobalExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * Starts one memcached server in a container for the specs of this application and stops it when they are done, as
 * {@code StartRedisGrailsIntegrationExtension} does for Redis. The application reads where the server listens from the
 * {@code MEMCACHED_HOSTS} system property, see {@code application.groovy}.
 *
 * <p>Without a container runtime nothing is started and the specs, which require {@link #isDockerAvailable()}, are
 * skipped.</p>
 */
@CompileStatic
class MemcachedContainerExtension implements IGlobalExtension {

    static final String HOSTS_PROPERTY = 'MEMCACHED_HOSTS'

    private static final String IMAGE = 'memcached:1.6.39-alpine@sha256:c8503d4491edd3110cc07d0465089d3a41cf1daf8645e71149e39a51835e92cd'
    private static final int MEMCACHED_PORT = 11211

    private static GenericContainer container

    @Override
    void start() {
        if (!isDockerAvailable()) {
            return
        }
        GenericContainer memcached = new GenericContainer(DockerImageName.parse(IMAGE))
        memcached.withExposedPorts(MEMCACHED_PORT)
        memcached.waitingFor(Wait.forListeningPort())
        memcached.start()
        container = memcached
        System.setProperty(HOSTS_PROPERTY, "${memcached.host}:${memcached.getMappedPort(MEMCACHED_PORT)}".toString())
    }

    @Override
    void stop() {
        container?.stop()
        container = null
        System.clearProperty(HOSTS_PROPERTY)
    }

    /**
     * Mirrors the probe of the CAS and Hibernate 7 specs. Checking the socket avoids the macOS failure mode where
     * asking Testcontainers for a client throws when the daemon API version differs.
     */
    static boolean isDockerAvailable() {
        List<String> candidates = [
                System.getProperty('user.home') + '/.docker/run/docker.sock',
                '/var/run/docker.sock',
                System.getenv('DOCKER_HOST') ?: ''
        ]
        candidates.any { String candidate -> candidate && new File(candidate).exists() }
    }
}
