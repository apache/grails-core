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
package org.grails.datastore.gorm

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import spock.lang.Specification

class DefaultDatastoreSelectorSpec extends Specification {

    private final GormEnhancerRegistry stateRegistry = GormEnhancerRegistry.instance

    void setup() {
        GormRegistry.reset()
        stateRegistry.clearPreferredDatastore()
        stateRegistry.clearResolvingDatastoreDepth()
    }

    void cleanup() {
        stateRegistry.clearPreferredDatastore()
        stateRegistry.clearResolvingDatastoreDepth()
        GormRegistry.reset()
    }

    void 'select returns default datastore when the current tenant is default'() {
        given:
        def selector = new DefaultDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        MultiTenantCapableDatastore defaultDatastore = Mock(MultiTenantCapableDatastore)
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)

        expect:
        CurrentTenantHolder.withTenant(defaultDatastore, ConnectionSource.DEFAULT) {
            selector.select(registry, stateRegistry, TenantEntity, TenantEntity.name, 0, new GormApiResolver(registry))
        }.is(defaultDatastore)
    }

    void 'select delegates to resolver for a non-default tenant'() {
        given:
        def selector = new DefaultDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        Datastore resolvedDatastore = Mock(Datastore)
        MultiTenantCapableDatastore defaultDatastore = Mock(MultiTenantCapableDatastore)
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerDatastore('tenant-1', resolvedDatastore)

        expect:
        CurrentTenantHolder.withTenant(defaultDatastore, 'tenant-1') {
            selector.select(registry, stateRegistry, TenantEntity, TenantEntity.name, 0, new GormApiResolver(registry))
        }.is(resolvedDatastore)
    }

    void 'select rethrows tenant not found in database mode'() {
        given:
        def selector = new DefaultDatastoreSelector()
        GormRegistry registry = GormRegistry.instance
        MultiTenantCapableDatastore defaultDatastore = Mock(MultiTenantCapableDatastore) {
            getMultiTenancyMode() >> MultiTenancySettings.MultiTenancyMode.DATABASE
            getTenantResolver() >> Stub(org.grails.datastore.mapping.multitenancy.TenantResolver) {
                resolveTenantIdentifier() >> { throw new TenantNotFoundException('missing') }
            }
        }
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)

        when:
        selector.select(registry, stateRegistry, TenantEntity, TenantEntity.name, 0, new GormApiResolver(registry))

        then:
        thrown(TenantNotFoundException)
    }

    private static class TenantEntity implements MultiTenant<TenantEntity> {

    }
}
