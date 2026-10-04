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
package org.grails.datastore.gorm.mongodb.boot.autoconfigure.discovered

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.annotation.ImportCandidates
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Service

import org.apache.grails.testing.mongo.AbstractMongoGrailsExtension
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.gorm.mongodb.boot.autoconfigure.MongoDbGormAutoConfiguration
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * Spring Boot finds an auto-configuration by reading
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}. Nothing here imports
 * {@link MongoDbGormAutoConfiguration}, so the application gets GORM only if that file names it.
 */
class MongoDbGormAutoConfigurationDiscoverySpec extends AutoStartedMongoSpec {

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void 'the GORM for MongoDB auto-configuration is registered where Spring Boot reads it'() {
        expect:
        MongoDbGormAutoConfiguration.name in ImportCandidates.load(AutoConfiguration, getClass().classLoader).candidates
    }

    void 'a Spring Boot application gets GORM for MongoDB without importing anything'() {
        when:
        ConfigurableApplicationContext context = new SpringApplicationBuilder(DiscoveringApplication)
                .web(WebApplicationType.NONE)
                .run("--spring.mongodb.host=${dbContainer.host}",
                        "--spring.mongodb.port=${dbContainer.getMappedPort(AbstractMongoGrailsExtension.DEFAULT_MONGO_PORT)}",
                        '--spring.mongodb.database=discoveredDb')

        then:
        context.getBean(MongoDatastore)

        and: 'GORM reads and writes through it'
        context.getBean(DiscoveredThingService).saveOne('found') == 1

        cleanup:
        context?.close()
    }
}

@SpringBootApplication
class DiscoveringApplication {
}

@Entity
class DiscoveredThing {
    String name
}

/**
 * Not a GORM data service, which the auto-configuration would register itself: an ordinary bean, so that it is there
 * whether or not GORM is.
 */
@Service
class DiscoveredThingService {

    /**
     * @return how many more there are than before
     */
    @Transactional
    long saveOne(String name) {
        long before = DiscoveredThing.count()
        new DiscoveredThing(name: name).save(flush: true, failOnError: true)
        DiscoveredThing.count() - before
    }
}
