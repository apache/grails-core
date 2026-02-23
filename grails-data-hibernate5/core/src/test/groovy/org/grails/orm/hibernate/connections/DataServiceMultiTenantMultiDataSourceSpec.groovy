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
package org.grails.orm.hibernate.connections

import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * Tests GORM Data Service auto-implemented CRUD methods when both DISCRIMINATOR
 * multi-tenancy and a non-default datasource are configured on the same domain.
 *
 * This combination triggers the allQualifiers() bug: when MultiTenant is present,
 * allQualifiers() returns tenant IDs instead of datasource names, causing schema
 * creation and query routing to go to the wrong database.
 *
 * Covers:
 * - Schema creation on the correct (analytics) datasource for MultiTenant domains
 * - save(), get(), delete(), count() with tenant isolation on secondary datasource
 * - findBy* dynamic finders with tenant isolation on secondary datasource
 * - GormEnhancer escape-hatch for aggregate HQL on secondary datasource
 * - Tenant isolation: same-named data under different tenants stays separate
 *
 * @see PartitionedMultiTenancySpec for basic DISCRIMINATOR multi-tenancy
 * @see MultipleDataSourceConnectionsSpec for Data Services on secondary datasource without multi-tenancy
 */
@RestoreSystemProperties
class DataServiceMultiTenantMultiDataSourceSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore

    void setupSpec() {
        Map config = [
                "grails.gorm.multiTenancy.mode": MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                "grails.gorm.multiTenancy.tenantResolverClass": SystemPropertyTenantResolver,
                'dataSource.url': "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'create-drop',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create-drop',
                'dataSources.analytics': [url: "jdbc:h2:mem:analyticsDB;LOCK_TIMEOUT=10000"],
        ]

        datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(config), Metric)
    }

    MetricService metricService

    void setup() {
        tenant = 'tenant1'
        metricService = datastore
                .getDatastoreForConnection('analytics')
                .getService(MetricService)
        metricService.deleteAll()
        // Also clean tenant2 data
        tenant = 'tenant2'
        metricService.deleteAll()
        // Reset to tenant1 for tests
        tenant = 'tenant1'
    }

    void "schema is created on analytics datasource"() {
        expect: 'The analytics datasource connects to the analyticsDB H2 database'
        Metric.analytics.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == 'jdbc:h2:mem:analyticsDB'
            return true
        }

        and: 'The default datasource connects to a different database'
        datastore.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == 'jdbc:h2:mem:grailsDB'
            return true
        }
    }

    void "save routes to analytics datasource with tenant isolation"() {
        when: 'A metric is saved under tenant1'
        def saved = metricService.save(new Metric(name: 'page_views', amount: 100))

        then: 'The metric is persisted with an ID'
        saved != null
        saved.id != null
        saved.name == 'page_views'
        saved.amount == 100

        and: 'The metric is retrievable via the analytics datasource qualifier'
        Metric.analytics.withNewSession {
            Metric.analytics.get(saved.id) != null
        }
    }

    void "get retrieves from analytics datasource"() {
        given: 'A metric saved to the analytics datasource'
        def saved = metricService.save(new Metric(name: 'sessions', amount: 42))

        when: 'The metric is retrieved by ID'
        def found = metricService.get(saved.id)

        then: 'The correct metric is returned'
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

    void "delete removes from analytics datasource"() {
        given: 'A metric saved under tenant1'
        def saved = metricService.save(new Metric(name: 'disposable', amount: 0))
        def id = saved.id

        when: 'The metric is deleted'
        metricService.delete(id)

        then: 'The metric is no longer retrievable'
        metricService.get(id) == null
        metricService.count() == 0
    }

    void "findByName routes to analytics datasource with tenant isolation"() {
        given: 'Same-named metrics under different tenants'
        metricService.save(new Metric(name: 'shared_name', amount: 100))
        tenant = 'tenant2'
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

    void "GormEnhancer resolves analytics qualifier for MultiTenant entity with explicit datasource"() {
        when: 'Looking up the static API without specifying a connection'
        def api = GormEnhancer.findStaticApi(Metric)

        then: 'The API is registered and functional (schema exists on correct datasource)'
        api != null

        when: 'Using the explicit analytics qualifier'
        def analyticsApi = GormEnhancer.findStaticApi(Metric, 'analytics')

        then: 'The analytics API is also registered'
        analyticsApi != null
    }

    void "GormEnhancer aggregate HQL routes to analytics datasource"() {
        given: 'Multiple metrics saved under tenant1'
        metricService.save(new Metric(name: 'alpha', amount: 10))
        metricService.save(new Metric(name: 'beta', amount: 20))
        metricService.save(new Metric(name: 'gamma', amount: 30))

        when: 'Using GormEnhancer for an aggregate query'
        def results = metricService.getTotalAmountAbove(15)

        then: 'The HQL executes against the analytics datasource'
        results.size() == 1
        results[0] == 50  // 20 + 30
    }
    
    private static void setTenant(String tenantId) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, tenantId)
    }
}

/**
 * Metric domain mapped to the 'analytics' datasource with DISCRIMINATOR multi-tenancy.
 * This combination triggers the allQualifiers() bug when both MultiTenant and
 * a non-default datasource are configured.
 */
@Entity
class Metric implements GormEntity<Metric>, MultiTenant<Metric> {
    Long id
    Long version
    String tenantId
    String name
    Integer amount

    static mapping = {
        datasource 'analytics'
    }

    static constraints = {
        name blank: false
        amount min: 0
    }
}

/**
 * Data Service interface for Metric - all methods auto-implemented by GORM.
 */
interface MetricDataService {
    Metric get(Serializable id)
    Metric save(Metric metric)
    void delete(Serializable id)
    Long count()
    Metric findByName(String name)
    List<Metric> findAllByAmountGreaterThan(Integer amount)
}

/**
 * Abstract class that binds MetricDataService to the 'analytics' datasource.
 * The @Transactional(connection = "analytics") ensures all auto-implemented methods
 * and custom methods route to the secondary datasource.
 */
@Service(Metric)
@Transactional(connection = 'analytics')
abstract class MetricService implements MetricDataService {

    /**
     * Statically compiled access to the analytics datasource via GormEnhancer.
     */
    private GormStaticApi<Metric> getAnalyticsApi() {
        GormEnhancer.findStaticApi(Metric, 'analytics')
    }

    /**
     * Delete all metrics for the current tenant from the analytics datasource.
     */
    void deleteAll() {
        analyticsApi.executeUpdate('delete from Metric')
    }

    /**
     * Aggregate query - calculates total amount of metrics above a threshold.
     */
    List getTotalAmountAbove(Integer minAmount) {
        analyticsApi.executeQuery(
            'select sum(m.amount) from Metric m where m.amount > :minAmount',
            [minAmount: minAmount]
        )
    }
}
