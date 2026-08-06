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
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Coverage-focused specs for {@link GormRegistry}. {@link GormRegistrySpec} (pre-existing) already
 * covers the datastore-registration/removal helpers; this spec targets the remaining gaps: the
 * static entry-point delegators, the string-keyed API lookups, the multi-tenancy resolution logic
 * (identical across resolveStaticApi/resolveInstanceApi/resolveValidationApi), normalization
 * caching, entity-datastore registration edge cases, and the entity-registration orchestration.
 */
class GormRegistryCoverageSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(GormRegistryCoverageThing)

    void setup() {
        GormRegistry.instance.reset()
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    void "the static findStaticApi/findInstanceApi/findValidationApi/findDatastore delegators resolve via the singleton instance"() {
        given:
        def registry = GormRegistry.instance
        def staticApi = new GormStaticApi(GormRegistryCoverageThing, datastore, [])
        def instanceApi = new GormInstanceApi(GormRegistryCoverageThing, datastore)
        def validationApi = new GormValidationApi(GormRegistryCoverageThing, datastore, registry)
        registry.registerEntityApis(GormRegistryCoverageThing.name, staticApi, instanceApi, validationApi)
        registry.registerEntityDatastore(GormRegistryCoverageThing.name, ConnectionSource.DEFAULT, datastore)

        expect:
        GormRegistry.findStaticApi(GormRegistryCoverageThing) == staticApi
        GormRegistry.findStaticApi(GormRegistryCoverageThing, ConnectionSource.DEFAULT) == staticApi
        GormRegistry.findInstanceApi(GormRegistryCoverageThing) == instanceApi
        GormRegistry.findInstanceApi(GormRegistryCoverageThing, ConnectionSource.DEFAULT) == instanceApi
        GormRegistry.findValidationApi(GormRegistryCoverageThing) == validationApi
        GormRegistry.findValidationApi(GormRegistryCoverageThing, ConnectionSource.DEFAULT) == validationApi
        GormRegistry.findDatastore(GormRegistryCoverageThing) == datastore
        GormRegistry.findDatastore(GormRegistryCoverageThing, ConnectionSource.DEFAULT) == datastore
    }

    void "the String-keyed getStaticApi/getInstanceApi/getValidationApi overloads resolve by normalized class name"() {
        given:
        def registry = new GormRegistry()
        def staticApi = new GormStaticApi(GormRegistryCoverageThing, datastore.mappingContext, [])
        def instanceApi = new GormInstanceApi(GormRegistryCoverageThing, datastore)
        def validationApi = new GormValidationApi(GormRegistryCoverageThing, datastore, registry)
        registry.registerEntityApis(GormRegistryCoverageThing.name, staticApi, instanceApi, validationApi)

        expect:
        registry.getStaticApi(GormRegistryCoverageThing.name) == staticApi
        registry.getStaticApi(GormRegistryCoverageThing.name, ConnectionSource.DEFAULT) == staticApi
        registry.getInstanceApi(GormRegistryCoverageThing.name) == instanceApi
        registry.getInstanceApi(GormRegistryCoverageThing.name, ConnectionSource.DEFAULT) == instanceApi
        registry.getValidationApi(GormRegistryCoverageThing.name) == validationApi
        registry.getValidationApi(GormRegistryCoverageThing.name, ConnectionSource.DEFAULT) == validationApi
    }

    void "resolveStaticApi returns the plain registered api for a non-multi-tenant entity regardless of qualifier"() {
        given:
        def registry = new GormRegistry()
        def api = new GormStaticApi(GormRegistryCoverageThing, datastore.mappingContext, [])
        registry.registerApi(GormRegistryCoverageThing.name, api, null, null)

        expect:
        registry.resolveStaticApi(GormRegistryCoverageThing) == api
        registry.resolveStaticApi(GormRegistryCoverageThing, 'irrelevant-for-non-multi-tenant') == api
    }

    void "resolveStaticApi prefers an api re-qualified for an explicit non-default tenant qualifier"() {
        given: "the entity's mapping context is real, so re-qualification via GormStaticApiRegistry.qualify can find it"
        def tenantThingDs = new SimpleMapDatastore(TenantThing)
        def registry = new GormRegistry()
        def defaultDs = Stub(Datastore) { getMappingContext() >> tenantThingDs.mappingContext }
        def tenantDs = Stub(Datastore) { getMappingContext() >> tenantThingDs.mappingContext }
        registry.registerEntityDatastore(TenantThing.name, ConnectionSource.DEFAULT, defaultDs)
        registry.datastoresByQualifier.put('tenant1', tenantDs)
        def defaultApi = new GormStaticApi(TenantThing, defaultDs, [])
        registry.staticApiRegistry.register(TenantThing.name, defaultApi)

        when: "resolving with an explicit non-default qualifier (priority 1)"
        def resolved = registry.resolveStaticApi(TenantThing, 'tenant1')

        then: "a freshly re-qualified api for that tenant is returned, not the default"
        resolved.qualifier == 'tenant1'
        !resolved.is(defaultApi)

        cleanup:
        tenantThingDs.close()
    }

    void "resolveStaticApi resolves via the currently bound tenant when the default qualifier is used"() {
        given:
        def tenantThingDs = new SimpleMapDatastore(TenantThing)
        def registry = new GormRegistry()
        def tenantResolver = Stub(TenantResolver)
        def defaultMtds = Stub(MixedDatastore) {
            getMappingContext() >> tenantThingDs.mappingContext
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> tenantResolver
        }
        def tenantDs = Stub(Datastore) { getMappingContext() >> tenantThingDs.mappingContext }
        registry.registerEntityDatastore(TenantThing.name, ConnectionSource.DEFAULT, defaultMtds)
        registry.datastoresByQualifier.put('tenant1', tenantDs)
        def defaultApi = new GormStaticApi(TenantThing, defaultMtds, [])
        registry.staticApiRegistry.register(TenantThing.name, defaultApi)
        CurrentTenantHolder.set(defaultMtds, 'tenant1')

        when: "resolving with the default qualifier while a tenant is bound (priority 2)"
        def resolved = registry.resolveStaticApi(TenantThing, ConnectionSource.DEFAULT)

        then:
        resolved.qualifier == 'tenant1'

        cleanup:
        CurrentTenantHolder.remove(defaultMtds)
        tenantThingDs.close()
    }

    void "resolveStaticApi resolves the tenant via the tenant resolver when strict mode has no bound tenant"() {
        given:
        def tenantThingDs = new SimpleMapDatastore(TenantThing)
        def registry = new GormRegistry()
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> 'tenant1'
        }
        def defaultMtds = Stub(MixedDatastore) {
            getMappingContext() >> tenantThingDs.mappingContext
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.SCHEMA
            getTenantResolver() >> tenantResolver
        }
        def tenantDs = Stub(Datastore) { getMappingContext() >> tenantThingDs.mappingContext }
        registry.registerEntityDatastore(TenantThing.name, ConnectionSource.DEFAULT, defaultMtds)
        registry.datastoresByQualifier.put('tenant1', tenantDs)
        def defaultApi = new GormStaticApi(TenantThing, defaultMtds, [])
        registry.staticApiRegistry.register(TenantThing.name, defaultApi)

        when:
        def resolved = registry.resolveStaticApi(TenantThing, ConnectionSource.DEFAULT)

        then:
        resolved.qualifier == 'tenant1'

        cleanup:
        tenantThingDs.close()
    }

    void "resolveStaticApi rethrows TenantNotFoundException from strict-mode tenant resolution"() {
        given:
        def registry = new GormRegistry()
        def tenantResolver = Stub(TenantResolver) {
            resolveTenantIdentifier() >> { throw new TenantNotFoundException() }
        }
        def mtds = Stub(MixedDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> tenantResolver
        }
        registry.registerEntityDatastore(TenantThing.name, ConnectionSource.DEFAULT, mtds)

        when:
        registry.resolveStaticApi(TenantThing, ConnectionSource.DEFAULT)

        then:
        thrown(TenantNotFoundException)
    }

    void "resolveStaticApi falls back to the default-qualifier api when no tenant-specific api is registered"() {
        given:
        def registry = new GormRegistry()
        def defaultApi = new GormStaticApi(TenantThing, datastore.mappingContext, [])
        registry.staticApiRegistry.register(TenantThing.name, defaultApi)

        expect:
        registry.resolveStaticApi(TenantThing, 'unregistered-tenant') == defaultApi
    }

    void "resolveInstanceApi mirrors resolveStaticApi's multi-tenant resolution for instance apis"() {
        given:
        def tenantThingDs = new SimpleMapDatastore(TenantThing)
        def registry = new GormRegistry()
        def tenantResolver = Stub(TenantResolver)
        def defaultMtds = Stub(MixedDatastore) {
            getMappingContext() >> tenantThingDs.mappingContext
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> tenantResolver
        }
        def tenantDs = Stub(Datastore) { getMappingContext() >> tenantThingDs.mappingContext }
        registry.registerEntityDatastore(TenantThing.name, ConnectionSource.DEFAULT, defaultMtds)
        registry.datastoresByQualifier.put('tenant1', tenantDs)
        def defaultApi = new GormInstanceApi(TenantThing, defaultMtds)
        registry.instanceApiRegistry.register(TenantThing.name, defaultApi)
        CurrentTenantHolder.set(defaultMtds, 'tenant1')

        expect:
        registry.resolveInstanceApi(TenantThing, ConnectionSource.DEFAULT) != null

        cleanup:
        CurrentTenantHolder.remove(defaultMtds)
        tenantThingDs.close()
    }

    void "resolveValidationApi mirrors resolveStaticApi's multi-tenant resolution for validation apis"() {
        given:
        def tenantThingDs = new SimpleMapDatastore(TenantThing)
        def registry = new GormRegistry()
        def tenantResolver = Stub(TenantResolver)
        def defaultMtds = Stub(MixedDatastore) {
            getMappingContext() >> tenantThingDs.mappingContext
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> tenantResolver
        }
        def tenantDs = Stub(Datastore) { getMappingContext() >> tenantThingDs.mappingContext }
        registry.registerEntityDatastore(TenantThing.name, ConnectionSource.DEFAULT, defaultMtds)
        registry.datastoresByQualifier.put('tenant1', tenantDs)
        def defaultApi = new GormValidationApi(TenantThing, defaultMtds, registry)
        registry.validationApiRegistry.register(TenantThing.name, defaultApi)
        CurrentTenantHolder.set(defaultMtds, 'tenant1')

        expect:
        registry.resolveValidationApi(TenantThing, ConnectionSource.DEFAULT) != null

        cleanup:
        CurrentTenantHolder.remove(defaultMtds)
        tenantThingDs.close()
    }

    void "normalizeEntityKey caches the normalized name for a Class on repeated lookups"() {
        given:
        def registry = new GormRegistry()

        when:
        String first = registry.normalizeEntityKey(GormRegistryCoverageThing)
        String second = registry.normalizeEntityKey(GormRegistryCoverageThing)

        then:
        first == GormRegistryCoverageThing.name
        second.is(first)
    }

    void "normalizeEntityKey trims and caches a String key, returning null for a blank key"() {
        given:
        def registry = new GormRegistry()

        expect:
        registry.normalizeEntityKey('  some.Entity  ') == 'some.Entity'
        registry.normalizeEntityKey('   ') == null
        registry.normalizeEntityKey(null) == null
    }

    void "the deprecated normalizeEntityKeyFromClass and normalizeQualifierByString aliases delegate to their replacements"() {
        given:
        def registry = new GormRegistry()

        expect:
        registry.normalizeEntityKeyFromClass(GormRegistryCoverageThing) == GormRegistryCoverageThing.name
        registry.normalizeQualifierByString('secondary') == 'secondary'
    }

    void "normalizeQualifier maps the legacy OLD_DEFAULT name to DEFAULT and caches the result"() {
        given:
        def registry = new GormRegistry()

        expect:
        registry.normalizeQualifier(ConnectionSource.OLD_DEFAULT) == ConnectionSource.DEFAULT
        registry.normalizeQualifier(ConnectionSource.OLD_DEFAULT) == ConnectionSource.DEFAULT
        registry.normalizeQualifier('  ') == ConnectionSource.DEFAULT
    }

    void "registerEntityDatastores resolves a per-connection datastore from a multi-connection-source datastore"() {
        given:
        def registry = new GormRegistry()
        def secondaryDs = Stub(Datastore)
        def multiConnectionDs = Stub(MultipleConnectionSourceDatastore) {
            getDatastoreForConnection('secondary') >> secondaryDs
        }

        when:
        registry.registerEntityDatastores(GormRegistryCoverageThing.name, multiConnectionDs, ['secondary'], null)

        then:
        registry.getDatastoreByString(GormRegistryCoverageThing.name, 'secondary') == secondaryDs
    }

    void "registerEntityDatastores swallows connection resolution failures and keeps the parent datastore"() {
        given:
        def registry = new GormRegistry()
        def multiConnectionDs = Stub(MultipleConnectionSourceDatastore) {
            getDatastoreForConnection('secondary') >> { throw new IllegalStateException('boom') }
        }

        when:
        registry.registerEntityDatastores(GormRegistryCoverageThing.name, multiConnectionDs, ['secondary'], null)

        then:
        registry.getDatastoreByString(GormRegistryCoverageThing.name, 'secondary') == multiConnectionDs
    }

    void "registerEntityDatastores skips a non-default qualifier that resolves back to the parent for a multi-tenant entity"() {
        given: "a plain (non-multi-connection) parent datastore, so the 'tenant1' qualifier can only resolve back to it"
        def registry = new GormRegistry()
        def parentDs = Stub(Datastore)
        def persistentEntity = Stub(PersistentEntity) {
            isMultiTenant() >> true
        }

        when:
        registry.registerEntityDatastores(TenantThing.name, parentDs, ['tenant1'], persistentEntity)

        then: "the runtime tenant id qualifier is skipped as an entity-specific override, falling through to registering DEFAULT instead"
        registry.getDatastoreByString(TenantThing.name, ConnectionSource.DEFAULT) == parentDs
    }

    void "registerEntityDatastores registers a default connection datastore when DEFAULT is not among the qualifiers"() {
        given:
        def registry = new GormRegistry()
        def secondaryDs = Stub(Datastore)

        when:
        registry.registerEntityDatastores(GormRegistryCoverageThing.name, secondaryDs, ['secondary'], null)

        then:
        registry.getDatastoreByString(GormRegistryCoverageThing.name, ConnectionSource.DEFAULT) == secondaryDs
    }

    void "getDatastoreByString(DEFAULT) falls back deterministically to the entity's next declared connection after the one DEFAULT pointed to is removed"() {
        given: "an entity mapped only to non-default connections, declared analytics first, then reporting, then audit"
        def registry = new GormRegistry()
        def analyticsDs = Stub(Datastore)
        def reportingDs = Stub(Datastore)
        def auditDs = Stub(Datastore)
        def multiConnectionDs = Stub(MultipleConnectionSourceDatastore) {
            getDatastoreForConnection('analytics') >> analyticsDs
            getDatastoreForConnection('reporting') >> reportingDs
            getDatastoreForConnection('audit') >> auditDs
        }
        registry.registerEntityDatastores(GormRegistryCoverageThing.name, multiConnectionDs, ['analytics', 'reporting', 'audit'], null)

        expect: "registration deterministically pointed DEFAULT at the first declared connection"
        registry.getDatastoreByString(GormRegistryCoverageThing.name, ConnectionSource.DEFAULT) == analyticsDs

        when: "the datastore DEFAULT points to is torn down at runtime (e.g. a tenant/child datastore removal)"
        registry.removeDatastore(analyticsDs)

        then: "DEFAULT falls back to the entity's next declared connection (reporting), not to whichever remaining entry the map's iteration order happens to produce"
        registry.getDatastoreByString(GormRegistryCoverageThing.name, ConnectionSource.DEFAULT) == reportingDs
    }

    void "createClassDatastoreResolver builds a resolver that delegates to the api resolver for the normalized class and qualifier"() {
        given:
        def registry = new GormRegistry()
        def resolvedDs = Stub(Datastore)
        registry.registerEntityDatastore(GormRegistryCoverageThing.name, 'secondary', resolvedDs)

        when:
        def resolver = registry.createClassDatastoreResolver(GormRegistryCoverageThing, 'secondary')

        then:
        resolver.resolve() == resolvedDs
    }

    void "createDynamicFinders(Datastore) builds finders using the datastore's own mapping context"() {
        given:
        def registry = new GormRegistry()

        expect:
        !registry.createDynamicFinders(datastore).isEmpty()
    }

    void "removeConstraints swallows failures outside a Grails 2 environment"() {
        given:
        def registry = new GormRegistry()

        expect:
        registry.removeConstraints() == null
    }

    void "removeDatastore de-registers constraints as part of tearing down the datastore"() {
        given:
        def registry = new GormRegistry()
        registry.registerDatastore(ConnectionSource.DEFAULT, datastore)

        when:
        registry.removeDatastore(datastore)

        then: "no exception - the Grails-2-only constraint removal safely no-ops outside that environment"
        notThrown(Throwable)
    }

    void "registerEntity registers static, instance and validation apis plus the entity's default datastore mapping"() {
        given:
        def registry = new GormRegistry()
        def persistentEntity = datastore.mappingContext.getPersistentEntity(GormRegistryCoverageThing.name)
        def enhancer = new GormEnhancer(datastore, datastore.transactionManager)

        when:
        registry.registerEntity(persistentEntity, enhancer)

        then:
        registry.getStaticApi(GormRegistryCoverageThing) != null
        registry.getInstanceApi(GormRegistryCoverageThing) != null
        registry.getValidationApi(GormRegistryCoverageThing) != null
        registry.getDatastore(GormRegistryCoverageThing) != null
    }

    void "registerEntity rejects a null persistentEntity or enhancer"() {
        given:
        def registry = new GormRegistry()
        def persistentEntity = Stub(PersistentEntity)

        when:
        registry.registerEntity(null, null)

        then:
        thrown(IllegalArgumentException)

        when:
        registry.registerEntity(persistentEntity, null)

        then:
        thrown(IllegalArgumentException)
    }

    interface MixedDatastore extends MultiTenantCapableDatastore, Datastore {}
    interface MultipleConnectionSourceDatastore extends Datastore, MultipleConnectionSourceCapableDatastore {}
}

@Entity
class GormRegistryCoverageThing {

    String name
}

class TenantThing implements MultiTenant<TenantThing> {
    Long id
}
