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
package org.grails.datastore.gorm.multitenancy

import grails.gorm.api.GormAllOperations
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import spock.lang.Specification

/**
 * Verifies {@link TenantDelegatingGormOperations} delegates to the wrapped operations under the
 * bound tenant, rather than a different method with a compatible signature, and that the
 * {@code Map}-arg overloads of {@code deleteAll} forward the params map rather than dropping it.
 *
 * Swaps {@link Tenants#datastoreLocator} (the established, pluggable-for-testing static field - see
 * {@code TenantsSpec}/{@code DefaultTenantServiceSpec}) so {@code Tenants.withId(Class, ...)}
 * resolves to a controlled {@code Mock(MultiTenantCapableDatastore)} regardless of the (datastore,
 * not domain) class it's actually called with - sidestepping that unrelated, pre-existing semantic
 * question entirely and just verifying this class's own delegation behaviour.
 */
class TenantDelegatingGormOperationsSpec extends Specification {

    private final Tenants.DatastoreLocator originalLocator = Tenants.datastoreLocator

    void cleanup() {
        Tenants.datastoreLocator = originalLocator
    }

    private TenantDelegatingGormOperations<Object> buildOperations(GormAllOperations delegate) {
        def tenantDatastore = Mock(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        }
        Tenants.datastoreLocator = new Tenants.DatastoreLocator() {
            @Override
            Datastore getDatastoreForDomain(Class domainClass) {
                return tenantDatastore
            }
        }
        return new TenantDelegatingGormOperations<Object>(tenantDatastore, 'tenant1', delegate)
    }

    void "delete(instance, params) delegates to the wrapped delete, not save"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def instance = new Object()
        def params = [flush: true]

        when:
        ops.delete(instance, params)

        then:
        1 * delegate.delete(instance, params)
        0 * delegate.save(_, _)
    }

    void "deleteAll() delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)

        when:
        def result = ops.deleteAll()

        then:
        1 * delegate.deleteAll() >> 5
        result == 5
    }

    void "deleteAll(Map) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def params = [flush: true]

        when:
        def result = ops.deleteAll(params)

        then:
        1 * delegate.deleteAll(params) >> 3
        result == 3
    }

    void "deleteAll(Iterable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def toDelete = ['a', 'b']

        when:
        ops.deleteAll(toDelete)

        then:
        1 * delegate.deleteAll(toDelete)
    }

    void "deleteAll(Map, Object...) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def params = [flush: true]
        def toDelete = ['a', 'b'] as Object[]

        when:
        ops.deleteAll(params, toDelete)

        then:
        1 * delegate.deleteAll(params, toDelete)
    }

    void "deleteAll(Map, Iterable) delegates to the wrapped operations under the bound tenant"() {
        given:
        def delegate = Mock(GormAllOperations)
        def ops = buildOperations(delegate)
        def params = [flush: true]
        def toDelete = ['a', 'b']

        when:
        ops.deleteAll(params, toDelete)

        then:
        1 * delegate.deleteAll(params, toDelete)
    }
}
