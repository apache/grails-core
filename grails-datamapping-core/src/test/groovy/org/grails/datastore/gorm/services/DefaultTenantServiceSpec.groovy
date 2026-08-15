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
package org.grails.datastore.gorm.services

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import spock.lang.Specification

class DefaultTenantServiceSpec extends Specification {

    DefaultTenantService tenantService = new DefaultTenantService()

    void 'multiTenantDatastore throws when the datastore is not multi-tenant capable'() {
        given:
        tenantService.datastore = Mock(Datastore)

        when:
        tenantService.eachTenant {}

        then:
        DatastoreConfigurationException e = thrown(DatastoreConfigurationException)
        e.message.contains('not Multi-Tenant capable')
    }

    void 'currentId throws when multi tenancy mode is NONE'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.NONE
        tenantService.datastore = datastore

        when:
        tenantService.currentId()

        then:
        DatastoreConfigurationException e = thrown(DatastoreConfigurationException)
        e.message.contains('not configured for Multi-Tenancy')
    }

    void 'currentId resolves the tenant id from the tenant resolver'() {
        given:
        TenantResolver tenantResolver = Mock(TenantResolver) {
            resolveTenantIdentifier() >> 'tenant1'
        }
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        datastore.tenantResolver >> tenantResolver
        tenantService.datastore = datastore

        expect:
        tenantService.currentId() == 'tenant1'
    }

    void 'withoutId throws when multi tenancy mode is NONE'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.NONE
        tenantService.datastore = datastore

        when:
        tenantService.withoutId { 'result' }

        then:
        thrown(DatastoreConfigurationException)
    }

    void 'withoutId with a shared connection mode executes the callable without a session'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        tenantService.datastore = datastore

        when:
        String result = tenantService.withoutId { -> 'result' }

        then:
        result == 'result'
        0 * datastore.withSession(_)
        0 * datastore.withNewSession(_, _)
    }

    void 'withoutId with a non-shared connection mode executes within a new session'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.DATABASE
        datastore.withNewSession(_, _) >> { args -> args[1].call('session') }
        tenantService.datastore = datastore

        expect:
        tenantService.withoutId { 'result' } == 'result'
    }

    void 'withId throws when multi tenancy mode is NONE'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.NONE
        tenantService.datastore = datastore

        when:
        tenantService.withId('tenant1') { 'result' }

        then:
        thrown(DatastoreConfigurationException)
    }

    void 'withId with a shared connection mode executes the callable directly'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.SCHEMA
        tenantService.datastore = datastore

        expect:
        tenantService.withId('tenant1') { tenantId -> "result-$tenantId" } == 'result-tenant1'
    }

    void 'withId with a non-shared connection mode executes within a new session for the tenant'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.DATABASE
        datastore.withNewSession('tenant1', _) >> { args -> args[1].call('session') }
        tenantService.datastore = datastore

        expect:
        tenantService.withId('tenant1') { 'result' } == 'result'
    }

    void 'withCurrent throws when multi tenancy mode is NONE'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.NONE
        tenantService.datastore = datastore

        when:
        tenantService.withCurrent { 'result' }

        then:
        thrown(DatastoreConfigurationException)
    }

    void 'withCurrent executes the callable with the resolved current tenant id'() {
        given:
        TenantResolver tenantResolver = Mock(TenantResolver) {
            resolveTenantIdentifier() >> 'tenant1'
        }
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.SCHEMA
        datastore.tenantResolver >> tenantResolver
        tenantService.datastore = datastore

        expect:
        tenantService.withCurrent { tenantId -> "result-$tenantId" } == 'result-tenant1'
    }

    void 'eachTenant throws when multi tenancy mode is NONE'() {
        given:
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.NONE
        tenantService.datastore = datastore

        when:
        tenantService.eachTenant {}

        then:
        thrown(UnsupportedOperationException)
    }

    void 'eachTenant with a shared connection mode invokes the callable for every resolved tenant id'() {
        given:
        AllTenantsResolver tenantResolver = Mock(AllTenantsResolver) {
            resolveTenantIds() >> ['tenant1', 'tenant2']
        }
        MultiTenantCapableDatastore datastore = Mock(MultiTenantCapableDatastore)
        datastore.multiTenancyMode >> MultiTenancySettings.MultiTenancyMode.SCHEMA
        datastore.tenantResolver >> tenantResolver
        tenantService.datastore = datastore

        and:
        List<Serializable> seen = []

        when:
        tenantService.eachTenant { tenantId -> seen << tenantId }

        then:
        seen == ['tenant1', 'tenant2']
    }
}
