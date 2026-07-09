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
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver

/**
 * Tests for {@link GormEnhancer#allQualifiers(Datastore, PersistentEntity)} to verify
 * that explicit datasource declarations on MultiTenant entities are preserved.
 *
 * <p>Prior to the fix, {@code allQualifiers()} would unconditionally expand qualifiers
 * to all connection sources for any {@link MultiTenant} entity, even when the entity
 * declared an explicit non-default datasource (e.g., {@code datasource 'secondary'}).
 * This caused silent data routing to the wrong database under DISCRIMINATOR multi-tenancy.</p>
 */
class GormEnhancerAllQualifiersSpec extends Specification {

    private Map<String, PersistentEntity> mockedEntities = [:]

    void cleanup() {
        mockedEntities.clear()
    }

    /**
     * Create a GormEnhancer with a minimal mock datastore (no entities registered).
     */
    private GormEnhancer createEnhancer() {
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        def datastore = Mock(Datastore) {
            getMappingContext() >> mappingContext
        }
        new GormEnhancer(datastore)
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
        def enhancer = createEnhancer()
        def entity = mockEntity(MultiTenantSecondaryEntity, ['secondary'])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "the explicit 'secondary' qualifier is preserved, not replaced with DEFAULT + all"
        qualifiers == ['secondary']
    }

    void "registerEntity adds static api under default and secondary for non-default datasource"() {
        given: "a non-MultiTenant entity with datasource 'secondary'"
        def enhancer = createEnhancer()
        def entity = mockEntity(NonMultiTenantSecondaryEntity, ['secondary'])
        when: "registering the entity"
        enhancer.registerEntity(entity)
        then: "static api is available under DEFAULT and secondary qualifiers"
        GormEnhancer.@STATIC_APIS.get(ConnectionSource.DEFAULT).containsKey(entity.name)
        GormEnhancer.@STATIC_APIS.get('secondary').containsKey(entity.name)
    }

    void "registerEntity adds static api under default and secondary for MultiTenant entity"() {
        given: "a MultiTenant entity with datasource 'secondary'"
        def enhancer = createEnhancer()
        def entity = mockEntity(MultiTenantSecondaryEntity, ['secondary'])
        when: "registering the entity"
        enhancer.registerEntity(entity)
        then: "static api is available under DEFAULT and secondary qualifiers"
        GormEnhancer.@STATIC_APIS.get(ConnectionSource.DEFAULT).containsKey(entity.name)
        GormEnhancer.@STATIC_APIS.get('secondary').containsKey(entity.name)
    }

    void "MultiTenant entity with default datasource expands to all qualifiers"() {
        given: "a MultiTenant entity on the default datasource"
        def enhancer = createEnhancer()
        def entity = mockEntity(MultiTenantDefaultEntity, [ConnectionSource.DEFAULT])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary', 'reporting'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "qualifiers expand to DEFAULT + all non-default connection sources"
        qualifiers.contains(ConnectionSource.DEFAULT)
        qualifiers.contains('secondary')
        qualifiers.contains('reporting')
        qualifiers.size() == 3
    }

