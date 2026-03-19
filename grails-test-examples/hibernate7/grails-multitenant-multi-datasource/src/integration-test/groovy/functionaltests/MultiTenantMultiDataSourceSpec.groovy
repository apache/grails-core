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
package functionaltests

import example.Metric
import example.MetricService
import org.hibernate.Session
import spock.lang.Specification


import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * Integration test verifying that GORM Data Service auto-implemented
 * CRUD methods route correctly to a non-default datasource when both
 * DISCRIMINATOR multi-tenancy and @Transactional(connection) are used.
 *
 * Metric implements MultiTenant and is mapped to the 'secondary' datasource.
 * Without the allQualifiers() fix, the schema would be created on the
 * default datasource and all operations would silently route there.
 *
 * The service is obtained from the secondary child datastore
 * (not auto-wired by Spring) to ensure proper session binding.
 *
 * @see example.Metric
 * @see example.MetricService
 */
@Integration
class MultiTenantMultiDataSourceSpec extends Specification {

    @Autowired
    HibernateDatastore hibernateDatastore

    MetricService metricService

    void cleanup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
    }

    void setup() {
        tenant = 'tenant1'
        metricService = hibernateDatastore
                .getDatastoreForConnection('secondary')
                .getService(MetricService)
        metricService.deleteAll()
        // Also clean tenant2 data
        tenant = 'tenant2'
        metricService.deleteAll()
        // Reset to tenant1 for tests
        tenant = 'tenant1'
    }

    void "schema is created on secondary datasource not default"() {
        expect: 'The secondary datasource connects to secondaryDb'
        Metric.secondary.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == 'jdbc:h2:mem:secondaryDb'
            return true
        }

        and: 'The default datasource connects to defaultDb'
        hibernateDatastore.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == 'jdbc:h2:mem:defaultDb'
            return true
        }
    }

    void "save routes to secondary datasource"() {
        when:
        def saved = metricService.save(new Metric(name: 'page_views', amount: 100))

        then:
        saved != null
        saved.id != null
        saved.name == 'page_views'
        saved.amount == 100
    }

    void "get by ID routes to secondary datasource"() {
        given:
        def saved = metricService.save(new Metric(name: 'sessions', amount: 42))

        when:
        def found = metricService.get(saved.id)

        then:
        found != null
        found.id == saved.id
        found.name == 'sessions'
        found.amount == 42
    }

    void "count returns count scoped to current tenant"() {
        given: 'Metrics saved under tenant1'
        metricService.save(new Metric(name: 'alpha', amount: 1))
        metricService.save(new Metric(name: 'beta', amount: 2))

        and: 'Metrics saved under tenant2'
        tenant = 'tenant2'
        metricService.save(new Metric(name: 'gamma', amount: 3))

        when: 'Counting under tenant1'
        tenant = 'tenant1'
        def count1 = metricService.count()

        and: 'Counting under tenant2'
        tenant = 'tenant2'
        def count2 = metricService.count()

        then: 'Each tenant sees only its own data'
        count1 == 2
        count2 == 1
    }

    void "delete removes from secondary datasource"() {
        given:
        def saved = metricService.save(new Metric(name: 'disposable', amount: 0))

        when:
        metricService.delete(saved.id)

        then:
        metricService.get(saved.id) == null
        metricService.count() == 0
    }

    void "findByName routes to secondary datasource with tenant isolation"() {
        given: 'Same-named metrics under different tenants'
        metricService.save(new Metric(name: 'shared_name', amount: 100))

        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'tenant2')
        metricService.save(new Metric(name: 'shared_name', amount: 200))

        when: 'Finding by name under tenant1'
        tenant = 'tenant1'
        def found1 = metricService.findByName('shared_name')

        and: 'Finding by name under tenant2'
        tenant = 'tenant2'
        def found2 = metricService.findByName('shared_name')

        then: 'Each tenant gets its own metric'
        found1 != null
        found1.amount == 100

        found2 != null
        found2.amount == 200
    }

    void "findAllByName routes to secondary datasource"() {
        given:
        metricService.save(new Metric(name: 'duplicate', amount: 10))
        metricService.save(new Metric(name: 'duplicate', amount: 20))
        metricService.save(new Metric(name: 'other', amount: 30))

        when:
        def found = metricService.findAllByName('duplicate')

        then:
        found.size() == 2
        found.every { it.name == 'duplicate' }
    }

    private static void setTenant(String tenantId) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, tenantId)
    }
}
