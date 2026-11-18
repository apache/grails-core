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
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
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

    void 'findCurrentTenantTransactionManager returns the transaction manager of the connection of the tenant bound for the default datastore'() {
        given:
        final registry = GormRegistry.instance
        final defaultDatastore = Stub(Datastore)
        final tenantTransactionManager = Stub(PlatformTransactionManager)
        registry.initializeDatastore(defaultDatastore)
        registry.registerDatastore('ds1', Stub(TransactionCapableDatastore) {
            getTransactionManager() >> tenantTransactionManager
        })

        expect:
        CurrentTenantHolder.withTenant(defaultDatastore, 'ds1') {
            registry.findCurrentTenantTransactionManager()
        } == tenantTransactionManager

        and: 'there is none without a tenant, for the tenant default, or for a tenant without a connection'
        registry.findCurrentTenantTransactionManager() == null
        CurrentTenantHolder.withTenant(defaultDatastore, ConnectionSource.DEFAULT) {
            registry.findCurrentTenantTransactionManager()
        } == null
        CurrentTenantHolder.withTenant(defaultDatastore, 'unknown') {
            registry.findCurrentTenantTransactionManager()
        } == null
    }

    void 'findCurrentTenantTransactionManager considers only the tenant bound for the default datastore while another datastore has a different one'() {
        given:
        final registry = GormRegistry.instance
        final defaultDatastore = Stub(Datastore)
        final otherDatastore = Stub(Datastore)
        final tenantTransactionManager = Stub(PlatformTransactionManager)
        registry.initializeDatastore(defaultDatastore)
        registry.registerDatastore('ds1', Stub(TransactionCapableDatastore) {
            getTransactionManager() >> tenantTransactionManager
        })

        expect:
        CurrentTenantHolder.withTenant(defaultDatastore, 'ds1') {
            CurrentTenantHolder.withTenant(otherDatastore, 'ds2') {
                registry.findCurrentTenantTransactionManager()
            }
        } == tenantTransactionManager

        and: 'a tenant bound for another datastore only does not count'
        CurrentTenantHolder.withTenant(otherDatastore, 'ds1') {
            registry.findCurrentTenantTransactionManager()
        } == null
    }

    void 'findCurrentTenantTransactionManager with a datastore considers the tenant bound for that datastore'() {
        given:
        final registry = GormRegistry.instance
        final defaultDatastore = Stub(Datastore)
        final serviceDatastore = Stub(Datastore)
        final tenantTransactionManager = Stub(PlatformTransactionManager)
        registry.initializeDatastore(defaultDatastore)
        registry.registerDatastore('ds1', Stub(TransactionCapableDatastore) {
            getTransactionManager() >> tenantTransactionManager
        })

        expect:
        CurrentTenantHolder.withTenant(defaultDatastore, 'ds2') {
            CurrentTenantHolder.withTenant(serviceDatastore, 'ds1') {
                registry.findCurrentTenantTransactionManager(serviceDatastore)
            }
        } == tenantTransactionManager

        and: 'without a datastore, the one of the default datastore'
        CurrentTenantHolder.withTenant(defaultDatastore, 'ds1') {
            registry.findCurrentTenantTransactionManager(null)
        } == tenantTransactionManager
    }

    void 'findCurrentTenantTransactionManager takes the connection of the tenant from the datastore it is bound for when that datastore has several'() {
        given:
        final registry = GormRegistry.instance
        final tenantTransactionManager = Stub(PlatformTransactionManager)
        final connectionDatastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> tenantTransactionManager
        }
        final datastore = Stub(MultipleConnectionSourceCapableDatastore) {
            getDatastoreForConnection('ds1') >> connectionDatastore
            getDatastoreForConnection('ds2') >> { throw new ConfigurationException('No datastore found for connection named [ds2]') }
        }
        final otherConnectionDatastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> Stub(PlatformTransactionManager)
        }
        registry.initializeDatastore(Stub(Datastore))
        registry.registerDatastore('ds1', otherConnectionDatastore)
        registry.registerDatastore('ds2', otherConnectionDatastore)

        expect: 'not the connection of that name of another datastore'
        CurrentTenantHolder.withTenant(datastore, 'ds1') {
            registry.findCurrentTenantTransactionManager(datastore)
        } == tenantTransactionManager

        and: 'none when the datastore has no connection of that name, although another datastore has'
        CurrentTenantHolder.withTenant(datastore, 'ds2') {
            registry.findCurrentTenantTransactionManager(datastore)
        } == null
    }

    void 'findCurrentTenantTransactionManagerByType considers the tenant bound for the datastore of the type'() {
        given:
        final registry = GormRegistry.instance
        final defaultDatastore = Stub(Datastore)
        final tenantTransactionManager = Stub(PlatformTransactionManager)
        final connectionDatastore = Stub(TransactionCapableDatastore) {
            getTransactionManager() >> tenantTransactionManager
        }
        final typedDatastore = Stub(MultipleConnectionSourceCapableDatastore) {
            getDatastoreForConnection('ds1') >> connectionDatastore
        }
        registry.initializeDatastore(defaultDatastore)
        registry.registerDatastoreByType(typedDatastore)

        expect:
        CurrentTenantHolder.withTenant(defaultDatastore, 'ds2') {
            CurrentTenantHolder.withTenant(typedDatastore, 'ds1') {
                registry.findCurrentTenantTransactionManagerByType(MultipleConnectionSourceCapableDatastore)
            }
        } == tenantTransactionManager

        and: 'a tenant bound for the default datastore only does not count'
        CurrentTenantHolder.withTenant(defaultDatastore, 'ds1') {
            registry.findCurrentTenantTransactionManagerByType(MultipleConnectionSourceCapableDatastore)
        } == null

        and: 'there is none without a datastore of the type'
        CurrentTenantHolder.withTenant(typedDatastore, 'ds1') {
            registry.findCurrentTenantTransactionManagerByType(TransactionCapableDatastore)
        } == null
    }

    void 'findCurrentTenantTransactionManager returns null when no datastore is registered'() {
        expect:
        CurrentTenantHolder.withTenant(Stub(Datastore), 'ds1') {
            GormRegistry.instance.findCurrentTenantTransactionManager()
        } == null
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
            // Spock stubs return themselves for covariant interface methods; explicit null here mirrors
            // the real HibernateDatastore behavior where unknown connection names throw.
            getDatastoreForConnection(_) >> null
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

    void "execute with connection-name qualifier on DISCRIMINATOR multi-tenant entity does not override tenant context"() {
        given: "a DISCRIMINATOR-mode child datastore that resolves its own connection qualifier"
        def session = Stub(Session)
        // childDatastore simulates a ChildHibernateDatastore: getDatastoreForConnection('secondary') returns itself
        def childDatastore = Stub(MixedDatastore)
        childDatastore.getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        childDatastore.hasCurrentSession() >> false
        childDatastore.connect() >> session
        childDatastore.getDatastoreForConnection("secondary") >> childDatastore
        childDatastore.getConnectionSources() >> Stub(ConnectionSources) {
            getDefaultConnectionSource() >> Stub(ConnectionSource) {
                getName() >> "secondary"
            }
        }
        session.getDatastore() >> childDatastore

        def registry = GormRegistry.instance
        registry.registerDatastore("secondary", childDatastore)

        // The secondary static API has qualifier="secondary" and datastore=childDatastore,
        // matching what GormRegistry.findStaticApi(entity, "secondary") returns in practice.
        def secondaryApi = new DummyStaticApiForTest(TestEntity, childDatastore, [:], "secondary")
        registry.registerEntityApis(TestEntity, secondaryApi, null, null)

        when: "the secondary API executes inside an outer tenant context ('tenant1')"
        def capturedTenantId = null
        Tenants.withId(childDatastore, "tenant1") {
            secondaryApi.withDatastoreSession { Session sess ->
                capturedTenantId = CurrentTenantHolder.get(childDatastore)
            }
        }

        then: "the connection qualifier does NOT override the enclosing tenant context"
        capturedTenantId == "tenant1"

        cleanup:
        TestEntity.metaClass = null
    }

    void "execute with tenant-ID qualifier on DISCRIMINATOR multi-tenant entity binds that qualifier as tenant"() {
        given: "a DISCRIMINATOR-mode parent datastore where 'tenant1' is not a known connection name"
        def session = Stub(Session)
        def datastore = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            hasCurrentSession() >> false
            connect() >> session
            getConnectionSources() >> Stub(ConnectionSources) {
                getDefaultConnectionSource() >> Stub(ConnectionSource) {
                    getName() >> "default"
                }
            }
            // 'tenant1' is not a datasource connection — getDatastoreForConnection returns null
            getDatastoreForConnection("tenant1") >> null
        }
        session.getDatastore() >> datastore

        def registry = GormRegistry.instance
        registry.registerDatastore("default", datastore)

        // withTenant("tenant1") produces an API with qualifier="tenant1"
        def tenantApi = new DummyStaticApiForTest(TestEntity, datastore, [:], "tenant1")
        registry.registerEntityApis(TestEntity, tenantApi, null, null)

        when: "the tenant-qualified API executes a session callback"
        def capturedTenantId = null
        tenantApi.withDatastoreSession { Session sess ->
            capturedTenantId = CurrentTenantHolder.get(datastore)
        }

        then: "the tenant ID qualifier is correctly bound as the current tenant"
        capturedTenantId == "tenant1"

        cleanup:
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
