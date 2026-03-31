/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
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

@RestoreSystemProperties
@Requires({ instance.manager?.supportsMultiTenantMultiDataSource() })
class DomainMultiTenantMultiDataSourceSpec extends GrailsDataTckSpec {

    void setup() {
        manager.setupMultiTenantMultiDataSource(DataServiceRoutingMetric)
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

    void "save with tenant isolation on secondary via domain API"() {
        given: 'a tenant selected'
        setTenant('tenant1')
        when: 'a metric is saved under tenant1'
        DataServiceRoutingMetric.secondary.withNewTransaction {
            new DataServiceRoutingMetric(name: 'page_views', amount: 100).secondary.save(flush: true)
        }

        then: 'count reflects tenant scoped data'
        DataServiceRoutingMetric.secondary.withNewTransaction {
            DataServiceRoutingMetric.secondary.count()
        } == 1
    }

    void "count scoped to tenant on secondary via domain API"() {
        given: 'metrics under tenant1'
        setTenant('tenant1')
        saveMetric('alpha', 1)
        saveMetric('beta', 2)

        and: 'a metric under tenant2'
        setTenant('tenant2')
        saveMetric('gamma', 3)

        when: 'counting under tenant1'
        setTenant('tenant1')
        def tenant1Count = countMetrics()

        and: 'counting under tenant2'
        setTenant('tenant2')
        def tenant2Count = countMetrics()

        then: 'each tenant sees only its data'
        tenant1Count == 2
        tenant2Count == 1
    }

    void "criteria query scoped to tenant on secondary via domain API"() {
        given: 'same named metrics across tenants'
        setTenant('tenant1')
        saveMetric('shared', 10)
        setTenant('tenant2')
        saveMetric('shared', 20)

        when: 'querying by name under tenant1'
        setTenant('tenant1')
        def tenant1Results = findByName('shared')

        and: 'querying by name under tenant2'
        setTenant('tenant2')
        def tenant2Results = findByName('shared')

        then: 'results are isolated per tenant'
        tenant1Results.size() == 1
        tenant1Results.first().amount == 10
        tenant2Results.size() == 1
        tenant2Results.first().amount == 20
    }

    void "delete with tenant isolation on secondary via domain API"() {
        given: 'a metric saved under tenant1'
        setTenant('tenant1')
        def saved = DataServiceRoutingMetric.secondary.withNewTransaction {
            def item = new DataServiceRoutingMetric(name: 'disposable', amount: 0)
            item.secondary.save(flush: true)
            item
        }

        when: 'the metric is deleted'
        DataServiceRoutingMetric.secondary.withNewTransaction {
            saved.secondary.delete(flush: true)
        }

        then: 'tenant1 has no metrics'
        countMetrics() == 0
    }

    void "tenant1 data not visible to tenant2 via domain API"() {
        given: 'data under tenant1'
        setTenant('tenant1')
        saveMetric('isolated', 5)

        when: 'switching to tenant2'
        setTenant('tenant2')

        then: 'tenant2 sees no data'
        countMetrics() == 0
    }

    private static void setTenant(String tenantId) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, tenantId)
    }

    private void saveMetric(String name, Integer amount) {
        DataServiceRoutingMetric.secondary.withNewTransaction {
            new DataServiceRoutingMetric(name: name, amount: amount).secondary.save(flush: true)
        }
    }

    private long countMetrics() {
        DataServiceRoutingMetric.secondary.withNewTransaction {
            DataServiceRoutingMetric.secondary.count()
        }
    }

    private List<DataServiceRoutingMetric> findByName(String name) {
        DataServiceRoutingMetric.secondary.withNewTransaction {
            DataServiceRoutingMetric.secondary.withCriteria {
                eq 'name', name
            }
        }
    }

    private void deleteAllForTenant(String tenantId) {
        setTenant(tenantId)
        DataServiceRoutingMetric.secondary.withNewTransaction {
            DataServiceRoutingMetric.secondary.list().each { it.secondary.delete(flush: true) }
        }
    }
}
