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
package grails.gorm.multitenancy

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException
import spock.lang.Specification

/**
 * A brand-new file in this PR (the GormRegistry rewrite's central "current tenant" ThreadLocal
 * holder). Already incidentally well-exercised by item 8's {@code TenantsSpec}, item 11's
 * {@code MultiTenantEventListenerSpec}, and item 14's {@code DefaultTenantServiceSpec} (via
 * {@code withTenant(datastore, id) { ... }}), but had no dedicated spec of its own - this adds one
 * covering the full public contract directly, plus the two remaining gaps: the "restore the
 * previous binding" branch of {@code withTenant(Class, ...)}/{@code withoutTenant} when called
 * nested inside an already-bound tenant (every prior incidental use only ever bound from a clean,
 * unbound state).
 */
class CurrentTenantHolderSpec extends Specification {

    void "get() returns null when no tenant is bound"() {
        expect:
        CurrentTenantHolder.get() == null
    }

    void "get() returns the current tenant once bound for any datastore"() {
        given:
        def datastore = Mock(Datastore)

        expect:
        CurrentTenantHolder.withTenant(datastore, 'tenant1') {
            CurrentTenantHolder.get() == 'tenant1'
        }
    }

    void "get() throws when different tenants are bound for different datastores"() {
        given:
        def datastoreA = Mock(Datastore)
        def datastoreB = Mock(Datastore)

        when:
        CurrentTenantHolder.withTenant(datastoreA, 'tenantA') {
            CurrentTenantHolder.withTenant(datastoreB, 'tenantB') {
                CurrentTenantHolder.get()
            }
        }

        then:
        thrown(TenantException)
    }

    void "get() does not throw when the same tenant id is bound for multiple datastores"() {
        given:
        def datastoreA = Mock(Datastore)
        def datastoreB = Mock(Datastore)

        expect:
        CurrentTenantHolder.withTenant(datastoreA, 'sameTenant') {
            CurrentTenantHolder.withTenant(datastoreB, 'sameTenant') {
                CurrentTenantHolder.get()
            }
        } == 'sameTenant'
    }

    void "get(Datastore) returns null when nothing is bound for that datastore"() {
        given:
        def datastore = Mock(Datastore)

        expect:
        CurrentTenantHolder.get(datastore) == null
    }

    void "set(Datastore, id)/remove(Datastore) round-trips the per-instance binding"() {
        given:
        def datastore = Mock(Datastore)

        when:
        CurrentTenantHolder.set(datastore, 'tenant1')

        then:
        CurrentTenantHolder.get(datastore) == 'tenant1'

        when:
        CurrentTenantHolder.remove(datastore)

        then:
        CurrentTenantHolder.get(datastore) == null
    }

    void "set(Class, id)/remove(Class) round-trips the per-class binding"() {
        given: "get(Datastore) only ever falls back via the instance's OWN runtime class, so the\n" +
                "same mock instance must be used for both the set(Class,...) key and the get(instance) read"
        def datastore = Mock(Datastore)

        when:
        CurrentTenantHolder.set(datastore.class, 'tenant1')

        then:
        // get(Datastore instance) falls back to the per-class binding when no per-instance one exists
        CurrentTenantHolder.get(datastore) == 'tenant1'

        cleanup:
        CurrentTenantHolder.remove(datastore.class)
    }

    void "get(Datastore) prefers the per-instance binding over the per-class fallback"() {
        given:
        def datastore = Mock(Datastore)
        CurrentTenantHolder.set(datastore.class, 'class_tenant')
        CurrentTenantHolder.set(datastore, 'instance_tenant')

        expect:
        CurrentTenantHolder.get(datastore) == 'instance_tenant'

        cleanup:
        CurrentTenantHolder.remove(datastore)
        CurrentTenantHolder.remove(datastore.class)
    }

    void "withTenant(Datastore) binds the tenant for the duration of the closure and removes it afterward"() {
        given:
        def datastore = Mock(Datastore)

        when:
        def result = CurrentTenantHolder.withTenant(datastore, 'tenant1') { boundId ->
            assert boundId == 'tenant1'
            CurrentTenantHolder.get(datastore)
        }

        then:
        result == 'tenant1'
        CurrentTenantHolder.get(datastore) == null
    }

    void "withTenant(Datastore) restores the previous binding on exit when nested"() {
        given:
        def datastore = Mock(Datastore)

        expect:
        CurrentTenantHolder.withTenant(datastore, 'outer') {
            CurrentTenantHolder.withTenant(datastore, 'inner') {
                CurrentTenantHolder.get(datastore)
            } == 'inner'
            CurrentTenantHolder.get(datastore)
        } == 'outer'
    }

    void "withTenant(Datastore) restores the previous binding even if the closure throws"() {
        given:
        def datastore = Mock(Datastore)

        when:
        CurrentTenantHolder.withTenant(datastore, 'outer') {
            CurrentTenantHolder.withTenant(datastore, 'inner') {
                throw new IllegalStateException('boom')
            }
        }

        then:
        thrown(IllegalStateException)
        CurrentTenantHolder.get(datastore) == null
    }

    void "withTenant(Class) binds the tenant for the duration of the closure and removes it afterward"() {
        given:
        def datastore = Mock(Datastore)

        when:
        def result = CurrentTenantHolder.withTenant(datastore.class, 'tenant1') { boundId ->
            assert boundId == 'tenant1'
            CurrentTenantHolder.get(datastore)
        }

        then:
        result == 'tenant1'
        CurrentTenantHolder.get(datastore) == null
    }

    void "withTenant(Class) restores the previous binding on exit when nested"() {
        given:
        def datastore = Mock(Datastore)

        expect:
        CurrentTenantHolder.withTenant(datastore.class, 'outer') {
            CurrentTenantHolder.withTenant(datastore.class, 'inner') {
                CurrentTenantHolder.get(datastore)
            } == 'inner'
            CurrentTenantHolder.get(datastore)
        } == 'outer'
    }

    void "withoutTenant binds the DEFAULT connection source and removes it afterward when nothing was previously bound"() {
        given:
        def datastore = Mock(Datastore)

        when:
        def result = CurrentTenantHolder.withoutTenant(datastore) {
            CurrentTenantHolder.get(datastore)
        }

        then:
        result == ConnectionSource.DEFAULT
        CurrentTenantHolder.get(datastore) == null
    }

    void "withoutTenant restores the previous binding on exit when nested inside an existing tenant"() {
        given:
        def datastore = Mock(Datastore)

        expect:
        CurrentTenantHolder.withTenant(datastore, 'outer') {
            CurrentTenantHolder.withoutTenant(datastore) {
                CurrentTenantHolder.get(datastore)
            } == ConnectionSource.DEFAULT
            CurrentTenantHolder.get(datastore)
        } == 'outer'
    }
}
