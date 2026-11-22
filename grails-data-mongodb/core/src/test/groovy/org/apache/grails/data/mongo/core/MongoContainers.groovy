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

package org.apache.grails.data.mongo.core

import groovy.transform.CompileStatic

import com.github.dockerjava.api.model.Ulimit
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.slf4j.LoggerFactory
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.output.Slf4jLogConsumer

import org.apache.grails.testing.mongo.AbstractMongoGrailsExtension

/**
 * One MongoDB container per test JVM, shared by all the specs it runs, as {@code DatabaseContainers} is for the SQL
 * databases. A spec used to start a replica-set mongod of its own, and the specs of each of the test workers did so
 * one after the other: with the seven workers Gradle starts on a ten core machine, that is seven mongod replica sets
 * starting at once and a container pile-up when a start failed, because the container was only stopped in
 * {@code cleanupSpec}, which does not run when {@code setupSpec} throws. The first spec to ask starts the container,
 * every later one finds it running, and none stops it: Testcontainers removes it when the JVM exits. The specs of one
 * JVM run one after the other, and each leaves the server empty with {@link #dropUserDatabases}.
 */
@CompileStatic
final class MongoContainers {

    /**
     * Docker's default soft limit of 1024 open files is easily exhausted by WiredTiger, and a crashed mongod leaves
     * the test worker waiting for server selection indefinitely
     */
    private static final long OPEN_FILES_LIMIT = 65536L

    private static final List<String> SYSTEM_DATABASES = ['admin', 'config', 'local']

    private static MongoDBContainer shared

    private MongoContainers() {
    }

    /**
     * @return The container of this JVM, started if no spec has asked for it yet
     */
    static synchronized MongoDBContainer shared() {
        if (shared == null) {
            MongoDBContainer container = new MongoDBContainer(AbstractMongoGrailsExtension.desiredMongoDockerName)
                    .withCreateContainerCmdModifier { cmd ->
                        cmd.hostConfig.withUlimits([new Ulimit('nofile', OPEN_FILES_LIMIT, OPEN_FILES_LIMIT)])
                    }
            try {
                container.start()
            }
            catch (Throwable e) {
                // a container whose start failed is not left to Testcontainers' cleanup at the end of the JVM
                container.stop()
                throw e
            }
            container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger('testcontainers')))
            shared = container
        }
        shared
    }

    /**
     * Drops every database but the system ones, so the next spec finds the server as the first one did.
     *
     * @param container The container to empty
     */
    static void dropUserDatabases(MongoDBContainer container) {
        MongoClient client = MongoClients.create(container.replicaSetUrl)
        try {
            for (String name : client.listDatabaseNames()) {
                if (!SYSTEM_DATABASES.contains(name)) {
                    client.getDatabase(name).drop()
                }
            }
        }
        finally {
            client.close()
        }
    }
}
