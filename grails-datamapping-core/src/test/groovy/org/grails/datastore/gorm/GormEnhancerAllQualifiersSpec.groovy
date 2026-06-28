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

import spock.lang.Specification

import grails.gorm.MultiTenant
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver

/**
 * Tests for {@link GormEnhancer#allQualifiers(Datastore, PersistentEntity)} to verify
 * that explicit datasource declarations on MultiTenant entities are preserved, and that
 * {@link GormEnhancer#registerEntity(PersistentEntity)} wires API objects into the
 * {@link GormRegistry} that backs the O(M+N) scaling strategy.
 *
 * <p>Prior to the fix verified here, {@code allQualifiers()} would unconditionally expand
 * qualifiers to all connection sources for any {@link MultiTenant} entity, even when the
 * entity declared an explicit non-default datasource (e.g., {@code datasource 'secondary'}).
 * This caused silent data routing to the wrong database under DISCRIMINATOR multi-tenancy.</p>
 *
 * <p>State formerly held in static maps on {@code GormEnhancer} now lives in
 * {@link GormRegistry}; these tests assert behaviour through that public surface. Each test
 * uses a fresh {@code GormRegistry} so the global singleton is never mutated and the suite
 * stays safe under parallel execution.</p>
 */
class GormEnhancerAllQualifiersSpec extends Specification {

    private Map<String, PersistentEntity> mockedEntities = [:]

    void cleanup() {
        mockedEntities.clear()
    }

    /**
     * Create a GormEnhancer wired to a fresh, isolated registry for the given datastore.
     */
    private GormEnhancer createEnhancer(Datastore datastore, GormRegistry registry) {
        new GormEnhancer(datastore, null, new ConnectionSourceSettings(), registry)
    }

    /**
     * Create a mock PersistentEntity with the specified java class and datasource list.
     * Uses a real {@link Entity} instance (concrete Groovy class) and mocks for interfaces.
     */
    private PersistentEntity mockEntity(Class javaClass, List<String> datasources) {
        def mappedForm = new Entity()
        mappedForm.datasources = datasources
        def classMapping = Mock(ClassMapping) {
            getMappedForm() >> mappedForm
        }
        def persistentEntity = Mock(PersistentEntity) {
            getJavaClass() >> javaClass
            getMapping() >> classMapping
            getName() >> javaClass.name
            isMultiTenant() >> MultiTenant.isAssignableFrom(javaClass)
        }
        mockedEntities[javaClass.name] = persistentEntity
        persistentEntity
    }

