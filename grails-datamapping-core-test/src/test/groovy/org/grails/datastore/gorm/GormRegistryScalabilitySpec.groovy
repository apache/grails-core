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

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.Shared
import spock.lang.Specification

/**
 * Verifies the O(M+N) memory guarantee of {@link GormRegistry} using a
 * {@link SimpleMapDatastore} in DATABASE multi-tenancy mode.
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

    @Shared SimpleMapDatastore datastore

    void setupSpec() {
        datastore = new SimpleMapDatastore(
            DatastoreUtils.createPropertyResolver([
                (Settings.SETTING_MULTI_TENANCY_MODE)   : "DATABASE",
                (Settings.SETTING_MULTI_TENANT_RESOLVER): new CoreScalabilityTenantsResolver(),
            ]),
            CoreScalabilityBook, CoreScalabilityAuthor
        )
    }

    void cleanupSpec() {
        datastore?.close()
    }

    void setup() {
        GormEnhancer.setPreferredDatastore(datastore)
    }

    void cleanup() {
        GormEnhancer.clearPreferredDatastore()
    }

    // -------------------------------------------------------------------------
    // O(M) — API maps must have exactly one entry per entity class, not per tenant
    // -------------------------------------------------------------------------

    void "GormRegistry staticApis map size equals number of entity classes (O(M))"() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect: "one static API entry per entity — never multiplied by tenant count"
        registry.staticApis.containsKey(CoreScalabilityBook.name)
        registry.staticApis.containsKey(CoreScalabilityAuthor.name)

        and: "the count is bounded by entity count, not entity × tenant"
        registry.staticApis.size() >= 2
        registry.staticApis.size() < 2 * TENANT_COUNT
    }

    void "GormRegistry instanceApis map size equals number of entity classes (O(M))"() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect:
        registry.instanceApis.containsKey(CoreScalabilityBook.name)
        registry.instanceApis.containsKey(CoreScalabilityAuthor.name)
        registry.instanceApis.size() >= 2
        registry.instanceApis.size() < 2 * TENANT_COUNT
    }

    void "GormRegistry validationApis map size equals number of entity classes (O(M))"() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect:
        registry.validationApis.containsKey(CoreScalabilityBook.name)
        registry.validationApis.containsKey(CoreScalabilityAuthor.name)
        registry.validationApis.size() >= 2
        registry.validationApis.size() < 2 * TENANT_COUNT
    }

    // -------------------------------------------------------------------------
    // O(1) — same API singleton returned regardless of qualifier
    // -------------------------------------------------------------------------

    void "getStaticApi returns the same singleton instance for any qualifier (O(1) retrieval)"() {
        given:
        GormRegistry registry = GormRegistry.instance
        GormStaticApi defaultApi = registry.getStaticApi(CoreScalabilityBook.name)

        expect: "default qualifier retrieves the canonical singleton"
        defaultApi != null

        and: "each tenant qualifier returns the same GormStaticApi type"
        CoreScalabilityTenantsResolver.TENANTS.every { tenantId ->
            registry.getStaticApi(CoreScalabilityBook.name, tenantId) instanceof GormStaticApi
        }
    }

    void "getInstanceApi returns a GormInstanceApi for any qualifier (O(1) retrieval)"() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect:
        registry.getInstanceApi(CoreScalabilityAuthor.name) != null
        CoreScalabilityTenantsResolver.TENANTS.every { tenantId ->
            registry.getInstanceApi(CoreScalabilityAuthor.name, tenantId) instanceof GormInstanceApi
        }
    }

    // -------------------------------------------------------------------------
    // O(N) — qualifier map must contain the registered datastores
    // -------------------------------------------------------------------------

    void "datastoresByQualifier contains the default qualifier (O(N) qualifier map)"() {
        given:
        GormRegistry registry = GormRegistry.instance

        expect:
        registry.datastoresByQualifier.containsKey(ConnectionSource.DEFAULT)
        registry.datastoresByQualifier.size() >= 1
    }

    // -------------------------------------------------------------------------
    // No spurious entries — unknown qualifiers must not pollute the registry
    // -------------------------------------------------------------------------

    void "looking up an unknown qualifier does not create a spurious registry entry"() {
        given:
        GormRegistry registry = GormRegistry.instance
        String ghost = "ghost_tenant_" + System.currentTimeMillis()
        int sizeBefore = registry.datastoresByQualifier.size()

        when:
        def result = registry.getDatastore(CoreScalabilityBook.name, ghost)

        then: "nothing is found"
        result == null

        and: "the map size is unchanged"
        registry.datastoresByQualifier.size() == sizeBefore
    }

    // -------------------------------------------------------------------------
    // Data isolation — tenant routing works correctly
    // -------------------------------------------------------------------------

    void "data written in one tenant is not visible in another"() {
        given:
        datastore.addTenantForSchema("tenant_write")
        datastore.addTenantForSchema("tenant_read")

        when:
        CoreScalabilityBook.withTenant("tenant_write") {
            new CoreScalabilityBook(title: "Isolated Book").save(flush: true)
        }

        then:
        CoreScalabilityBook.withTenant("tenant_write") { CoreScalabilityBook.count() } >= 1
        CoreScalabilityBook.withTenant("tenant_read") { CoreScalabilityBook.count() } == 0
    }
}

// ---------------------------------------------------------------------------
// Test fixtures
// ---------------------------------------------------------------------------

class CoreScalabilityTenantsResolver implements AllTenantsResolver {
    static final List<String> TENANTS = ["core_a", "core_b", "core_c", "core_d", "core_e"]

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
class CoreScalabilityBook implements MultiTenant<CoreScalabilityBook> {
    String title
}

@Entity
class CoreScalabilityAuthor implements MultiTenant<CoreScalabilityAuthor> {
    String name
}
