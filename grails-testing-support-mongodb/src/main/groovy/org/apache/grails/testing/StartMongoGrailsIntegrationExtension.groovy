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

package org.apache.grails.testing


import org.spockframework.runtime.extension.IGlobalExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * While unit tests can spin up as many Mongo instances as they desire, integration tests require a fully running
 * server.  This extension will start a single MongoDB instance for the life of the entire test run if an existing
 * instance is not detected.
 *
 * @since 7.0.0
 * @author James Daugherty
 */
class StartMongoGrailsIntegrationExtension extends AbstractMongoGrailsExtension implements IGlobalExtension {
    static GenericContainer dbContainer
    static String connectionString

    @Override
    void start() {
        if(!isIntegrationTestRun()) {
            return
        }

        if(!isMongoAlreadyRunning()) {
            System.out.println("Starting MongoDB container on port ${DEFAULT_MONGO_PORT}")
            DockerImageName dockerImage = getDesiredMongoDockerName()
            dbContainer = MongoContainerHolder.startMongoContainer(dockerImage)

            System.setProperty('grails.mongodb.host', dbContainer.getHost())
            System.setProperty('grails.mongodb.port', dbContainer.getMappedPort(DEFAULT_MONGO_PORT) as String)
            connectionString = createConnectionString(dbContainer.getHost(), dbContainer.getMappedPort(DEFAULT_MONGO_PORT))
            System.setProperty('grails.mongodb.url', connectionString)
        }
        else {
            // Assume the defaults, for consistency, set the same variables
            System.out.println("MongoDB is already running on localhost:${DEFAULT_MONGO_PORT}")

            System.setProperty('grails.mongodb.host', 'localhost')
            System.setProperty('grails.mongodb.port', DEFAULT_MONGO_PORT as String)
            connectionString = "mongodb://localhost:${DEFAULT_MONGO_PORT as String}/myDb" as String
            System.setProperty('spring.data.mongodb.uri', connectionString)
        }
    }

    // TODO: We could add validations for IsAutoStartedMongoSpec to ensure these are never assigned to an integration test

    @Override
    void stop() {
        if(dbContainer) {
            dbContainer.start()
        }
    }
}
