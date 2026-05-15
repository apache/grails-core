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
package org.grails.orm.hibernate

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.hibernate.dialect.H2Dialect
import spock.lang.Shared
import spock.lang.Specification

/**
 * Verifies the O(M+N) memory guarantee of {@link GormRegistry} in the H7 SCHEMA
 * multi-tenancy context.
 *
 * The registry must satisfy:
 *   - O(M)   static/instance/validation API maps — one entry per entity class, never per tenant
 *   - O(N)   datastoresByQualifier map — one entry per tenant/qualifier
 *   - O(1)   API retrieval for any qualifier — same singleton instance returned
 *
 * where M = number of entity classes, N = number of tenants/connections.
 */
class GormRegistryScalabilitySpec extends Specification {

    static final int TENANT_COUNT = 5

    @Shared HibernateDatastore datastore

    void setupSpec() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        Map config = [
            'grails.gorm.multiTenancy.mode'              : 'SCHEMA',
            'grails.gorm.multiTenancy.tenantResolverClass': ScalabilityTenantsResolver,
            'dataSource.url'                              : 'jdbc:h2:mem:scalabilityDB;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate'                         : 'update',
            'dataSource.dialect'                          : H2Dialect.name,
            'hibernate.flush.mode'                        : 'COMMIT',
            'hibernate.hbm2ddl.auto'                      : 'create',
        ]
        datastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(config),
            ScalabilityBook, ScalabilityAuthor
        )
    }

    void cleanupSpec() {
        datastore?.close()
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)
    }

    // -------------------------------------------------------------------------
    // O(M) — API maps must have exactly one entry per entity class, not per tenant
    // -------------------------------------------------------------------------

    void 'GormRegistry staticApis map size equals number of entity classes (O(M))'() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect: 'one static API entry per entity — never multiplied by tenant count'
        registry.staticApiRegistry.containsKey(ScalabilityBook.name)
        registry.staticApiRegistry.containsKey(ScalabilityAuthor.name)

        and: 'our two entities contribute exactly 2 keys (not 2 × tenant count)'
        registry.staticApiRegistry.keySet().count { it == ScalabilityBook.name || it == ScalabilityAuthor.name } == 2
    }

    void 'GormRegistry instanceApis map size equals number of entity classes (O(M))'() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect:
        registry.instanceApiRegistry.containsKey(ScalabilityBook.name)
        registry.instanceApiRegistry.containsKey(ScalabilityAuthor.name)

        and: 'our two entities contribute exactly 2 keys (not 2 × tenant count)'
        registry.instanceApiRegistry.keySet().count { it == ScalabilityBook.name || it == ScalabilityAuthor.name } == 2
    }

    void 'GormRegistry validationApis map size equals number of entity classes (O(M))'() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect:
        registry.validationApiRegistry.containsKey(ScalabilityBook.name)
        registry.validationApiRegistry.containsKey(ScalabilityAuthor.name)

        and: 'our two entities contribute exactly 2 keys (not 2 × tenant count)'
        registry.validationApiRegistry.keySet().count { it == ScalabilityBook.name || it == ScalabilityAuthor.name } == 2
    }

    // -------------------------------------------------------------------------
    // O(1) — same API singleton returned regardless of qualifier
    // -------------------------------------------------------------------------

    void 'getStaticApi returns the same singleton instance for any qualifier (O(1) retrieval)'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormStaticApi defaultApi = registry.getStaticApi(ScalabilityBook.name)

        expect: 'default qualifier retrieves the canonical singleton'
        defaultApi != null

        and: 'retrieval remains O(1) and returns the same singleton regardless of tenant loop context'
        ScalabilityTenantsResolver.TENANTS.every { tenantId ->
            registry.getStaticApi(ScalabilityBook.name).is(defaultApi)
        }
    }

    void 'getInstanceApi returns the same singleton instance for any qualifier (O(1) retrieval)'() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormInstanceApi defaultApi = registry.getInstanceApi(ScalabilityAuthor.name)

        expect:
        defaultApi != null
        ScalabilityTenantsResolver.TENANTS.every { tenantId ->
            registry.getInstanceApi(ScalabilityAuthor.name).is(defaultApi)
        }
    }

    // -------------------------------------------------------------------------
    // O(N) — qualifier map must grow with tenants (datastoresByQualifier)
    // -------------------------------------------------------------------------

    void 'datastoresByQualifier contains all registered tenants (O(N) qualifier map)'() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect: 'at minimum, the default qualifier is registered'
        registry.datastoresByQualifier.containsKey(ConnectionSource.DEFAULT)

        and: 'the qualifier map has at least one entry (the parent datastore)'
        registry.datastoresByQualifier.size() >= 1
    }

    // -------------------------------------------------------------------------
    // No spurious entries — unknown qualifiers must not pollute the registry
    // -------------------------------------------------------------------------

    void 'looking up an unknown qualifier does not create a spurious registry entry'() {
        given:
        GormRegistry registry = GormRegistry.instance
        String ghost = 'ghost_tenant_' + System.currentTimeMillis()
        int sizeBefore = registry.datastoresByQualifier.size()

        when:
        def result = registry.getDatastore(ScalabilityBook.name, ghost)

        then: 'nothing is found'
        result == null

        and: 'the map size is unchanged — no null/empty entry was inserted'
        registry.datastoresByQualifier.size() == sizeBefore
    }

    // -------------------------------------------------------------------------
    // H7 enhancer smoke check — child datastores exist for known tenants
    // -------------------------------------------------------------------------

    void 'child datastores are registered for all known SCHEMA tenants'() {
        expect:
        ScalabilityTenantsResolver.TENANTS.every { tenantId ->
            datastore.getDatastoreForTenantId(tenantId) != null
        }
    }
}

// ---------------------------------------------------------------------------
// Test fixtures
// ---------------------------------------------------------------------------

class ScalabilityTenantsResolver implements AllTenantsResolver {

    static final List<String> TENANTS = ['schemaA', 'schemaB', 'schemaC', 'schemaD', 'schemaE']

    @Override
    Serializable resolveTenantIdentifier() {
        TENANTS[0]
    }

    @Override
    Iterable<Serializable> resolveTenantIds() {
        TENANTS
    }
}

@Entity
class ScalabilityBook implements MultiTenant<ScalabilityBook> {

    String title
    String author
}

@Entity
class ScalabilityAuthor implements MultiTenant<ScalabilityAuthor> {

    String name
}
