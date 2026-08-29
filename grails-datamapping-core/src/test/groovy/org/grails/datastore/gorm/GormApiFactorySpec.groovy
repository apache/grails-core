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
package org.grails.datastore.gorm

import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

/**
 * Tests for GormApiFactory interface contract
 */
class GormApiFactorySpec extends Specification {

    void 'factory creates GormStaticApi instances'() {
        given:
        GormApiFactory factory = new MockGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        GormStaticApi<TestEntity> staticApi = factory.createStaticApi(
            TestEntity,
            mappingContext,
            resolver,
            'default',
            GormRegistry.instance
        )

        then:
        staticApi != null
        staticApi instanceof GormStaticApi
    }

    void 'factory creates GormInstanceApi instances'() {
        given:
        GormApiFactory factory = new MockGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        GormInstanceApi<TestEntity> instanceApi = factory.createInstanceApi(
            TestEntity,
            mappingContext,
            resolver,
            GormRegistry.instance,
            true,
            false
        )

        then:
        instanceApi != null
        instanceApi instanceof GormInstanceApi
    }

    void 'factory creates GormValidationApi instances'() {
        given:
        GormApiFactory factory = new MockGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        GormValidationApi<TestEntity> validationApi = factory.createValidationApi(
            TestEntity,
            mappingContext,
            resolver,
            GormRegistry.instance
        )

        then:
        validationApi != null
        validationApi instanceof GormValidationApi
    }

    void 'factory creates dynamic finders'() {
        given:
        GormApiFactory factory = new MockGormApiFactory()
        MappingContext mappingContext = Mock(MappingContext)
        DatastoreResolver resolver = Stub(DatastoreResolver)

        when:
        List<FinderMethod> finders = factory.createDynamicFinders(resolver, mappingContext)

        then:
        finders != null
        finders instanceof List
    }

    static class TestEntity {
        String name
    }

    static class MockGormApiFactory implements GormApiFactory {
        @Override
        <D> GormStaticApi<D> createStaticApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver resolver, String qualifier, GormRegistry registry) {
            new GormStaticApi<D>(persistentClass, mappingContext, [], resolver, qualifier, registry)
        }

        @Override
        <D> GormInstanceApi<D> createInstanceApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver resolver, GormRegistry registry, boolean failOnError, boolean markDirty) {
            GormInstanceApi<D> api = new GormInstanceApi<D>(persistentClass, mappingContext, resolver, registry)
            api.failOnError = failOnError
            api.markDirty = markDirty
            return api
        }

        @Override
        <D> GormValidationApi<D> createValidationApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver resolver, GormRegistry registry) {
            new GormValidationApi<D>(persistentClass, mappingContext, resolver, registry)
        }

        @Override
        List<FinderMethod> createDynamicFinders(DatastoreResolver datastoreResolver, MappingContext mappingContext) {
            []
        }
    }
}
