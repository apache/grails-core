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

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import groovy.transform.CompileStatic
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

@CompileStatic
class Application extends GrailsAutoConfiguration {
    static GenericContainer dbContainer = null

    static void main(String[] args) {
        String mongoVersion = System.getProperty("mongodbContainerVersion", "7.0.19")
        DockerImageName dockerImage = DockerImageName.parse("mongo:${mongoVersion}")
        dbContainer = new MongoDBContainer(dockerImage)
        dbContainer.start()
        dbContainer.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("testcontainers")))

        System.setProperty("docker.mongodb.port", dbContainer.getMappedPort(27017).toString())
        System.setProperty("docker.mongodb.hostname", dbContainer.getHost())

        GrailsApp.run(Application, args)
    }

    @Override
    void onShutdown(Map<String, Object> event) {
        if(dbContainer) {
            dbContainer.start()
        }
    }
}