    /**
     * Create a mock datastore that also implements ConnectionSourcesProvider,
     * returning the specified connection source names.
     */
    private Datastore mockMultiConnectionDatastore(List<String> connectionNames, MultiTenancySettings.MultiTenancyMode mode = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
        def connectionSourceSettings = new ConnectionSourceSettings()
        connectionSourceSettings.multiTenancy.mode = mode
        def connectionSourceMocks = connectionNames.collect { name ->
            Mock(ConnectionSource) {
                getName() >> name
                getSettings() >> connectionSourceSettings
            }
        }
        def defaultConnectionSource = connectionSourceMocks.find { it.name == ConnectionSource.DEFAULT } ?: connectionSourceMocks.first()
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> { mockedEntities.values() }
            getPersistentEntity(_) >> { String name -> mockedEntities[name] }
        }
        def allSources = Mock(ConnectionSources) {
            getAllConnectionSources() >> connectionSourceMocks
            getDefaultConnectionSource() >> defaultConnectionSource
        }
        Mock(TestMultiTenantConnectionSourcesProviderDatastore) {
            getConnectionSources() >> allSources
            getMappingContext() >> mappingContext
            getMultiTenancyMode() >> mode
            getTenantResolver() >> Mock(TenantResolver)
        }
    }

    void "MultiTenant entity with explicit non-default datasource preserves qualifier"() {
        given: "a MultiTenant entity with datasource 'secondary'"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def enhancer = createEnhancer(datastore, new GormRegistry())
        def entity = mockEntity(MultiTenantSecondaryEntity, ['secondary'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "the explicit 'secondary' qualifier is preserved, not replaced with DEFAULT + all"
        qualifiers == ['secondary']
    }

    void "MultiTenant entity with default datasource expands to all qualifiers"() {
        given: "a MultiTenant entity on the default datasource"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary', 'reporting'])
        def enhancer = createEnhancer(datastore, new GormRegistry())
        def entity = mockEntity(MultiTenantDefaultEntity, [ConnectionSource.DEFAULT])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "qualifiers expand to DEFAULT + all non-default connection sources"
        qualifiers.contains(ConnectionSource.DEFAULT)
        qualifiers.contains('secondary')
        qualifiers.contains('reporting')
        qualifiers.size() == 3
    }

    void "MultiTenant entity with ALL datasource expands to all qualifiers"() {
        given: "a MultiTenant entity declared with ConnectionSource.ALL"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def enhancer = createEnhancer(datastore, new GormRegistry())
        def entity = mockEntity(MultiTenantAllEntity, [ConnectionSource.ALL])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "qualifiers expand to DEFAULT + all non-default connection sources"
        qualifiers.contains(ConnectionSource.DEFAULT)
        qualifiers.contains('secondary')
        qualifiers.size() == 2
    }

    void "MultiTenant entity with multiple explicit datasources preserves all qualifiers"() {
        given: "a MultiTenant entity with multiple explicit datasources"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'analytics', 'reporting', 'other'])
        def enhancer = createEnhancer(datastore, new GormRegistry())
        def entity = mockEntity(MultiTenantMultiDsEntity, ['analytics', 'reporting'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "the explicit qualifiers are preserved, not expanded"
        qualifiers == ['analytics', 'reporting']
    }

    void "non-MultiTenant entity with explicit datasource preserves qualifier"() {
        given: "a non-MultiTenant entity with datasource 'secondary'"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def enhancer = createEnhancer(datastore, new GormRegistry())
        def entity = mockEntity(NonMultiTenantSecondaryEntity, ['secondary'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "the explicit qualifier is preserved"
        qualifiers == ['secondary']
    }

    void "non-MultiTenant entity with default datasource keeps default only"() {
        given: "a non-MultiTenant entity on the default datasource"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def enhancer = createEnhancer(datastore, new GormRegistry())
        def entity = mockEntity(NonMultiTenantDefaultEntity, [ConnectionSource.DEFAULT])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "only DEFAULT qualifier is returned"
        qualifiers == [ConnectionSource.DEFAULT]
    }

    void "non-MultiTenant entity with ALL datasource expands to all qualifiers"() {
        given: "a non-MultiTenant entity declared with ConnectionSource.ALL"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def enhancer = createEnhancer(datastore, new GormRegistry())
        def entity = mockEntity(NonMultiTenantAllEntity, [ConnectionSource.ALL])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "qualifiers expand to DEFAULT + all non-default connection sources"
        qualifiers.contains(ConnectionSource.DEFAULT)
        qualifiers.contains('secondary')
        qualifiers.size() == 2
    }

    void "registerEntity registers the default static, instance and validation APIs in the registry"() {
        given: "an enhancer backed by a fresh registry"
        def registry = new GormRegistry()
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def enhancer = createEnhancer(datastore, registry)
        def entity = mockEntity(NonMultiTenantSecondaryEntity, ['secondary'])

        when: "registering the entity"
        enhancer.registerEntity(entity)

        then: "the default API objects are resolvable from the registry"
        registry.getStaticApi(NonMultiTenantSecondaryEntity) != null
        registry.getInstanceApi(NonMultiTenantSecondaryEntity) != null
        registry.getValidationApi(NonMultiTenantSecondaryEntity) != null
    }

    void "registerEntity makes the explicit connection qualifier resolvable"() {
        given: "a non-MultiTenant entity declaring datasource 'secondary' (no separate child datastore)"
        def registry = new GormRegistry()
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def enhancer = createEnhancer(datastore, registry)
        def entity = mockEntity(NonMultiTenantSecondaryEntity, ['secondary'])

        when: "registering the entity"
        enhancer.registerEntity(entity)

        then: "the static API resolves for both DEFAULT and the explicit qualifier, sharing the same instance when the qualifier routes to the same datastore"
        def defaultApi = registry.getStaticApi(NonMultiTenantSecondaryEntity, ConnectionSource.DEFAULT)
        def secondaryApi = registry.getStaticApi(NonMultiTenantSecondaryEntity, 'secondary')
        defaultApi != null
        secondaryApi != null
        secondaryApi.is(defaultApi)
    }

    void "registerEntity eagerly allocates APIs for an explicitly-mapped non-default datasource (bounded M side)"() {
        given: "an entity explicitly mapped to 'secondary', which routes to a distinct child datastore"
        def registry = new GormRegistry()
        def childDatastore = Mock(Datastore) {
            getMappingContext() >> Mock(MappingContext) { getPersistentEntities() >> [] }
        }
        def datastore = mockConnectionRoutingDatastore([ConnectionSource.DEFAULT, 'secondary'], ['secondary': childDatastore])
        def enhancer = createEnhancer(datastore, registry)
        def entity = mockEntity(ConnectionRoutedEntity, ['secondary'])

        when: "registering the entity"
        enhancer.registerEntity(entity)

        then: "the mapped 'secondary' APIs are materialized eagerly, with no prior access"
        registry.staticApiRegistry.isAllocated(ConnectionRoutedEntity.name, 'secondary')
        registry.instanceApiRegistry.isAllocated(ConnectionRoutedEntity.name, 'secondary')
        registry.validationApiRegistry.isAllocated(ConnectionRoutedEntity.name, 'secondary')

        and: "the 'secondary' static API is a distinct, cached instance from the DEFAULT API"
        def defaultApi = registry.getStaticApi(ConnectionRoutedEntity, ConnectionSource.DEFAULT)
        def secondaryApi = registry.getStaticApi(ConnectionRoutedEntity, 'secondary')
        !secondaryApi.is(defaultApi)
        secondaryApi.is(registry.getStaticApi(ConnectionRoutedEntity, 'secondary'))
    }

    void "registerEntity allocates an unmapped connection's API lazily on first access (unbounded N side)"() {
        given: "an entity on DEFAULT, with 'secondary' configured but NOT mapped to the entity"
        def registry = new GormRegistry()
        def childDatastore = Mock(Datastore) {
            getMappingContext() >> Mock(MappingContext) { getPersistentEntities() >> [] }
        }
        def datastore = mockConnectionRoutingDatastore([ConnectionSource.DEFAULT, 'secondary'], ['secondary': childDatastore])
        def enhancer = createEnhancer(datastore, registry)
        def entity = mockEntity(LazyConnectionEntity, [ConnectionSource.DEFAULT])

        when: "registering the entity"
        enhancer.registerEntity(entity)

        then: "the unmapped 'secondary' API is NOT materialized yet"
        registry.staticApiRegistry.isAllocated(LazyConnectionEntity.name, ConnectionSource.DEFAULT)
        !registry.staticApiRegistry.isAllocated(LazyConnectionEntity.name, 'secondary')

        when: "the qualifier-specific API is requested"
        def secondaryApi = registry.getStaticApi(LazyConnectionEntity, 'secondary')

        then: "a distinct API is created lazily for the child datastore and cached on subsequent lookups"
        secondaryApi != null
        registry.staticApiRegistry.isAllocated(LazyConnectionEntity.name, 'secondary')
        !secondaryApi.is(registry.getStaticApi(LazyConnectionEntity, ConnectionSource.DEFAULT))
        secondaryApi.is(registry.getStaticApi(LazyConnectionEntity, 'secondary'))
    }

    void "close removes the datastore registration from the registry"() {
        given: "a registered entity on an isolated registry"
        def registry = new GormRegistry()
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT])
        def enhancer = createEnhancer(datastore, registry)
        def entity = mockEntity(ClosableEntity, [ConnectionSource.DEFAULT])
        enhancer.registerEntity(entity)

        expect: "the datastore is registered before close"
        registry.allDatastores.contains(datastore)

        when: "the enhancer is closed"
        enhancer.close()

        then: "the datastore is removed from the registry"
        !registry.allDatastores.contains(datastore)
    }

    /**
     * Create a mock datastore that routes specific connection names to distinct child datastores.
     */
    private Datastore mockConnectionRoutingDatastore(List<String> connectionNames, Map<String, Datastore> connectionRouting) {
        def connectionSourceSettings = new ConnectionSourceSettings()
        def connectionSourceMocks = connectionNames.collect { name ->
            Mock(ConnectionSource) {
                getName() >> name
                getSettings() >> connectionSourceSettings
            }
        }
        def defaultConnectionSource = connectionSourceMocks.find { it.name == ConnectionSource.DEFAULT } ?: connectionSourceMocks.first()
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> { mockedEntities.values() }
            getPersistentEntity(_) >> { String name -> mockedEntities[name] }
        }
        def allSources = Mock(ConnectionSources) {
            getAllConnectionSources() >> connectionSourceMocks
            getDefaultConnectionSource() >> defaultConnectionSource
        }
        Mock(TestConnectionRoutingDatastore) {
            getConnectionSources() >> allSources
            getMappingContext() >> mappingContext
            getDatastoreForConnection(_) >> { String name -> connectionRouting[name] }
        }
    }

    // Stub entity classes

    static class MultiTenantSecondaryEntity implements MultiTenant<MultiTenantSecondaryEntity> {}
    static class MultiTenantDefaultEntity implements MultiTenant<MultiTenantDefaultEntity> {}
    static class MultiTenantAllEntity implements MultiTenant<MultiTenantAllEntity> {}
    static class MultiTenantMultiDsEntity implements MultiTenant<MultiTenantMultiDsEntity> {}
    static class NonMultiTenantSecondaryEntity {}
    static class NonMultiTenantDefaultEntity {}
    static class NonMultiTenantAllEntity {}
    static class ConnectionRoutedEntity {}
    static class LazyConnectionEntity {}
    static class ClosableEntity {}

    /**
     * Combined interface so Spock can mock a Datastore that also provides ConnectionSources.
     */
    static interface TestMultiTenantConnectionSourcesProviderDatastore extends MultiTenantCapableDatastore<Object, ConnectionSourceSettings> {}

    /**
     * Combined interface so Spock can mock a Datastore that both provides ConnectionSources and
     * routes connection names to distinct child datastores.
     */
    static interface TestConnectionRoutingDatastore extends MultipleConnectionSourceCapableDatastore, ConnectionSourcesProvider {}
}
