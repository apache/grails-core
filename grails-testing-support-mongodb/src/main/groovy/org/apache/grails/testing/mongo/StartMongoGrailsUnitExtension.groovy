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


import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.FieldInfo
import org.spockframework.runtime.model.SpecInfo
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

/**
 * Unit tests do not have a full server environment, and thus can have varying options for when a Mongo instance is built, reused, and torn down
 *
 * @since 7.0.0
 * @author James Daugherty
 */
class StartMongoGrailsUnitExtension extends AbstractMongoGrailsExtension implements IGlobalExtension {

    DockerImageName desiredDockerImage = getDesiredMongoDockerName()
    MongoContainerHolder containerHolder

    @Override
    void start() {
        if (isIntegrationTestRun()) {
            return
        }

        containerHolder = new MongoContainerHolder(desiredDockerImage)
    }

    @Override
    void visitSpec(SpecInfo spec) {
        if (isIntegrationTestRun()) {
            return
        }

        if (isIntegrationSpec(spec)) {
            throw new IllegalStateException("An Integration Specification was found in a Unit Test run.")
        }

        if (isAutoStartedMongoSpec(spec)) {
            // Unit tests will either start their own version or will need the configuration injected.
            if (isMongoSpec(spec)) {
                throw new IllegalStateException("A Specification may be a MongoSpec or an AutoStartedMongoSpec, but not both.")
            }
        } else {
            return
        }

        // TODO: we could collect the tests based on their requirements and then create a resource lock and spin up X
        // containers.  This would allow for much faster execution since we wouldn't be build up & tearing down
        // every time.  https://spockframework.org/spock/docs/2.0/parallel_execution.html

        spec.addSetupSpecInterceptor { invocation ->
            FieldInfo dbContainerField = spec.getSpecsBottomToTop().findResult { SpecInfo s -> s.fields.find { it.shared && MongoDBContainer.isAssignableFrom(it.type) && it.name == 'dbContainer' } }
            if (!dbContainerField) {
                throw new IllegalStateException("Unexpected State: DB Container not found.")
            }

            GenericContainer container = containerHolder.getContainer()
            dbContainerField.writeValue(invocation.sharedInstance, container)

            FieldInfo hostField = spec.getSpecsTopToBottom().findResult { SpecInfo s -> s.fields.find { it.name == 'mongoHost' } }
            if (!hostField) {
                throw new IllegalStateException("Unexpected State: Host field not found.")
            }
            hostField.writeValue(invocation.sharedInstance, container.getHost())

            FieldInfo portField = spec.getSpecsTopToBottom().findResult { SpecInfo s -> s.fields.find { it.name == 'mongoPort' } }
            if (!portField) {
                throw new IllegalStateException("Unexpected State: Port field not found.")
            }
            portField.writeValue(invocation.sharedInstance, container.getMappedPort(DEFAULT_MONGO_PORT) as String)

            AutoStartedMongoSpec autostarted = invocation.sharedInstance as AutoStartedMongoSpec
            if (autostarted.shouldInitializeDatastore()) {
                Class mongoDatastoreClass = getMongoDatastoreClass()
                if (mongoDatastoreClass) {
                    FieldInfo mongoDatastoreField = spec.getSpecsBottomToTop().findResult { SpecInfo s -> s.fields.find { is(isSharedMongoDatastore(it)) } }
                    if (mongoDatastoreField) {
                        List<Package> packages = autostarted.getMongoPackages()
                        Package[] packagesArray = packages.toArray(new Package[packages.size()])

                        Map<String, Object> configuration = ['grails.mongodb.url': createConnectionString(container.getHost(), container.getMappedPort(DEFAULT_MONGO_PORT))]
                        def datastore = mongoDatastoreClass.getDeclaredConstructor(Map<String, Object>, Package[]).newInstance(configuration, packagesArray)
                        mongoDatastoreField.writeValue(invocation.sharedInstance, datastore)
                    }
                }
            }

            invocation.proceed()
        }
    }

    Class<?> getMongoDatastoreClass() {
        try {
            return Class.forName('org.grails.datastore.mapping.mongo.MongoDatastore')
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    boolean isSharedMongoDatastore(FieldInfo field) {
        if (!field.shared) {
            return false
        }

        try {
            Class<?> mongoDatastore = Class.forName('org.grails.datastore.mapping.mongo.MongoDatastore')
            return mongoDatastore.isAssignableFrom(field.type)
        } catch (ClassNotFoundException e) {
            return false
        }
    }
}
