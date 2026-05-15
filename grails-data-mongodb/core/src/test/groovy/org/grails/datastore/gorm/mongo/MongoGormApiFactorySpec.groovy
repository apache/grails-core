/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.mongo

import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.mongo.api.MongoStaticApi
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

/**
 * Tests for MongoGormApiFactory
 */
class MongoGormApiFactorySpec extends Specification {

    void 'createStaticApi returns MongoStaticApi instance'() {
        given:
        MongoGormApiFactory factory = new MongoGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)
        String qualifier = 'default'

        when:
        def staticApi = factory.createStaticApi(
            TestEntity,
            mappingContext,
            resolver,
            qualifier,
            GormRegistry.instance
        )

        then:
        staticApi != null
        staticApi instanceof MongoStaticApi
        staticApi.persistentClass == TestEntity
    }

    void 'createStaticApi creates finders'() {
        given:
        MongoGormApiFactory factory = new MongoGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        def staticApi = factory.createStaticApi(
            TestEntity,
            mappingContext,
            resolver,
            'default',
            GormRegistry.instance
        )

        then:
        staticApi.finders.size() > 0
    }

    void 'createInstanceApi uses parent factory behavior'() {
        given:
        MongoGormApiFactory factory = new MongoGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        def instanceApi = factory.createInstanceApi(
            TestEntity,
            mappingContext,
            resolver,
            GormRegistry.instance,
            true,
            false
        )

        then:
        instanceApi != null
        instanceApi.failOnError
        !instanceApi.markDirty
    }

    void 'createValidationApi uses parent factory behavior'() {
        given:
        MongoGormApiFactory factory = new MongoGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        def validationApi = factory.createValidationApi(
            TestEntity,
            mappingContext,
            resolver,
            GormRegistry.instance
        )

        then:
        validationApi != null
    }

    static class TestEntity {

        String name
        Integer age
    }
}
