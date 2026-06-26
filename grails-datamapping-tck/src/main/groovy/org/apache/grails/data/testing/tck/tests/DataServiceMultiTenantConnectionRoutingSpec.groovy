/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * 'License'); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import spock.lang.Requires
import spock.util.environment.RestoreSystemProperties

import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.DataServiceRoutingMetric
import org.apache.grails.data.testing.tck.domains.DataServiceRoutingMetricService

/**
 * Verifies that {@code @Service}-based Data Service operations on a secondary datasource are
 * correctly scoped to the active tenant via the {@code GormRegistry} selector chain.
 *
 * <p>Replaces the service-layer portion of the removed {@code CrossLayerMultiTenantMultiDataSourceSpec}.
 * That spec obtained a tenant-scoped service via {@code manager.getServiceForMultiTenantConnection()}
 * — internal wiring that no longer exists after the {@code GormEnhancer} static maps were replaced
 * by a single {@code GormRegistry}. Services now receive tenant context through the registry's
 * selector chain combined with GORM's {@code CurrentTenantHolder}. The domain-API side of this
 * contract is covered by {@link DomainMultiTenantMultiDataSourceSpec}.</p>
 */
@RestoreSystemProperties
@Requires({ instance.manager?.supportsMultiTenantMultiDataSource() })
class DataServiceMultiTenantConnectionRoutingSpec extends GrailsDataTckSpec {

    DataServiceRoutingMetricService metricService

    void setup() {
        manager.setupMultiTenantMultiDataSource(DataServiceRoutingMetric)
        metricService = manager.getServiceForMultiTenantConnection(DataServiceRoutingMetricService, 'secondary')

        deleteAllForTenant('tenant1')
        deleteAllForTenant('tenant2')
    }

    void cleanup() {
        try {
            deleteAllForTenant('tenant1')
            deleteAllForTenant('tenant2')
        } finally {
            System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)
            manager.cleanupMultiTenantMultiDataSource()
        }
    }

    void 'save routes to secondary datasource with tenant isolation'() {
        given:
        tenant = 'tenant1'

        when: 'a metric is saved under tenant1'
        def saved = metricService.save(new DataServiceRoutingMetric(name: 'page_views', amount: 100))

        then: 'it is persisted with an ID'
        saved != null
        saved.id != null
        saved.name == 'page_views'
        saved.amount == 100
    }

    void 'get retrieves from secondary datasource'() {
        given: 'a metric saved under tenant1'
        tenant = 'tenant1'
        def saved = metricService.save(new DataServiceRoutingMetric(name: 'sessions', amount: 42))

        when: 'we retrieve it by ID'
        def found = metricService.get(saved.id)

        then: 'the correct metric is returned'
        found != null
        found.id == saved.id
        found.name == 'sessions'
        found.amount == 42
    }

    void 'count is scoped to current tenant on secondary datasource'() {
        given: 'metrics saved under tenant1'
        tenant = 'tenant1'
        metricService.save(new DataServiceRoutingMetric(name: 'alpha', amount: 1))
        metricService.save(new DataServiceRoutingMetric(name: 'beta', amount: 2))

        and: 'a metric saved under tenant2'
        tenant = 'tenant2'
        metricService.save(new DataServiceRoutingMetric(name: 'gamma', amount: 3))

        when: 'counting under tenant1'
        tenant = 'tenant1'
        def count1 = metricService.count()

        and: 'counting under tenant2'
        tenant = 'tenant2'
        def count2 = metricService.count()

        then: 'each tenant sees only its own data'
        count1 == 2
        count2 == 1
    }

    void 'delete removes from secondary datasource'() {
        given: 'a metric saved under tenant1'
        tenant = 'tenant1'
        def saved = metricService.save(new DataServiceRoutingMetric(name: 'disposable', amount: 0))
        def id = saved.id

        when: 'the metric is deleted'
        metricService.delete(id)

        then: 'it no longer exists'
        metricService.get(id) == null
        metricService.count() == 0
    }

    void 'findByName routes to secondary datasource with tenant isolation'() {
        given: 'same-named metrics under different tenants'
        tenant = 'tenant1'
        metricService.save(new DataServiceRoutingMetric(name: 'shared_name', amount: 100))
        tenant = 'tenant2'
        metricService.save(new DataServiceRoutingMetric(name: 'shared_name', amount: 200))

        when: 'finding by name under tenant1'
        tenant = 'tenant1'
        def found1 = metricService.findByName('shared_name')

        and: 'finding by name under tenant2'
        tenant = 'tenant2'
        def found2 = metricService.findByName('shared_name')

        then: 'each tenant gets its own metric'
        found1 != null
        found1.amount == 100

        found2 != null
        found2.amount == 200
    }

    private static void setTenant(String tenantId) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, tenantId)
    }

    private void deleteAllForTenant(String tenantId) {
        tenant = tenantId
        DataServiceRoutingMetric.secondary.withNewTransaction {
            DataServiceRoutingMetric.secondary.list().each { it.secondary.delete(flush: true) }
        }
    }
}
