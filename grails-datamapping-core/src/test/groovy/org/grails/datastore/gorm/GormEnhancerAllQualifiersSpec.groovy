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
import org.springframework.transaction.PlatformTransactionManager

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
        def transactionManager = Mock(PlatformTransactionManager)
        new GormEnhancer(datastore, transactionManager, new ConnectionSourceSettings())
    }

    private GormEnhancer createEnhancer(GormRegistry registry) {
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        def datastore = Mock(Datastore) {
            getMappingContext() >> mappingContext
        }
        def transactionManager = Mock(PlatformTransactionManager)
        new GormEnhancer(datastore, transactionManager, new ConnectionSourceSettings(), registry)
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
        Mock(PersistentEntity) {
            getJavaClass() >> javaClass
            getMapping() >> classMapping
            getName() >> javaClass.name
        }
    }

    /**
     * Create a mock datastore that also implements ConnectionSourcesProvider,
     * returning the specified connection source names.
     */
    private Datastore mockMultiConnectionDatastore(List<String> connectionNames) {
        def connectionSourceMocks = connectionNames.collect { name ->
            Mock(ConnectionSource) {
                getName() >> name
            }
        }
        def allSources = Mock(ConnectionSources) {
            getAllConnectionSources() >> connectionSourceMocks
        }
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        Mock(TestConnectionSourcesProviderDatastore) {
            getConnectionSources() >> allSources
            getMappingContext() >> mappingContext
        }
    }

    def cleanup() {
        GormRegistry.reset()
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
        GormRegistry.instance.getDatastore(entity.name, ConnectionSource.DEFAULT) != null
        GormRegistry.instance.getDatastore(entity.name, 'secondary') != null
    }

    void "registerEntity adds static api under default and secondary for MultiTenant entity"() {
        given: "a MultiTenant entity with datasource 'secondary'"
        def enhancer = createEnhancer()
        def entity = mockEntity(MultiTenantSecondaryEntity, ['secondary'])
        when: "registering the entity"
        enhancer.registerEntity(entity)
        then: "static api is available under DEFAULT and secondary qualifiers"
        GormRegistry.instance.getDatastore(entity.name, ConnectionSource.DEFAULT) != null
        GormRegistry.instance.getDatastore(entity.name, 'secondary') != null
    }

    void "MultiTenant entity with default datasource expands to all qualifiers"() {
        given: "a MultiTenant entity on the default datasource"
        def entity = mockEntity(MultiTenantDefaultEntity, [ConnectionSource.DEFAULT])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary', 'reporting'])
        def transactionManager = Mock(PlatformTransactionManager)
        def enhancer = new GormEnhancer(datastore, transactionManager, new ConnectionSourceSettings())

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
        def entity = mockEntity(MultiTenantAllEntity, [ConnectionSource.ALL])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def transactionManager = Mock(PlatformTransactionManager)
        def enhancer = new GormEnhancer(datastore, transactionManager, new ConnectionSourceSettings())

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
        GormRegistry.instance.getDatastore(entity.name, ConnectionSource.DEFAULT) != null
    }

    void "registerEntity can resolve through injected registry without touching global singleton"() {
        given:
        def injectedRegistry = new GormRegistry()
        def enhancer = createEnhancer(injectedRegistry)
        def entity = mockEntity(NonMultiTenantDefaultEntity, [ConnectionSource.DEFAULT])

        when:
        enhancer.registerEntity(entity)

        then:
        injectedRegistry.getDatastore(entity.name, ConnectionSource.DEFAULT) != null
        injectedRegistry.resolveStaticApi(NonMultiTenantDefaultEntity) != null
        GormRegistry.instance.getDatastore(entity.name, ConnectionSource.DEFAULT) == null
    }

    void "non-MultiTenant entity with ALL datasource expands to all qualifiers"() {
        given: "a non-MultiTenant entity declared with ConnectionSource.ALL"
        def entity = mockEntity(NonMultiTenantAllEntity, [ConnectionSource.ALL])
        def datastore = mockMultiConnectionDatastore([ConnectionSource.DEFAULT, 'secondary'])
        def transactionManager = Mock(PlatformTransactionManager)
        def enhancer = new GormEnhancer(datastore, transactionManager, new ConnectionSourceSettings())

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

    // --- Stub entity classes ---

    static class MultiTenantSecondaryEntity implements MultiTenant<MultiTenantSecondaryEntity> {}
    static class MultiTenantDefaultEntity implements MultiTenant<MultiTenantDefaultEntity> {}
    static class MultiTenantAllEntity implements MultiTenant<MultiTenantAllEntity> {}
    static class MultiTenantMultiDsEntity implements MultiTenant<MultiTenantMultiDsEntity> {}
    static class NonMultiTenantSecondaryEntity {}
    static class NonMultiTenantDefaultEntity {}
    static class NonMultiTenantAllEntity {}

    /**
     * Combined interface so Spock can mock a Datastore that also provides ConnectionSources.
     */
    static interface TestConnectionSourcesProviderDatastore extends Datastore, ConnectionSourcesProvider {}
}
