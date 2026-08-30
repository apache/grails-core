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

import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import spock.lang.Specification

/**
 * {@code TenantServiceSpec} in the separate grails-datamapping-core-test module already exercises
 * this class end-to-end through a real transformed service, but that module's test run doesn't
 * count toward grails-datamapping-core's own JaCoCo report (a different Gradle module/test task
 * entirely) - so from this module's own coverage perspective, this whole class had 0% coverage.
 * Every method here delegates to the extensively-tested {@code Tenants}/{@code CurrentTenantHolder}
 * static API (see {@code TenantsSpec}, item 8 of this plan), so these tests focus on
 * DefaultTenantService's own logic - the mode guard, the datastore-capability check, and (new in
 * this PR) the {@code RESOLVING} reentrancy guard - not on re-verifying Tenants' own behaviour.
 */
class DefaultTenantServiceSpec extends Specification {

    DefaultTenantService service = new DefaultTenantService()

    void "getDatastore/setDatastore round-trip"() {
        given:
        def ds = Mock(Datastore)

        when:
        service.setDatastore(ds)

        then:
        service.getDatastore().is(ds)
    }

    void "multiTenantDatastore throws DatastoreConfigurationException for a non-multi-tenant-capable datastore"() {
        given:
        service.setDatastore(Mock(Datastore))

        when:
        service.eachTenant { }

        then:
        def e = thrown(DatastoreConfigurationException)
        e.message.contains('is not Multi-Tenant capable')
    }

    private MultiTenantCapableDatastore discriminatorDatastore(List tenantIds = ['tenant1']) {
        Mock(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            getTenantResolver() >> Mock(AllTenantsResolver) {
                resolveTenantIds() >> tenantIds
            }
        }
    }

    void "eachTenant delegates to Tenants.eachTenant for each resolved tenant id"() {
        given:
        service.setDatastore(discriminatorDatastore(['tenant1', 'tenant2']))
        def seen = []

        when:
        service.eachTenant { seen << CurrentTenantHolder.get() }

        then:
        seen == ['tenant1', 'tenant2']
    }

    void "currentId throws DatastoreConfigurationException when multi tenancy mode is NONE"() {
        given:
        service.setDatastore(Mock(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.NONE
        })

        when:
        service.currentId()

        then:
        def e = thrown(DatastoreConfigurationException)
        e.message.contains('is not configured for Multi-Tenancy')
    }

    void "currentId delegates to Tenants.currentId when multi tenancy is configured"() {
        given:
        def ds = discriminatorDatastore()
        service.setDatastore(ds)

        expect:
        CurrentTenantHolder.withTenant(ds, 'tenant1') {
            service.currentId() == 'tenant1'
        }
    }

    void "withoutId throws DatastoreConfigurationException when multi tenancy mode is NONE"() {
        given:
        service.setDatastore(Mock(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.NONE
        })

        when:
        service.withoutId { -> 'result' }

        then:
        thrown(DatastoreConfigurationException)
    }

    void "withoutId delegates to Tenants.withoutId when multi tenancy is configured"() {
        given:
        service.setDatastore(discriminatorDatastore())

        expect:
        // an explicit zero-arg closure (`-> ...`) is required here: Tenants.withoutId's
        // shared-connection branch only calls a zero-parameter closure directly - any other
        // arity routes through multiTenantCapableDatastore.withSession(...), which a bare Mock
        // won't invoke without also stubbing withSession itself.
        service.withoutId { -> 'no tenant here' } == 'no tenant here'
    }

    void "withCurrent throws DatastoreConfigurationException when multi tenancy mode is NONE"() {
        given:
        service.setDatastore(Mock(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.NONE
        })

        when:
        service.withCurrent { 'result' }

        then:
        thrown(DatastoreConfigurationException)
    }

    void "withCurrent delegates to Tenants.withId using the resolved current tenant"() {
        given:
        def ds = discriminatorDatastore()
        service.setDatastore(ds)

        expect:
        CurrentTenantHolder.withTenant(ds, 'tenant1') {
            service.withCurrent { CurrentTenantHolder.get() } == 'tenant1'
        }
    }

    void "withCurrent's RESOLVING guard lets a nested call re-enter without recursing into Tenants"() {
        given: "a datastore that would throw if the mode guard were re-evaluated incorrectly on re-entry"
        def ds = discriminatorDatastore()
        service.setDatastore(ds)

        when:
        def result = CurrentTenantHolder.withTenant(ds, 'tenant1') {
            service.withCurrent {
                // Nested call while RESOLVING is already true - short-circuits straight
                // to the inner callable rather than re-invoking Tenants.withId.
                service.withCurrent { 'nested result' }
            }
        }

        then:
        result == 'nested result'

        and: "the RESOLVING flag is cleared afterwards so a later, independent call still works"
        CurrentTenantHolder.withTenant(ds, 'tenant2') {
            service.withCurrent { CurrentTenantHolder.get() }
        } == 'tenant2'
    }

    void "withId throws DatastoreConfigurationException when multi tenancy mode is NONE"() {
        given:
        service.setDatastore(Mock(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.NONE
        })

        when:
        service.withId('tenant1') { 'result' }

        then:
        thrown(DatastoreConfigurationException)
    }

    void "withId delegates to Tenants.withId with the given tenant id"() {
        given:
        service.setDatastore(discriminatorDatastore())

        expect:
        service.withId('explicit_tenant') { CurrentTenantHolder.get() } == 'explicit_tenant'
    }

    void "withId's RESOLVING guard lets a nested call re-enter without recursing into Tenants"() {
        given:
        service.setDatastore(discriminatorDatastore())

        expect:
        service.withId('outer_tenant') {
            service.withId('inner_tenant') { 'nested result' }
        } == 'nested result'
    }
}
