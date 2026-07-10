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
