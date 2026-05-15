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

@RestoreSystemProperties
@Requires({ instance.manager?.supportsMultiTenantMultiDataSource() })
class CrossLayerMultiTenantMultiDataSourceSpec extends GrailsDataTckSpec {

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

    void 'domain save with tenant visible through service'() {
        given: 'tenant1 selected'
        setTenant('tenant1')
        def saved = saveDomainMetric('domain_metric', 10)

        when: 'retrieved through the service'
        def found = metricService.findByName('domain_metric')

        then: 'service sees tenant1 data'
        found != null
        found.id == saved.id
    }

    void 'service save with tenant visible through domain API'() {
        given: 'tenant1 selected'
        setTenant('tenant1')
        def saved = metricService.save(new DataServiceRoutingMetric(name: 'service_metric', amount: 20))

        when: 'retrieved through the domain API'
        def found = DataServiceRoutingMetric.secondary.withNewTransaction {
            DataServiceRoutingMetric.secondary.get(saved.id)
        }

        then: 'domain API sees service data'
        found != null
        found.id == saved.id
        found.name == 'service_metric'
    }

    void 'tenant isolation consistent across layers'() {
        given: 'tenant1 data saved via domain API'
        setTenant('tenant1')
        saveDomainMetric('metric1', 1)

        and: 'tenant2 data saved via service'
        setTenant('tenant2')
        metricService.save(new DataServiceRoutingMetric(name: 'metric2', amount: 2))

        when: 'querying tenant1 through both layers'
        setTenant('tenant1')
        def tenant1DomainCount = countMetrics()
        def tenant1ServiceCount = metricService.count()

        and: 'querying tenant2 through both layers'
        setTenant('tenant2')
        def tenant2DomainCount = countMetrics()
        def tenant2ServiceCount = metricService.count()

        then: 'each tenant only sees its own data'
        tenant1DomainCount == 1
        tenant1ServiceCount == 1
        tenant2DomainCount == 1
        tenant2ServiceCount == 1
    }

    private static void setTenant(String tenantId) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, tenantId)
    }

    private DataServiceRoutingMetric saveDomainMetric(String name, Integer amount) {
        DataServiceRoutingMetric.secondary.withNewTransaction {
            def item = new DataServiceRoutingMetric(name: name, amount: amount)
            item.secondary.save(flush: true)
            item
        }
    }

    private long countMetrics() {
        DataServiceRoutingMetric.secondary.withNewTransaction {
            DataServiceRoutingMetric.secondary.count()
        }
    }

    private void deleteAllForTenant(String tenantId) {
        setTenant(tenantId)
        DataServiceRoutingMetric.secondary.withNewTransaction {
            DataServiceRoutingMetric.secondary.list().each { it.secondary.delete(flush: true) }
        }
    }
}
