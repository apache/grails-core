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
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import spock.lang.Specification

/**
 * Tests for the {@link Tenants} helper class.
 */
class TenantsSpec extends Specification {

    def "test withId sets and resets tenant for a specific datastore"() {
        given: "A mock datastore"
        Datastore datastore = Mock(MultiTenantCapableDatastore)
        ((MultiTenantCapableDatastore)datastore).getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
        ((MultiTenantCapableDatastore)datastore).withNewSession(_ as Serializable, _ as Closure) >> { Serializable tenantId, Closure callable ->
            callable.call(null)
        }

        when: "Executing withId"
        def result = Tenants.withId((MultiTenantCapableDatastore)datastore, "tenant1") {
            return Tenants.currentId((MultiTenantCapableDatastore)datastore)
        }

        then: "The tenant is correct inside the closure"
        result == "tenant1"

        and: "The tenant is cleared after execution"
        CurrentTenantHolder.get(datastore) == null
    }

    def "test nested withId for different datastores"() {
        given: "Two mock datastores"
        Datastore ds1 = Mock(MultiTenantCapableDatastore)
        Datastore ds2 = Mock(MultiTenantCapableDatastore)
        ((MultiTenantCapableDatastore)ds1).getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
        ((MultiTenantCapableDatastore)ds2).getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE

        ((MultiTenantCapableDatastore)ds1).withNewSession(_ as Serializable, _ as Closure) >> { Serializable tenantId, Closure callable ->
            callable.call(null)
        }
        ((MultiTenantCapableDatastore)ds2).withNewSession(_ as Serializable, _ as Closure) >> { Serializable tenantId, Closure callable ->
            callable.call(null)
        }

        when: "Executing nested withId calls"
        def results = [:]
        Tenants.withId((MultiTenantCapableDatastore)ds1, "t1") {
            results.ds1_inner1 = Tenants.currentId((MultiTenantCapableDatastore)ds1)
            Tenants.withId((MultiTenantCapableDatastore)ds2, "t2") {
                results.ds1_inner2 = Tenants.currentId((MultiTenantCapableDatastore)ds1)
                results.ds2_inner2 = Tenants.currentId((MultiTenantCapableDatastore)ds2)
            }
            results.ds1_inner3 = Tenants.currentId((MultiTenantCapableDatastore)ds1)
            results.ds2_inner3 = CurrentTenantHolder.get(ds2)
        }

        then: "Each datastore maintains its own tenant context"
        results.ds1_inner1 == "t1"
        results.ds1_inner2 == "t1"
        results.ds2_inner2 == "t2"
        results.ds1_inner3 == "t1"
        results.ds2_inner3 == null
    }

    def "test currentId fallbacks to TenantResolver if no ThreadLocal set"() {
        given: "A mock datastore with a resolver"
        Datastore datastore = Mock(MultiTenantCapableDatastore)
        TenantResolver resolver = Mock(TenantResolver)
        ((MultiTenantCapableDatastore)datastore).getTenantResolver() >> resolver

        when: "No tenant is set in ThreadLocal"
        def result = Tenants.currentId((MultiTenantCapableDatastore)datastore)

        then: "The resolver is called"
        1 * resolver.resolveTenantIdentifier() >> "resolvedTenant"
        result == "resolvedTenant"
    }

    def "test withoutId executes without tenant context"() {
        given: "A mock datastore"
        Datastore datastore = Mock(MultiTenantCapableDatastore)
        ((MultiTenantCapableDatastore)datastore).getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
        ((MultiTenantCapableDatastore)datastore).withNewSession(_ as Serializable, _ as Closure) >> { Serializable tenantId, Closure callable ->
            callable.call(null)
        }

        when: "Executing withoutId"
        def result = Tenants.withoutId((MultiTenantCapableDatastore)datastore) {
            return CurrentTenantHolder.get(datastore)
        }

        then: "The current tenant is the default one"
        result == "default"
    }

    
}
