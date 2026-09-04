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
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

class GormApiResolverSpec extends Specification {

    void setup() {
        GormEnhancerRegistry.instance.clearPreferredDatastore()
        GormEnhancerRegistry.instance.clearResolvingDatastoreDepth()
    }

    void cleanup() {
        GormEnhancerRegistry.instance.clearPreferredDatastore()
        GormEnhancerRegistry.instance.clearResolvingDatastoreDepth()
    }

    void "findDatastore returns the default-qualified datastore directly once the recursion guard trips"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def defaultDs = Stub(Datastore)
        registry.datastoresByQualifier.put(ConnectionSource.DEFAULT, defaultDs)
        GormEnhancerRegistry.instance.setResolvingDatastoreDepth(6)

        when:
        def result = resolver.findDatastore(PlainEntity, 'tenant1')

        then:
        result == defaultDs
    }

    void "findDatastore resolves the plain default datastore when nothing else applies"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def defaultDs = Stub(Datastore)
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        when:
        def result = resolver.findDatastore(PlainEntity, null)

        then:
        result == defaultDs
    }

    void "findDatastore returns null when the entity is null and no datastore is configured"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)

        expect:
        resolver.findDatastore(null, null) == null
    }

    void "findDatastore throws when an entity is given but no datastore is configured"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)

        when:
        resolver.findDatastore(PlainEntity, null)

        then:
        IllegalStateException ex = thrown()
        ex.message.contains(PlainEntity.name)
    }

    void "findDatastore prefers the thread's preferred datastore over the default"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def preferred = Stub(Datastore)
        GormEnhancerRegistry.instance.setPreferredDatastore(preferred)

        expect:
        resolver.findDatastore(null, null) == preferred
    }

    void "findDatastore delegates to the qualified selector for a non-default qualifier"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def qualifiedDs = Stub(Datastore)
        registry.datastoresByQualifier.put('secondary', qualifiedDs)

        expect:
        resolver.findDatastore(null, 'secondary') == qualifiedDs
    }

    void "findDatastore prefers a datastore with an active session over the plain default"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def activeDs = Stub(Datastore) {
            hasCurrentSession() >> true
        }
        registry.allDatastores.add(activeDs)
        TransactionSynchronizationManager.bindResource(activeDs, new Object())

        expect:
        resolver.findDatastore(null, null) == activeDs

        cleanup:
        TransactionSynchronizationManager.unbindResource(activeDs)
    }

    void "findDatastoreByType returns the exact registered type"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def datastore = Stub(Datastore)
        registry.datastoresByType.put(Datastore, datastore)

        expect:
        resolver.findDatastoreByType(Datastore) == datastore
    }

    void "findDatastoreByType falls back to an assignable supertype"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def datastore = Stub(SubDatastore)
        registry.datastoresByType.put(SubDatastore, datastore)

        expect:
        resolver.findDatastoreByType(Datastore) == datastore
    }

    void "findDatastoreByType throws when nothing matches"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)

        when:
        resolver.findDatastoreByType(Datastore)

        then:
        IllegalStateException ex = thrown()
        ex.message.contains('Datastore')
    }

    void "findSingleDatastore delegates to findDatastore when more than one qualifier is registered"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def defaultDs = Stub(Datastore)
        def secondaryDs = Stub(Datastore)
        registry.datastoresByQualifier.put(ConnectionSource.DEFAULT, defaultDs)
        registry.datastoresByQualifier.put('secondary', secondaryDs)

        expect:
        resolver.findSingleDatastore() == defaultDs
    }

    void "findSingleDatastore returns the default-qualified datastore when exactly one is registered under DEFAULT"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def defaultDs = Stub(Datastore)
        registry.datastoresByQualifier.put(ConnectionSource.DEFAULT, defaultDs)

        expect:
        resolver.findSingleDatastore() == defaultDs
    }

    void "findSingleDatastore returns the sole non-default qualifier when only one is registered"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def onlyDs = Stub(Datastore)
        registry.datastoresByQualifier.put('secondary', onlyDs)

        expect:
        resolver.findSingleDatastore() == onlyDs
    }

    void "findSingleDatastore throws when no datastore is configured at all"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)

        when:
        resolver.findSingleDatastore()

        then:
        IllegalStateException ex = thrown()
        ex.message.contains('No GORM implementations configured')
    }

    void "findSingleDatastore throws when more than one implementation is configured by type only"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        registry.datastoresByType.put(Datastore, Stub(Datastore))
        registry.datastoresByType.put(SubDatastore, Stub(SubDatastore))

        when:
        resolver.findSingleDatastore()

        then:
        IllegalStateException ex = thrown()
        ex.message.contains('More than one GORM implementation is configured')
    }

    void "findSingleDatastore falls back to the sole type-registered datastore when no qualifiers exist"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def onlyDs = Stub(Datastore)
        registry.datastoresByType.put(Datastore, onlyDs)

        expect:
        resolver.findSingleDatastore() == onlyDs
    }

    void "findServiceDatastore returns the entity-specific default datastore when mapped"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def mappedDs = Stub(Datastore)
        def otherDs = Stub(Datastore)
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, mappedDs)
        registry.datastoresByQualifier.put(ConnectionSource.DEFAULT, otherDs)

        expect:
        resolver.findServiceDatastore(PlainEntity) == mappedDs
    }

    void "findServiceDatastore falls back to the single configured datastore when the entity is null"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def onlyDs = Stub(Datastore)
        registry.datastoresByQualifier.put(ConnectionSource.DEFAULT, onlyDs)

        expect:
        resolver.findServiceDatastore(null) == onlyDs
    }

    void "findServiceDatastore falls back to the single configured datastore when the entity is unmapped"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def onlyDs = Stub(Datastore)
        registry.datastoresByQualifier.put(ConnectionSource.DEFAULT, onlyDs)

        expect:
        resolver.findServiceDatastore(PlainEntity) == onlyDs
    }

    void "findEntity resolves the persistent entity from the default datastore for a non-multi-tenant class"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def persistentEntity = Stub(PersistentEntity)
        def mappingContext = Stub(MappingContext) {
            getPersistentEntity(PlainEntity.name) >> persistentEntity
        }
        def datastore = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, datastore)

        expect:
        resolver.findEntity(PlainEntity) == persistentEntity
    }

    void "findEntity resolves the tenant-qualified datastore for a multi-tenant class using the current tenant"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def tenantPersistentEntity = Stub(PersistentEntity)
        def tenantMappingContext = Stub(MappingContext) {
            getPersistentEntity(TenantEntity.name) >> tenantPersistentEntity
        }
        def tenantResolver = Stub(TenantResolver)
        def defaultDs = Stub(MixedDatastore) {
            getTenantResolver() >> tenantResolver
        }
        def tenantDs = Stub(Datastore) {
            getMappingContext() >> tenantMappingContext
        }
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, defaultDs)
        registry.datastoresByQualifier.put('tenant1', tenantDs)
        CurrentTenantHolder.set(defaultDs, 'tenant1')

        expect:
        resolver.findEntity(TenantEntity) == tenantPersistentEntity

        cleanup:
        CurrentTenantHolder.remove(defaultDs)
    }

    void "findEntity falls back to the DEFAULT qualifier when tenant resolution fails for a multi-tenant class"() {
        given:
        def registry = new GormRegistry()
        def resolver = new GormApiResolver(registry)
        def persistentEntity = Stub(PersistentEntity)
        def mappingContext = Stub(MappingContext) {
            getPersistentEntity(TenantEntity.name) >> persistentEntity
        }
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> { throw new TenantNotFoundException() }
        }
        def defaultDs = Stub(MixedDatastore) {
            getTenantResolver() >> tenantResolver
            getMappingContext() >> mappingContext
        }
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        resolver.findEntity(TenantEntity) == persistentEntity
    }

    void "PreferredDatastoreSelector returns null when there is no preferred datastore"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)

        expect:
        selector.select(registry, stateRegistry, PlainEntity, null, PlainEntity.name, 0, resolver) == null
    }

    void "PreferredDatastoreSelector returns null when the preferred datastore does not map the requested entity"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def mappingContext = Stub(MappingContext) {
            getPersistentEntity(PlainEntity.name) >> null
        }
        def preferred = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        stateRegistry.setPreferredDatastore(preferred)

        expect:
        selector.select(registry, stateRegistry, PlainEntity, null, PlainEntity.name, 0, resolver) == null
    }

    void "PreferredDatastoreSelector returns null when a different datastore owns the entity's default mapping"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def mappingContext = Stub(MappingContext) {
            getPersistentEntity(PlainEntity.name) >> Stub(PersistentEntity)
        }
        def preferred = Stub(Datastore) {
            getMappingContext() >> mappingContext
        }
        def owningDs = Stub(Datastore)
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, owningDs)
        stateRegistry.setPreferredDatastore(preferred)

        expect:
        selector.select(registry, stateRegistry, PlainEntity, null, PlainEntity.name, 0, resolver) == null
    }

    void "PreferredDatastoreSelector returns the preferred datastore directly for a non-multi-tenant entity"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def preferred = Stub(Datastore)
        stateRegistry.setPreferredDatastore(preferred)

        expect:
        selector.select(registry, stateRegistry, null, null, null, 0, resolver) == preferred
    }

    void "PreferredDatastoreSelector re-resolves via the tenant id when the preferred datastore is multi-tenant"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def preferred = Stub(MixedDatastore)
        def tenantDs = Stub(Datastore)
        registry.datastoresByQualifier.put('tenant1', tenantDs)
        stateRegistry.setPreferredDatastore(preferred)
        CurrentTenantHolder.set(preferred, 'tenant1')

        expect:
        selector.select(registry, stateRegistry, TenantEntity, null, null, 0, resolver) == tenantDs

        cleanup:
        CurrentTenantHolder.remove(preferred)
    }

    void "PreferredDatastoreSelector re-throws TenantNotFoundException for a multi-tenant entity"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> { throw new TenantNotFoundException() }
        }
        def preferred = Stub(MixedDatastore) {
            getTenantResolver() >> tenantResolver
        }
        stateRegistry.setPreferredDatastore(preferred)

        when:
        selector.select(registry, stateRegistry, TenantEntity, null, null, 0, resolver)

        then:
        thrown(TenantNotFoundException)
    }

    void "PreferredDatastoreSelector swallows tenant resolution failures for a non-multi-tenant entity and returns the preferred datastore"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> { throw new TenantNotFoundException() }
        }
        def preferred = Stub(MixedDatastore) {
            getTenantResolver() >> tenantResolver
        }
        stateRegistry.setPreferredDatastore(preferred)

        expect:
        selector.select(registry, stateRegistry, PlainEntity, null, null, 0, resolver) == preferred
    }

    void "PreferredDatastoreSelector resolves a qualified connection from a preferred multi-connection datastore"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def connectionDs = Stub(Datastore)
        def preferred = Stub(MultipleConnectionSourceDatastore) {
            getDatastoreForConnection('secondary') >> connectionDs
        }
        stateRegistry.setPreferredDatastore(preferred)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', null, 0, resolver) == connectionDs
    }

    void "PreferredDatastoreSelector returns null when the preferred datastore cannot resolve the connection qualifier"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def preferred = Stub(Datastore)
        stateRegistry.setPreferredDatastore(preferred)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', null, 0, resolver) == null
    }

    void "PreferredDatastoreSelector swallows connection resolution failures on a multi-connection preferred datastore"() {
        given:
        def selector = new PreferredDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def preferred = Stub(MultipleConnectionSourceDatastore) {
            getDatastoreForConnection('secondary') >> { throw new IllegalStateException('boom') }
        }
        stateRegistry.setPreferredDatastore(preferred)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', null, 0, resolver) == null
    }

    void "QualifiedDatastoreSelector returns a datastore directly bound to the qualifier in the transaction sync manager"() {
        given:
        def selector = new QualifiedDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def bound = Stub(Datastore)
        TransactionSynchronizationManager.bindResource('secondary', bound)

        expect:
        selector.select(registry, stateRegistry, null, 'secondary', 0) == bound

        cleanup:
        TransactionSynchronizationManager.unbindResource('secondary')
    }

    void "QualifiedDatastoreSelector returns the entity-specific mapped datastore for the qualifier"() {
        given:
        def selector = new QualifiedDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def mappedDs = Stub(Datastore)
        registry.registerEntityDatastore(PlainEntity.name, 'tenant1', mappedDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity.name, 'tenant1', 0) == mappedDs
    }

    void "QualifiedDatastoreSelector returns the DISCRIMINATOR-mode default datastore for a logical tenant qualifier"() {
        given:
        def selector = new QualifiedDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def defaultDs = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity.name, 'tenant1', 0) == defaultDs
    }

    void "QualifiedDatastoreSelector resolves a connection-source qualifier via a multi-connection default datastore"() {
        given:
        def selector = new QualifiedDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def connectionDs = Stub(Datastore)
        def defaultDs = Stub(MultipleConnectionSourceDatastore) {
            getDatastoreForConnection('secondary') >> connectionDs
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity.name, 'secondary', 0) == connectionDs
    }

    void "QualifiedDatastoreSelector swallows connection resolution failures and falls back to the default datastore"() {
        given:
        def selector = new QualifiedDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def defaultDs = Stub(MultipleConnectionSourceDatastore) {
            getDatastoreForConnection('secondary') >> { throw new IllegalStateException('boom') }
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity.name, 'secondary', 0) == defaultDs
    }

    void "QualifiedDatastoreSelector resolves a tenant id via a multi-tenant-capable default datastore"() {
        given:
        def selector = new QualifiedDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def tenantDs = Stub(Datastore)
        def defaultDs = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.SCHEMA
            getDatastoreForTenantId('tenant1') >> tenantDs
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity.name, 'tenant1', 0) == tenantDs
    }

    void "QualifiedDatastoreSelector swallows tenant id resolution failures and falls back to the default datastore"() {
        given:
        def selector = new QualifiedDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def defaultDs = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.SCHEMA
            getDatastoreForTenantId('tenant1') >> { throw new TenantNotFoundException() }
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity.name, 'tenant1', 0) == defaultDs
    }

    void "QualifiedDatastoreSelector falls back to the plain default datastore when nothing else resolves the qualifier"() {
        given:
        def selector = new QualifiedDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def defaultDs = Stub(Datastore)
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity.name, 'tenant1', 0) == defaultDs
    }

    void "ActiveSessionDatastoreSelector returns the entity-matching datastore with an active session"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def activeDs = Stub(Datastore) {
            hasCurrentSession() >> true
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, activeDs)
        TransactionSynchronizationManager.bindResource(activeDs, new Object())

        expect:
        selector.select(registry, PlainEntity.name) == activeDs

        cleanup:
        TransactionSynchronizationManager.unbindResource(activeDs)
    }

    void "ActiveSessionDatastoreSelector ignores sessions bound for unrelated datastores"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def unrelatedActiveDs = Stub(Datastore) {
            hasCurrentSession() >> true
        }
        def mappedDs = Stub(Datastore)
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, mappedDs)
        TransactionSynchronizationManager.bindResource(unrelatedActiveDs, new Object())

        expect:
        selector.select(registry, PlainEntity.name) == null

        cleanup:
        TransactionSynchronizationManager.unbindResource(unrelatedActiveDs)
    }

    void "ActiveSessionDatastoreSelector skips a DATABASE-mode datastore whose active connection does not match the resolved tenant"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def activeDs = Stub(MixedDatastore) {
            hasCurrentSession() >> true
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getConnectionSources() >> Stub(ConnectionSources) {
                getDefaultConnectionSource() >> Stub(ConnectionSource) {
                    getName() >> 'tenant2'
                }
            }
        }
        def persistentEntity = Stub(PersistentEntity) {
            isMultiTenant() >> true
        }
        def mappingContext = Stub(MappingContext) {
            getPersistentEntity(TenantEntity.name) >> persistentEntity
        }
        activeDs.getMappingContext() >> mappingContext
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, activeDs)
        CurrentTenantHolder.set(activeDs, 'tenant1')
        TransactionSynchronizationManager.bindResource(activeDs, new Object())

        expect:
        selector.select(registry, TenantEntity.name) == null

        cleanup:
        CurrentTenantHolder.remove(activeDs)
        TransactionSynchronizationManager.unbindResource(activeDs)
    }

    void "ActiveSessionDatastoreSelector returns a DATABASE-mode datastore whose active connection matches the resolved tenant"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def activeDs = Stub(MixedDatastore) {
            hasCurrentSession() >> true
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getConnectionSources() >> Stub(ConnectionSources) {
                getDefaultConnectionSource() >> Stub(ConnectionSource) {
                    getName() >> 'tenant1'
                }
            }
        }
        def persistentEntity = Stub(PersistentEntity) {
            isMultiTenant() >> true
        }
        def mappingContext = Stub(MappingContext) {
            getPersistentEntity(TenantEntity.name) >> persistentEntity
        }
        activeDs.getMappingContext() >> mappingContext
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, activeDs)
        CurrentTenantHolder.set(activeDs, 'tenant1')
        TransactionSynchronizationManager.bindResource(activeDs, new Object())

        expect:
        selector.select(registry, TenantEntity.name) == activeDs

        cleanup:
        CurrentTenantHolder.remove(activeDs)
        TransactionSynchronizationManager.unbindResource(activeDs)
    }

    void "ActiveSessionDatastoreSelector skips a DATABASE-mode datastore when the tenant cannot be resolved"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> { throw new TenantNotFoundException() }
        }
        def activeDs = Stub(MixedDatastore) {
            hasCurrentSession() >> true
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> tenantResolver
        }
        def persistentEntity = Stub(PersistentEntity) {
            isMultiTenant() >> true
        }
        def mappingContext = Stub(MappingContext) {
            getPersistentEntity(TenantEntity.name) >> persistentEntity
        }
        activeDs.getMappingContext() >> mappingContext
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, activeDs)
        TransactionSynchronizationManager.bindResource(activeDs, new Object())

        expect:
        selector.select(registry, TenantEntity.name) == null

        cleanup:
        TransactionSynchronizationManager.unbindResource(activeDs)
    }

    void "ActiveSessionDatastoreSelector skips a DATABASE-mode datastore whose active connection is still DEFAULT"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def activeDs = Stub(MixedDatastore) {
            hasCurrentSession() >> true
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getConnectionSources() >> Stub(ConnectionSources) {
                getDefaultConnectionSource() >> Stub(ConnectionSource) {
                    getName() >> ConnectionSource.DEFAULT
                }
            }
        }
        def persistentEntity = Stub(PersistentEntity) {
            isMultiTenant() >> true
        }
        def mappingContext = Stub(MappingContext) {
            getPersistentEntity(TenantEntity.name) >> persistentEntity
        }
        activeDs.getMappingContext() >> mappingContext
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, activeDs)
        CurrentTenantHolder.set(activeDs, 'tenant1')
        TransactionSynchronizationManager.bindResource(activeDs, new Object())

        expect:
        selector.select(registry, TenantEntity.name) == null

        cleanup:
        CurrentTenantHolder.remove(activeDs)
        TransactionSynchronizationManager.unbindResource(activeDs)
    }

    void "ActiveSessionDatastoreSelector skips a single-tenant-context DEFAULT-connection datastore when no className is given"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def activeDs = Stub(MixedDatastore) {
            hasCurrentSession() >> true
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getConnectionSources() >> Stub(ConnectionSources) {
                getDefaultConnectionSource() >> Stub(ConnectionSource) {
                    getName() >> ConnectionSource.DEFAULT
                }
            }
        }
        registry.allDatastores.add(activeDs)
        TransactionSynchronizationManager.bindResource(activeDs, new Object())

        expect:
        selector.select(registry, null) == null

        cleanup:
        TransactionSynchronizationManager.unbindResource(activeDs)
    }

    void "ActiveSessionDatastoreSelector falls back to iterating allDatastores when nothing is bound in the transaction sync manager"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def activeDs = Stub(Datastore) {
            hasCurrentSession() >> true
        }
        registry.allDatastores.add(activeDs)

        expect:
        selector.select(registry, null) == activeDs
    }

    void "ActiveSessionDatastoreSelector falls back to iterating allDatastores for a specific entity className"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        def activeDs = Stub(Datastore) {
            hasCurrentSession() >> true
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, activeDs)
        registry.allDatastores.add(activeDs)

        expect:
        selector.select(registry, PlainEntity.name) == activeDs
    }

    void "ActiveSessionDatastoreSelector skips the fallback iteration when more than ten datastores are registered"() {
        given:
        def selector = new ActiveSessionDatastoreSelector()
        def registry = new GormRegistry()
        (1..11).each { i ->
            registry.allDatastores.add(Stub(Datastore) {
                hasCurrentSession() >> true
            })
        }

        expect:
        selector.select(registry, null) == null
    }

    void "DefaultDatastoreSelector returns a non-multi-tenant default datastore directly"() {
        given:
        def selector = new DefaultDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def defaultDs = Stub(Datastore)
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity, PlainEntity.name, 0, resolver) == defaultDs
    }

    void "DefaultDatastoreSelector returns the default datastore when the resolved tenant id is DEFAULT"() {
        given:
        def selector = new DefaultDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def defaultDs = Stub(MixedDatastore)
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, defaultDs)
        CurrentTenantHolder.set(defaultDs, ConnectionSource.DEFAULT)

        expect:
        selector.select(registry, stateRegistry, TenantEntity, TenantEntity.name, 0, resolver) == defaultDs

        cleanup:
        CurrentTenantHolder.remove(defaultDs)
    }

    void "DefaultDatastoreSelector recursively resolves the tenant-qualified datastore when the current tenant is not DEFAULT"() {
        given:
        def selector = new DefaultDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def defaultDs = Stub(MixedDatastore)
        def tenantDs = Stub(Datastore)
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, defaultDs)
        registry.datastoresByQualifier.put('tenant1', tenantDs)
        CurrentTenantHolder.set(defaultDs, 'tenant1')

        expect:
        selector.select(registry, stateRegistry, TenantEntity, TenantEntity.name, 0, resolver) == tenantDs

        cleanup:
        CurrentTenantHolder.remove(defaultDs)
    }

    void "DefaultDatastoreSelector re-throws TenantNotFoundException for a DATABASE-mode multi-tenant entity"() {
        given:
        def selector = new DefaultDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> { throw new TenantNotFoundException() }
        }
        def defaultDs = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> tenantResolver
        }
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, defaultDs)

        when:
        selector.select(registry, stateRegistry, TenantEntity, TenantEntity.name, 0, resolver)

        then:
        thrown(TenantNotFoundException)
    }

    void "DefaultDatastoreSelector swallows TenantNotFoundException for a DISCRIMINATOR-mode multi-tenant entity"() {
        given:
        def selector = new DefaultDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> { throw new TenantNotFoundException() }
        }
        def defaultDs = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            getTenantResolver() >> tenantResolver
        }
        registry.registerEntityDatastore(TenantEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, TenantEntity, TenantEntity.name, 0, resolver) == defaultDs
    }

    void "DefaultDatastoreSelector swallows TenantNotFoundException for a non-multi-tenant entity class"() {
        given:
        def selector = new DefaultDatastoreSelector()
        def registry = new GormRegistry()
        def stateRegistry = new GormEnhancerRegistry()
        def resolver = new GormApiResolver(registry)
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> { throw new TenantNotFoundException() }
        }
        def defaultDs = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> tenantResolver
        }
        registry.registerEntityDatastore(PlainEntity.name, ConnectionSource.DEFAULT, defaultDs)

        expect:
        selector.select(registry, stateRegistry, PlainEntity, PlainEntity.name, 0, resolver) == defaultDs
    }

    interface SubDatastore extends Datastore {}
    interface MultipleConnectionSourceDatastore extends Datastore, MultipleConnectionSourceCapableDatastore {}
    interface MixedDatastore extends MultiTenantCapableDatastore, MultipleConnectionSourceCapableDatastore, Datastore {}

    static class PlainEntity {
        Long id
    }

    static class TenantEntity implements MultiTenant<TenantEntity> {
        Long id
    }
}
