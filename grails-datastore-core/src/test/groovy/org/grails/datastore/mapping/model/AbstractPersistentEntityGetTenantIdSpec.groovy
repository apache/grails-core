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
package org.grails.datastore.mapping.model

import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import spock.lang.Specification

class AbstractPersistentEntityGetTenantIdSpec extends Specification {

    def "getTenantId resolves the TenantId property in DISCRIMINATOR mode"() {
        given:
        TestMappingContext context = new TestMappingContext()
        context.initialize(discriminatorSettings())
        def entity = context.addPersistentEntity(TenantScopedEntity)

        expect:
        entity.getTenantId() != null
        entity.getTenantId().name == 'tenantId'
    }

    def "getTenantId returns null when multi-tenancy mode is not DISCRIMINATOR"() {
        given:
        TestMappingContext context = new TestMappingContext()
        def entity = context.addPersistentEntity(TenantScopedEntity)

        expect:
        context.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.NONE
        entity.getTenantId() == null
    }

    def "getTenantId does not throw and returns null when entity initialization is deferred"() {
        given:
        TestMappingContext context = new TestMappingContext()
        context.initialize(discriminatorSettings())
        context.setCanInitializeEntities(false)
        def entity = context.addPersistentEntity(TenantScopedEntity)

        expect:
        entity.getTenantId() == null
    }

    def "getTenantId lazily resolves the TenantId property when the context switches to DISCRIMINATOR mode after the entity was already initialized"() {
        given: "an entity initialized while the context is still in NONE mode, so initialize()'s eager loop never assigns tenantId"
        TestMappingContext context = new TestMappingContext()
        def entity = context.addPersistentEntity(TenantScopedEntity)

        expect:
        context.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.NONE
        entity.getTenantId() == null

        when: "the context is reconfigured into DISCRIMINATOR mode afterwards"
        context.initialize(discriminatorSettings())

        then: "getTenantId() falls back to a lazy scan of the already-populated persistentProperties instead of staying stuck at null"
        entity.getTenantId() != null
        entity.getTenantId().name == 'tenantId'
    }

    def "getTenantId's lazy scan completes without a match and returns null for a multi-tenant entity with no tenantId property"() {
        given: "initialized in NONE mode - DISCRIMINATOR mode at initialize() time would reject this class for lacking a tenant identifier property"
        TestMappingContext context = new TestMappingContext()
        def entity = context.addPersistentEntity(TenantScopedEntityWithoutTenantIdProperty)

        when: "the context is reconfigured into DISCRIMINATOR mode afterwards, same as the successful-lookup case above"
        context.initialize(discriminatorSettings())

        then: "the lazy scan runs to completion without finding a TenantId property, rather than throwing or looping forever"
        entity.getTenantId() == null
    }

    def "getTenantId's lazy fallback short-circuits on isMultiTenant() for a non-multi-tenant entity, even in DISCRIMINATOR mode"() {
        given:
        TestMappingContext context = new TestMappingContext()
        context.initialize(discriminatorSettings())
        def entity = context.addPersistentEntity(NonTenantScopedEntity)

        expect:
        !entity.isMultiTenant()
        entity.getTenantId() == null
    }

    private static ConnectionSourceSettings discriminatorSettings() {
        ConnectionSourceSettings settings = new ConnectionSourceSettings()
        settings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        return settings
    }
}

interface MultiTenant {
}

class TenantScopedEntity implements MultiTenant {
    Long id
    String tenantId
}

class TenantScopedEntityWithoutTenantIdProperty implements MultiTenant {
    Long id
    String name
}

class NonTenantScopedEntity {
    Long id
    String name
}
