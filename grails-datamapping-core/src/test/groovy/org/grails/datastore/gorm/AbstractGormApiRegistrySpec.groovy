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
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import spock.lang.Specification

class AbstractGormApiRegistrySpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    void "test register and get"() {
        given:
        def registry = GormRegistry.instance
        def testRegistry = new TestGormApiRegistry(registry)
        def api = new DummyApi(Stub(Datastore))

        when: "registering a valid api"
        testRegistry.register(TestEntity.name, api)

        then:
        testRegistry.get(TestEntity.name) == api
        testRegistry.size() == 1
        testRegistry.containsKey(TestEntity.name)
        testRegistry.keySet().contains(TestEntity.name)

        when: "registering with null class name"
        testRegistry.register("   ", new DummyApi(Stub(Datastore)))

        then: "it is ignored"
        testRegistry.size() == 1

        when: "registering with null api"
        testRegistry.register("SomeOtherClass", null)

        then: "it is ignored"
        testRegistry.size() == 1
    }

    void "test get with qualifier"() {
        given:
        def registry = GormRegistry.instance
        def testRegistry = new TestGormApiRegistry(registry)
        def defaultDatastore = Stub(Datastore)
        def secondaryDatastore = Stub(Datastore)
        
        def api = new DummyApi(defaultDatastore)
        testRegistry.register(TestEntity.name, api)
        
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerDatastore("secondary", secondaryDatastore)
        
        registry.registerEntityDatastore(TestEntity.name, ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerEntityDatastore(TestEntity.name, "secondary", secondaryDatastore)

        when: "getting with default qualifier"
        def defaultApi = testRegistry.get(TestEntity.name, ConnectionSource.DEFAULT)

        then:
        defaultApi == api

        when: "getting with secondary qualifier"
        def secondaryApi = testRegistry.get(TestEntity.name, "secondary")

        then:
        secondaryApi != api
        secondaryApi instanceof AbstractDatastoreApi
        testRegistry.qualifiedApi != null // To ensure qualify was called
    }

    void "test get with qualifier when datastore is the same"() {
        given:
        def registry = GormRegistry.instance
        def testRegistry = new TestGormApiRegistry(registry)
        def defaultDatastore = Stub(Datastore)
        
        def api = new DummyApi(defaultDatastore)
        testRegistry.register(TestEntity.name, api)
        
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerDatastore("secondary", defaultDatastore)
        
        registry.registerEntityDatastore(TestEntity.name, ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerEntityDatastore(TestEntity.name, "secondary", defaultDatastore)

        when: "getting with secondary qualifier but datastore is identical"
        def secondaryApi = testRegistry.get(TestEntity.name, "secondary")

        then: "the original api is returned without calling qualify"
        secondaryApi == api
    }

    void "test clear"() {
        given:
        def testRegistry = new TestGormApiRegistry(GormRegistry.instance)
        testRegistry.register(TestEntity.name, new DummyApi(Stub(Datastore)))

        when:
        testRegistry.clear()

        then:
        testRegistry.size() == 0
        !testRegistry.containsKey(TestEntity.name)
    }

    void "test className helper"() {
        given:
        def testRegistry = new TestGormApiRegistry(GormRegistry.instance)

        expect:
        testRegistry.getClassName(TestEntity) == TestEntity.name
    }

    void "test stateException helper"() {
        given:
        def testRegistry = new TestGormApiRegistry(GormRegistry.instance)

        when:
        def ex = testRegistry.getStateException(TestEntity)

        then:
        ex.message == "No GORM implementation configured for class [${TestEntity.name}]. Ensure GORM has been initialized correctly"
    }

    static class TestEntity {
    }
    
    static class DummyApi extends AbstractDatastoreApi {
        DummyApi(Datastore ds) {
            super(ds)
        }
    }

    static class TestGormApiRegistry extends AbstractGormApiRegistry<AbstractDatastoreApi> {
        AbstractDatastoreApi qualifiedApi

        TestGormApiRegistry(GormRegistry registry) {
            super(registry)
        }

        @Override
        protected AbstractDatastoreApi qualify(AbstractDatastoreApi api, String qualifier) {
            qualifiedApi = new DummyApi(api.datastore)
            return qualifiedApi
        }

        String getClassName(Class entity) {
            return className(entity)
        }

        IllegalStateException getStateException(Class entity) {
            return stateException(entity)
        }
    }
}
