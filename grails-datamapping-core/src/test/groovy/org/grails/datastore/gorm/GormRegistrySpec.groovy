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

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenantHolder
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification

class GormRegistrySpec extends Specification {

    void setup() {
        GormRegistry.instance.reset()
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    void "reset clears all registries"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)
        registry.reset()

        then:
        registry.allDatastores.isEmpty()
    }

    void "findSingleTransactionManager returns null for non-transactional datastore"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)

        then:
        registry.findSingleTransactionManager() == null
    }

    void "findSingleTransactionManager returns transaction manager for TransactionCapableDatastore"() {
        given:
        def txManager = Stub(PlatformTransactionManager)
        def datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)

        then:
        registry.findSingleTransactionManager() == txManager
    }

    void "findSingleTransactionManager with connectionName returns transaction manager"() {
        given:
        def txManager = Stub(PlatformTransactionManager)
        def datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        def registry = GormRegistry.instance

        when:
        registry.registerDatastore("ds1", datastore)

        then:
        registry.findSingleTransactionManager("ds1") == txManager
    }

    void "findTransactionManager returns transaction manager for entity"() {
        given:
        def txManager = Stub(PlatformTransactionManager)
        def datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)
        // Register entity datastore directly to avoid GormEnhancer complexity
        registry.registerEntityDatastore(TestEntity.name, ConnectionSource.DEFAULT, datastore)

        then:
        registry.findTransactionManager(TestEntity) == txManager
    }
    
    void "removeEntityDatastore removes datastore specifically for entity"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)
        registry.registerEntityDatastore(TestEntity.name, ConnectionSource.DEFAULT, datastore)
        registry.removeEntityDatastore(TestEntity.name, datastore)

        then:
        registry.getDatastore(TestEntity.name) == null
    }

    void "removeDatastoreByType removes from type registry but keeps in allDatastores"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)
        registry.removeDatastoreByType(datastore.getClass())

        then:
        registry.allDatastores.contains(datastore)
        !registry.datastoresByType.containsKey(datastore.getClass())
    }

    void "removeDatastoreFromDiscovery removes from type registry and allDatastores"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)
        registry.removeDatastoreFromDiscovery(datastore)

        then:
        !registry.allDatastores.contains(datastore)
        !registry.datastoresByType.containsKey(datastore.getClass())
    }

    void "removeDatastore removes from all registries"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)
        registry.removeDatastore(datastore)

        then:
        registry.allDatastores.isEmpty()
        registry.datastoresByQualifier.isEmpty()
    }

    void "normalizeEntityKey properly normalizes class names"() {
        given:
        def registry = GormRegistry.instance

        expect:
        registry.normalizeEntityKey(TestEntity) == TestEntity.name
        registry.normalizeEntityKey(TestEntity.name) == TestEntity.name
        registry.normalizeEntityKey(null) == null
    }

    void "normalizeQualifier properly normalizes qualifiers"() {
        given:
        def registry = GormRegistry.instance

        expect:
        registry.normalizeQualifier(null) == ConnectionSource.DEFAULT
        registry.normalizeQualifier("") == ConnectionSource.DEFAULT
        registry.normalizeQualifier("ds1") == "ds1"
    }

    void "registerDatastoreByQualifier only registers by qualifier"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.registerDatastoreByQualifier("ds1", datastore)

        then:
        registry.datastoresByQualifier.get("ds1") == datastore
        !registry.allDatastores.contains(datastore)
    }

    void "getApiFactory falls back to parent type or default if specific type is not registered"() {
        given:
        def datastore1 = Stub(Datastore1)
        def datastore2 = Stub(Datastore2)
        def factory = Stub(GormApiFactory)
        def registry = GormRegistry.instance

        when:
        registry.registerApiFactory(Datastore1, factory)

        then:
        registry.getApiFactory(datastore1) == factory
        registry.getApiFactory(datastore2) instanceof DefaultGormApiFactory
    }

    void "test withTenant and exists with multi-tenant entity in DISCRIMINATOR mode"() {
        given:
        def datastore = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            getConnectionSources() >> Stub(ConnectionSources) {
                getDefaultConnectionSource() >> Stub(ConnectionSource) {
                    getName() >> "default"
                }
            }
        }
        def mappingContext = Stub(org.grails.datastore.mapping.model.MappingContext)
        def entity = Stub(PersistentEntity) {
            getName() >> "TestEntity"
            getJavaClass() >> TestEntity
            isMultiTenant() >> true
            getMappingContext() >> mappingContext
        }
        
        def registry = GormRegistry.instance
        registry.registerDatastore("default", datastore)
        
        def staticApi = new GormStaticApi(TestEntity, mappingContext, [], new DatastoreResolver() {
            @Override Datastore resolve() { return datastore }
        }, ConnectionSource.DEFAULT, registry)
        
        TestEntity.metaClass.static.getGormPersistentEntity = { entity }
        registry.registerApi(TestEntity.name, staticApi, null, null)

        when: "Calling exists via withTenant"
        def capturedTenantId = null
        // Capture tenant ID during call to connect() which is called by execute()
        datastore.connect() >> {
            capturedTenantId = CurrentTenantHolder.get(datastore)
            return Stub(Session) {
                getDatastore() >> datastore
            }
        }

        Tenants.withId(datastore, "initial") {
             staticApi.withTenant("tenant1").exists(1L)
        }

        then: "The tenant context was correctly set during the call"
        capturedTenantId == "tenant1"
        
        cleanup:
        registry.metaClass = null
        TestEntity.metaClass = null
    }

    void "findTransactionManager with qualifier returns transaction manager"() {
        given:
        def txManager = Stub(PlatformTransactionManager)
        def datastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> txManager
        }
        def registry = GormRegistry.instance

        when:
        registry.registerDatastore("ds1", datastore)
        registry.registerEntityDatastore(TestEntity.name, "ds1", datastore)

        then:
        registry.findTransactionManager(TestEntity, "ds1") == txManager
    }

    void "registerEntityApis and resolve APIs works as expected"() {
        given:
        def registry = GormRegistry.instance
        def datastore = Stub(Datastore)
        def validationApi = new GormValidationApi(TestEntity, datastore, registry)
        def staticApi = new GormStaticApi(TestEntity, null, [], new DatastoreResolver() {
            @Override Datastore resolve() { return datastore }
        }, ConnectionSource.DEFAULT, registry)
        def instanceApi = new GormInstanceApi(TestEntity, datastore, registry)

        when:
        registry.registerEntityApis(TestEntity, staticApi, instanceApi, validationApi)

        then:
        registry.resolveValidationApi(TestEntity) == validationApi
        registry.resolveStaticApi(TestEntity) == staticApi
        registry.resolveInstanceApi(TestEntity) == instanceApi
    }

    void "registerDatastoreByType registers datastore in discovery"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.registerDatastoreByType(datastore)

        then:
        registry.allDatastores.contains(datastore)
        registry.datastoresByType.get(datastore.getClass()) == datastore
    }

    void "removeDatastoreByType(Datastore) removes from type registry"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)
        registry.removeDatastoreByType(datastore)

        then:
        registry.allDatastores.contains(datastore)
        !registry.datastoresByType.containsKey(datastore.getClass())
    }

    void "getDatastore with entity Class returns registered datastore"() {
        given:
        def datastore = Stub(Datastore)
        def registry = GormRegistry.instance

        when:
        registry.initializeDatastore(datastore)
        registry.registerEntityDatastore(TestEntity.name, ConnectionSource.DEFAULT, datastore)

        then:
        registry.getDatastore(TestEntity) == datastore
    }

    void "createDynamicFinders delegates to datastore api factory"() {
        given:
        def registry = GormRegistry.instance
        def mappingContext = Stub(org.grails.datastore.mapping.model.MappingContext)
        def datastore = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        def resolver = new DatastoreResolver() {
            @Override Datastore resolve() { datastore }
        }

        when:
        def finders = registry.createDynamicFinders(resolver, mappingContext)

        then:
        !finders.isEmpty()
    }

    void "registerEntity throws IllegalArgumentException for null arguments"() {
        given:
        def registry = GormRegistry.instance
        def entity = Stub(PersistentEntity)

        when:
        registry.registerEntity(null, null)

        then:
        thrown(IllegalArgumentException)

        when:
        registry.registerEntity(entity, null)

        then:
        thrown(IllegalArgumentException)
    }

    interface MixedDatastore extends MultiTenantCapableDatastore, MultipleConnectionSourceCapableDatastore, Datastore {}
    interface Datastore1 extends Datastore {}
    interface Datastore2 extends Datastore {}

    static class DummyStaticApiForTest extends GormStaticApi<TestEntity> {
        Map sharedState
        private final Datastore ds

        DummyStaticApiForTest(Class<TestEntity> persistentClass, Datastore datastore, Map sharedState, String qualifier = "default") {
            super(persistentClass, null, [], new DatastoreResolver() {
                @Override Datastore resolve() { return datastore }
            }, qualifier)
            this.ds = datastore
            this.sharedState = sharedState
        }

        @Override
        Datastore getDatastore() { ds }

        @Override
        GormStaticApi<TestEntity> forQualifier(String qualifier) {
            return new DummyStaticApiForTest(persistentClass, ds, sharedState, qualifier)
        }
    }

    static class TestEntity implements MultiTenant<TestEntity> {
        Long id
    }
}
