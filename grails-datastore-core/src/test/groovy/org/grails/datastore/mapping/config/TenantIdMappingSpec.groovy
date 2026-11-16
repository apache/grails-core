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
package org.grails.datastore.mapping.config

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode

/**
 * The tenant id of a multi-tenant entity is the property named in the mapping with {@code tenantId name:}, or else
 * the {@code tenantId} property, whether or not the mapping or the constraints configure that property.
 */
@Issue('https://github.com/apache/grails-core/issues/14365')
class TenantIdMappingSpec extends Specification {

    @Unroll
    void 'the tenantId property of #type.simpleName is the tenant id in #mode mode'() {
        when:
        PersistentEntity entity = mappingContext(mode).addPersistentEntity(type)

        then:
        entity.getPropertyByName('tenantId') instanceof TenantId

        and: 'discriminator mode, the only mode that assigns the tenant id, resolves it'
        entity.tenantId?.name == (mode == MultiTenancyMode.DISCRIMINATOR ? 'tenantId' : null)

        where:
        [type, mode] << [[PlainTenant, ConstrainedTenant, MappedTenant], MultiTenancyMode.values()].combinations()
    }

    @Unroll
    void 'the property named with tenantId name: is the tenant id of #type.simpleName in #mode mode'() {
        when:
        PersistentEntity entity = mappingContext(mode).addPersistentEntity(type)

        then:
        entity.getPropertyByName('firmId') instanceof TenantId
        !(entity.getPropertyByName('tenantId') instanceof TenantId)
        entity.tenantId?.name == (mode == MultiTenancyMode.DISCRIMINATOR ? 'firmId' : null)

        where:
        [type, mode] << [[NamedTenant, ConstrainedNamedTenant], MultiTenancyMode.values()].combinations()
    }

    private static KeyValueMappingContext mappingContext(MultiTenancyMode mode) {
        def settings = new ConnectionSourceSettings()
        settings.multiTenancy.mode = mode
        new KeyValueMappingContext('test', settings)
    }
}

// Stands for grails.gorm.MultiTenant, which this module does not depend on: GORM recognises a multi-tenant entity by
// the simple name of the interface it implements
interface MultiTenant {
}

class PlainTenant implements MultiTenant {
    Long id
    String name
    String tenantId
}

class ConstrainedTenant implements MultiTenant {
    Long id
    String name
    String tenantId

    static constraints = {
        tenantId nullable: false, maxSize: 5
    }
}

class MappedTenant implements MultiTenant {
    Long id
    String name
    String tenantId

    static mapping = {
        tenantId index: true
    }
}

class NamedTenant implements MultiTenant {
    Long id
    String name
    String firmId
    String tenantId

    static mapping = {
        tenantId name: 'firmId'
    }
}

class ConstrainedNamedTenant implements MultiTenant {
    Long id
    String name
    String firmId
    String tenantId

    static constraints = {
        firmId nullable: false, maxSize: 5
        tenantId nullable: true
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}