    void "registerEntity creates expanded MultiTenant qualifier APIs lazily"() {
        given: "a MultiTenant entity that expands across many qualifiers"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'tenantA', 'tenantB'])
        def enhancer = new CountingGormEnhancer(datastore)
        def entity = mockEntity(MultiTenantExpandedEntity, [ConnectionSource.DEFAULT])

        when: "registering the entity"
        enhancer.registerEntity(entity)

        then: "only the default APIs are created eagerly"
        enhancer.staticApiCount == 1
        enhancer.instanceApiCount == 1
        enhancer.validationApiCount == 1
        GormEnhancer.@DATASTORES.get('tenantA').containsKey(entity.name)
        !GormEnhancer.@STATIC_APIS.get('tenantA').containsKey(entity.name)
        !GormEnhancer.@INSTANCE_APIS.get('tenantA').containsKey(entity.name)
        !GormEnhancer.@VALIDATION_APIS.get('tenantA').containsKey(entity.name)

        when: "the qualifier-specific APIs are requested"
        def staticApi = GormEnhancer.findStaticApi(MultiTenantExpandedEntity, 'tenantA')
        def instanceApi = GormEnhancer.findInstanceApi(MultiTenantExpandedEntity, 'tenantA')
        def validationApi = GormEnhancer.findValidationApi(MultiTenantExpandedEntity, 'tenantA')

        then: "registered qualifiers create APIs lazily without collapsing to the default datastore"
        staticApi.is(GormEnhancer.findStaticApi(MultiTenantExpandedEntity, 'tenantA'))
        instanceApi.is(GormEnhancer.findInstanceApi(MultiTenantExpandedEntity, 'tenantA'))
        validationApi.is(GormEnhancer.findValidationApi(MultiTenantExpandedEntity, 'tenantA'))
        !staticApi.is(GormEnhancer.findStaticApi(MultiTenantExpandedEntity, ConnectionSource.DEFAULT))
        !instanceApi.is(GormEnhancer.findInstanceApi(MultiTenantExpandedEntity, ConnectionSource.DEFAULT))
        !validationApi.is(GormEnhancer.findValidationApi(MultiTenantExpandedEntity, ConnectionSource.DEFAULT))
        enhancer.staticApiCount == 2
        enhancer.instanceApiCount == 2
        enhancer.validationApiCount == 2
    }

    void "registerEntity creates tenant APIs lazily for database-per-tenant qualifiers"() {
        given: "a database-per-tenant entity that expands across tenant qualifiers"
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'tenantA', 'tenantB'], MultiTenancySettings.MultiTenancyMode.DATABASE)
        def enhancer = new CountingGormEnhancer(datastore)
        def entity = mockEntity(DatabaseMultiTenantExpandedEntity, [ConnectionSource.DEFAULT])

        when: "registering the entity"
        enhancer.registerEntity(entity)

        then: "only the default APIs are created eagerly"
        enhancer.staticApiCount == 1
        enhancer.instanceApiCount == 1
        enhancer.validationApiCount == 1
        GormEnhancer.@DATASTORES.get('tenantA').containsKey(entity.name)
        !GormEnhancer.@STATIC_APIS.get('tenantA').containsKey(entity.name)
        !GormEnhancer.@INSTANCE_APIS.get('tenantA').containsKey(entity.name)
        !GormEnhancer.@VALIDATION_APIS.get('tenantA').containsKey(entity.name)

        when: "the tenant-specific APIs are requested"
        def staticApi = GormEnhancer.findStaticApi(DatabaseMultiTenantExpandedEntity, 'tenantA')
        def instanceApi = GormEnhancer.findInstanceApi(DatabaseMultiTenantExpandedEntity, 'tenantA')
        def validationApi = GormEnhancer.findValidationApi(DatabaseMultiTenantExpandedEntity, 'tenantA')

        then: "database-per-tenant APIs are created lazily and cached per tenant datastore"
        staticApi.is(GormEnhancer.findStaticApi(DatabaseMultiTenantExpandedEntity, 'tenantA'))
        instanceApi.is(GormEnhancer.findInstanceApi(DatabaseMultiTenantExpandedEntity, 'tenantA'))
        validationApi.is(GormEnhancer.findValidationApi(DatabaseMultiTenantExpandedEntity, 'tenantA'))
        !staticApi.is(GormEnhancer.findStaticApi(DatabaseMultiTenantExpandedEntity, ConnectionSource.DEFAULT))
        enhancer.staticApiCount == 2
        enhancer.instanceApiCount == 2
        enhancer.validationApiCount == 2
    }

    void "MultiTenant entity with ALL datasource expands to all qualifiers"() {
        given: "a MultiTenant entity declared with ConnectionSource.ALL"
        def enhancer = createEnhancer()
        def entity = mockEntity(MultiTenantAllEntity, [ConnectionSource.ALL])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "qualifiers expand to DEFAULT + all non-default connection sources"
        qualifiers.contains(ConnectionSource.DEFAULT)
        qualifiers.contains('secondary')
        qualifiers.size() == 2
    }

    void "non-MultiTenant entity with explicit datasource preserves qualifier"() {
        given: "a non-MultiTenant entity with datasource 'secondary'"
        def enhancer = createEnhancer()
        def entity = mockEntity(NonMultiTenantSecondaryEntity, ['secondary'])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "the explicit qualifier is preserved"
        qualifiers == ['secondary']
    }

    void "non-MultiTenant entity with default datasource keeps default only"() {
        given: "a non-MultiTenant entity on the default datasource"
        def enhancer = createEnhancer()
        def entity = mockEntity(NonMultiTenantDefaultEntity, [ConnectionSource.DEFAULT])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "only DEFAULT qualifier is returned"
        qualifiers == [ConnectionSource.DEFAULT]
    }

    void "registerEntity adds static api under default for default datasource"() {
        given: "a non-MultiTenant entity on the default datasource"
        def enhancer = createEnhancer()
        def entity = mockEntity(NonMultiTenantDefaultEntity, [ConnectionSource.DEFAULT])
        when: "registering the entity"
        enhancer.registerEntity(entity)
        then: "static api is available under DEFAULT qualifier"
        GormEnhancer.@STATIC_APIS.get(ConnectionSource.DEFAULT).containsKey(entity.name)
    }

    void "non-MultiTenant entity with ALL datasource expands to all qualifiers"() {
        given: "a non-MultiTenant entity declared with ConnectionSource.ALL"
        def enhancer = createEnhancer()
        def entity = mockEntity(NonMultiTenantAllEntity, [ConnectionSource.ALL])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "qualifiers expand to DEFAULT + all non-default connection sources"
        qualifiers.contains(ConnectionSource.DEFAULT)
        qualifiers.contains('secondary')
        qualifiers.size() == 2
    }

    void "MultiTenant entity with multiple explicit datasources preserves all qualifiers"() {
        given: "a MultiTenant entity with multiple explicit datasources"
        def enhancer = createEnhancer()
        def entity = mockEntity(MultiTenantMultiDsEntity, ['analytics', 'reporting'])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'analytics', 'reporting', 'other'])

        when:
        def qualifiers = enhancer.allQualifiers(datastore, entity)

        then: "the explicit qualifiers are preserved, not expanded"
        qualifiers == ['analytics', 'reporting']
    }

    void "close keeps a newer enhancer registered for the same datastore"() {
        given: "two enhancers created for the same datastore instance"
        def entity = mockEntity(StaleCloseEntity, [ConnectionSource.DEFAULT])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT])
        def firstEnhancer = new CountingGormEnhancer(datastore)
        def secondEnhancer = new CountingGormEnhancer(datastore)

        expect: "the newest enhancer owns the datastore registration"
        GormEnhancer.@ENHANCERS.get(datastore).is(secondEnhancer)

        when: "the stale enhancer is closed"
        firstEnhancer.close()

        then: "the active enhancer is not removed"
        GormEnhancer.@ENHANCERS.get(datastore).is(secondEnhancer)
        GormEnhancer.findDatastore(StaleCloseEntity).is(datastore)
        GormEnhancer.findStaticApi(StaleCloseEntity).is(GormEnhancer.findStaticApi(StaleCloseEntity))
        GormEnhancer.findInstanceApi(StaleCloseEntity).is(GormEnhancer.findInstanceApi(StaleCloseEntity))
        GormEnhancer.findValidationApi(StaleCloseEntity).is(GormEnhancer.findValidationApi(StaleCloseEntity))
    }

    // --- Stub entity classes ---

    static class MultiTenantSecondaryEntity implements MultiTenant<MultiTenantSecondaryEntity> {}
    static class MultiTenantDefaultEntity implements MultiTenant<MultiTenantDefaultEntity> {}
    static class MultiTenantAllEntity implements MultiTenant<MultiTenantAllEntity> {}
    static class MultiTenantMultiDsEntity implements MultiTenant<MultiTenantMultiDsEntity> {}
    static class MultiTenantExpandedEntity implements MultiTenant<MultiTenantExpandedEntity> {}
    static class DatabaseMultiTenantExpandedEntity implements MultiTenant<DatabaseMultiTenantExpandedEntity> {}
    static class NonMultiTenantSecondaryEntity {}
    static class NonMultiTenantDefaultEntity {}
    static class NonMultiTenantAllEntity {}
    static class StaleCloseEntity {}

    static class CountingGormEnhancer extends GormEnhancer {

        int staticApiCount
        int instanceApiCount
        int validationApiCount

        CountingGormEnhancer(Datastore datastore) {
            super(datastore)
        }

        @Override
        protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, String qualifier) {
            staticApiCount++
            super.getStaticApi(cls, qualifier)
        }

        @Override
        protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier) {
            instanceApiCount++
            super.getInstanceApi(cls, qualifier)
        }

        @Override
        protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier) {
            validationApiCount++
            super.getValidationApi(cls, qualifier)
        }
    }

    /**
     * Combined interface so Spock can mock a Datastore that also provides ConnectionSources.
     */
    static interface TestMultiTenantConnectionSourcesProviderDatastore extends MultiTenantCapableDatastore<Object, ConnectionSourceSettings> {}
}
