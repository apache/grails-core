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
package example

import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.slf4j.LoggerFactory
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.FieldInfo
import org.spockframework.runtime.model.SpecInfo
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

class SpringBootStartMongoExtension implements IGlobalExtension {
    static GenericContainer dbContainer

    @Override
    void start() {
        String mongoVersion = System.getProperty("mongodbContainerVersion", "7.0.19")
        DockerImageName dockerImage = DockerImageName.parse("mongo:${mongoVersion}")
        dbContainer = new MongoDBContainer(dockerImage)
        dbContainer.start()
        dbContainer.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("testcontainers")))
    }

    @Override
    void visitSpec(SpecInfo spec) {
        spec.addSharedInitializerInterceptor { invocation ->
            FieldInfo mongoDatastoreField = spec.fields.find { it.shared && MongoDatastore.isAssignableFrom(it.type) }
            if(mongoDatastoreField) {
                mongoDatastoreField.writeValue(invocation.sharedInstance, new MongoDatastore([(MongoSettings.SETTING_HOST): dbContainer.getHost(), (MongoSettings.SETTING_PORT): dbContainer.getMappedPort(27017) as String], getClass().getPackage()))
            }
        }
    }

    @Override
    void stop() {
        if(dbContainer) {
            dbContainer.start()
        }
    }
}
