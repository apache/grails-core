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
package org.grails.datastore.gorm.mongodb.springdata

import java.util.function.Supplier

import com.mongodb.MongoClientSettings

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import spock.lang.Specification

/**
 * Exercises the auto-configuration through a real Spring Boot {@link ApplicationContextRunner} so the
 * conditions, ordering and {@code @ConditionalOnMissingBean} back-off are actually evaluated - the
 * earlier specs only invoked the {@code @Bean} methods directly and so could not have caught a
 * mis-registered or mis-conditioned auto-configuration. Runs offline (a lazily-created client; no
 * MongoDB operations are issued).
 */
class SpringDataMongoGormAutoConfigurationContextSpec extends Specification {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringDataMongoGormAutoConfiguration))

    private static MongoDatastore newDatastore() {
        new MongoDatastore(MongoClientSettings.builder(), DatastoreUtils.createPropertyResolver([:]), new MongoMappingContext("test"))
    }

    void "test the interop beans are wired when a MongoDatastore bean is present"() {
        given:
        MongoDatastore datastore = newDatastore()

        expect:
        runner.withBean(MongoDatastore, { datastore } as Supplier).run { context ->
            assert context.getBeanNamesForType(MongoDatabaseFactory).length == 1
            assert context.getBean(MongoTemplate) != null
            assert context.getBean('transactionManager') instanceof GormSharedSessionMongoTransactionManager
        }

        cleanup:
        datastore.close()
    }

    void "test the auto-configuration does not activate without a MongoDatastore bean"() {
        expect:
        runner.run { context ->
            assert context.getBeanNamesForType(MongoTemplate).length == 0
            assert context.getBeanNamesForType(MongoDatabaseFactory).length == 0
            assert !context.containsBean('transactionManager')
        }
    }

    void "test an application-defined mongoTemplate backs off the auto-configured one"() {
        given:
        MongoDatastore datastore = newDatastore()
        MongoTemplate applicationTemplate = new MongoTemplate(
                new SimpleMongoClientDatabaseFactory(datastore.mongoClient, datastore.defaultDatabase))

        expect:
        runner.withBean(MongoDatastore, { datastore } as Supplier)
                .withBean('mongoTemplate', MongoTemplate, { applicationTemplate } as Supplier)
                .run { context ->
                    assert context.getBean('mongoTemplate').is(applicationTemplate)
                    assert context.getBeanNamesForType(MongoTemplate).length == 1
                }

        cleanup:
        datastore.close()
    }
}
