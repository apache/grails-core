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

import java.util.UUID

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

@RestoreSystemProperties
class GormApiAllocationSpec extends Specification {

    @AutoCleanup HibernateDatastore datastore

    void cleanup() {
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)
    }

    void "DISCRIMINATOR multi tenant single datasource uses only default APIs"() {
        when:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, [], H7AllocationDefaultTenant)

        then:
        hasAllApis(H7AllocationDefaultTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H7AllocationDefaultTenant, 'tenantA')

        when:
        def result = H7AllocationDefaultTenant.withTenant('tenantA') { true }

        then:
        result
        !hasAnyApis(H7AllocationDefaultTenant, 'tenantA')
    }

    void "instance API mirrors the datastore markDirty and defaults to false, not the enhancer default of true"() {
        when: "a datastore is created without explicit grails.gorm.markDirty configuration"
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, [], H7AllocationDefaultTenant)

        then: "Hibernate manages its own dirty checking, so the instance API must not force-mark entities dirty"
        !datastore.markDirty
        !GormEnhancer.findInstanceApi(H7AllocationDefaultTenant, ConnectionSource.DEFAULT).markDirty
    }

    void "DISCRIMINATOR multi tenant default datasource with multiple configured datasources creates non-default APIs lazily"() {
        given:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, ['analytics', 'reporting'], H7AllocationDefaultTenant)

        expect:
        hasAllApis(H7AllocationDefaultTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H7AllocationDefaultTenant, 'analytics')
        !hasAnyApis(H7AllocationDefaultTenant, 'reporting')

        when:
        def staticApi = GormEnhancer.findStaticApi(H7AllocationDefaultTenant, 'analytics')
        def instanceApi = GormEnhancer.findInstanceApi(H7AllocationDefaultTenant, 'analytics')
        def validationApi = GormEnhancer.findValidationApi(H7AllocationDefaultTenant, 'analytics')

        then:
        hasAllApis(H7AllocationDefaultTenant, 'analytics')
        !hasAnyApis(H7AllocationDefaultTenant, 'reporting')
        !staticApi.is(GormEnhancer.findStaticApi(H7AllocationDefaultTenant, ConnectionSource.DEFAULT))
        instanceApi.is(GormEnhancer.findInstanceApi(H7AllocationDefaultTenant, 'analytics'))
        validationApi.is(GormEnhancer.findValidationApi(H7AllocationDefaultTenant, 'analytics'))
    }

    void "SCHEMA multi tenant single datasource creates tenant APIs lazily"() {
        given:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.SCHEMA, [], H7AllocationSchemaTenant)

        expect:
        hasAllApis(H7AllocationSchemaTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H7AllocationSchemaTenant, 'tenantA')
        !hasAnyApis(H7AllocationSchemaTenant, 'tenantB')

        when:
        def staticApi = GormEnhancer.findStaticApi(H7AllocationSchemaTenant, 'tenantA')
        def instanceApi = GormEnhancer.findInstanceApi(H7AllocationSchemaTenant, 'tenantA')
        def validationApi = GormEnhancer.findValidationApi(H7AllocationSchemaTenant, 'tenantA')

        then:
        hasAllApis(H7AllocationSchemaTenant, 'tenantA')
        !hasAnyApis(H7AllocationSchemaTenant, 'tenantB')
        // Strict-mode (SCHEMA) entities have no tenant-less DEFAULT API to resolve (that requires a
        // bound tenant); verify the tenant API is a stable, distinct per-tenant allocation via its
        // cached identity instead.
        staticApi.is(GormEnhancer.findStaticApi(H7AllocationSchemaTenant, 'tenantA'))
        instanceApi.is(GormEnhancer.findInstanceApi(H7AllocationSchemaTenant, 'tenantA'))
        validationApi.is(GormEnhancer.findValidationApi(H7AllocationSchemaTenant, 'tenantA'))
    }

    void "DATABASE multi tenant creates tenant APIs lazily"() {
        given:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DATABASE, ['tenantA', 'tenantB'], H7AllocationDatabaseTenant)

        expect:
        hasAllApis(H7AllocationDatabaseTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H7AllocationDatabaseTenant, 'tenantA')
        !hasAnyApis(H7AllocationDatabaseTenant, 'tenantB')

        when:
        def staticApi = GormEnhancer.findStaticApi(H7AllocationDatabaseTenant, 'tenantA')
        def instanceApi = GormEnhancer.findInstanceApi(H7AllocationDatabaseTenant, 'tenantA')
        def validationApi = GormEnhancer.findValidationApi(H7AllocationDatabaseTenant, 'tenantA')

        then:
        hasAllApis(H7AllocationDatabaseTenant, 'tenantA')
        !hasAnyApis(H7AllocationDatabaseTenant, 'tenantB')
        // Strict-mode (DATABASE) entities have no tenant-less DEFAULT API to resolve (that requires a
        // bound tenant); verify the tenant API is a stable, distinct per-tenant allocation via its
        // cached identity instead.
        staticApi.is(GormEnhancer.findStaticApi(H7AllocationDatabaseTenant, 'tenantA'))
        instanceApi.is(GormEnhancer.findInstanceApi(H7AllocationDatabaseTenant, 'tenantA'))
        validationApi.is(GormEnhancer.findValidationApi(H7AllocationDatabaseTenant, 'tenantA'))
    }

    void "DISCRIMINATOR multi tenant explicit datasource allocates only mapped datasource APIs"() {
        when:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, ['analytics', 'reporting'], H7AllocationAnalyticsTenant)

        then:
        hasAllApis(H7AllocationAnalyticsTenant, ConnectionSource.DEFAULT)
        hasAllApis(H7AllocationAnalyticsTenant, 'analytics')
        !hasAnyApis(H7AllocationAnalyticsTenant, 'reporting')
    }

    void "DISCRIMINATOR multi tenant explicit multiple datasources allocates only mapped datasource APIs"() {
        when:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, ['analytics', 'reporting', 'audit'], H7AllocationMultipleDatasourceTenant)

        then:
        hasAllApis(H7AllocationMultipleDatasourceTenant, ConnectionSource.DEFAULT)
        hasAllApis(H7AllocationMultipleDatasourceTenant, 'analytics')
        hasAllApis(H7AllocationMultipleDatasourceTenant, 'reporting')
        !hasAnyApis(H7AllocationMultipleDatasourceTenant, 'audit')
    }

    void "SCHEMA multi tenant creating a second datastore for the same entity evicts stale lazy-cached tenant APIs"() {
        given: "a SCHEMA datastore with tenant APIs lazily populated"
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.SCHEMA, [], H7AllocationSchemaTenant)
        GormEnhancer.findStaticApi(H7AllocationSchemaTenant, 'tenantA')
        GormEnhancer.findInstanceApi(H7AllocationSchemaTenant, 'tenantA')
        GormEnhancer.findValidationApi(H7AllocationSchemaTenant, 'tenantA')

        expect: "the cache holds APIs for tenantA after the first access"
        hasAllApis(H7AllocationSchemaTenant, 'tenantA')

        when: "a new SCHEMA datastore is created for the same entity (e.g. in a new test spec's setup)"
        HibernateDatastore datastore2 = newDatastore(MultiTenancySettings.MultiTenancyMode.SCHEMA, [], H7AllocationSchemaTenant)

        then: "stale cached APIs for tenantA are evicted so the next access re-creates them against the new session factory"
        !hasAnyApis(H7AllocationSchemaTenant, 'tenantA')

        and: "DEFAULT APIs are not affected"
        hasAllApis(H7AllocationSchemaTenant, ConnectionSource.DEFAULT)

        cleanup:
        datastore2?.close()
    }

    void "DISCRIMINATOR multi tenant ALL datasource creates non-default datasource APIs lazily"() {
        given:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, ['analytics', 'reporting'], H7AllocationAllDatasourceTenant)

        expect:
        hasAllApis(H7AllocationAllDatasourceTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H7AllocationAllDatasourceTenant, 'analytics')
        !hasAnyApis(H7AllocationAllDatasourceTenant, 'reporting')

        when:
        GormEnhancer.findStaticApi(H7AllocationAllDatasourceTenant, 'analytics')
        GormEnhancer.findInstanceApi(H7AllocationAllDatasourceTenant, 'analytics')
        GormEnhancer.findValidationApi(H7AllocationAllDatasourceTenant, 'analytics')

        then:
        hasAllApis(H7AllocationAllDatasourceTenant, 'analytics')
        !hasAnyApis(H7AllocationAllDatasourceTenant, 'reporting')
    }

    private HibernateDatastore newDatastore(MultiTenancySettings.MultiTenancyMode mode, List<String> extraDataSources, Class<?>... domainClasses) {
        Map config = baseConfig(mode, domainClasses[0].simpleName)
        extraDataSources.each { String name ->
            config["dataSources.${name}.url".toString()] = "jdbc:h2:mem:${databaseName(domainClasses[0].simpleName + name)};LOCK_TIMEOUT=10000".toString()
        }
        new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), domainClasses)
    }

    private static Map baseConfig(MultiTenancySettings.MultiTenancyMode mode, String name) {
        [
                'grails.gorm.multiTenancy.mode': mode,
                'grails.gorm.multiTenancy.tenantResolverClass': H7AllocationTenantResolver,
                'dataSource.url': "jdbc:h2:mem:${databaseName(name)};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': mode == MultiTenancySettings.MultiTenancyMode.SCHEMA ? 'update' : 'create-drop',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.hbm2ddl.auto': mode == MultiTenancySettings.MultiTenancyMode.SCHEMA ? 'create' : 'create-drop'
        ]
    }

    private static String databaseName(String name) {
        "h7_${name}_${UUID.randomUUID().toString().replace('-', '')}"
    }

    private static boolean hasAllApis(Class<?> type, String qualifier) {
        hasStaticApi(type, qualifier) && hasInstanceApi(type, qualifier) && hasValidationApi(type, qualifier)
    }

    private static boolean hasAnyApis(Class<?> type, String qualifier) {
        hasStaticApi(type, qualifier) || hasInstanceApi(type, qualifier) || hasValidationApi(type, qualifier)
    }

    private static boolean hasStaticApi(Class<?> type, String qualifier) {
        GormRegistry.instance.staticApiRegistry.isAllocated(type.name, qualifier)
    }

    private static boolean hasInstanceApi(Class<?> type, String qualifier) {
        GormRegistry.instance.instanceApiRegistry.isAllocated(type.name, qualifier)
    }

    private static boolean hasValidationApi(Class<?> type, String qualifier) {
        GormRegistry.instance.validationApiRegistry.isAllocated(type.name, qualifier)
    }
}

class H7AllocationTenantResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {

    @Override
    Iterable<Serializable> resolveTenantIds() {
        ['tenantA', 'tenantB']
    }
}

@Entity
class H7AllocationDefaultTenant implements GormEntity<H7AllocationDefaultTenant>, MultiTenant<H7AllocationDefaultTenant> {
    Long id
    Long version
    String tenantId
    String name
}

@Entity
class H7AllocationSchemaTenant implements GormEntity<H7AllocationSchemaTenant>, MultiTenant<H7AllocationSchemaTenant> {
    Long id
    Long version
    String tenantId
    String name
}

@Entity
class H7AllocationDatabaseTenant implements GormEntity<H7AllocationDatabaseTenant>, MultiTenant<H7AllocationDatabaseTenant> {
    Long id
    Long version
    String tenantId
    String name
}

@Entity
class H7AllocationAnalyticsTenant implements GormEntity<H7AllocationAnalyticsTenant>, MultiTenant<H7AllocationAnalyticsTenant> {
    Long id
    Long version
    String tenantId
    String name

    static mapping = {
        datasource 'analytics'
    }
}

@Entity
class H7AllocationMultipleDatasourceTenant implements GormEntity<H7AllocationMultipleDatasourceTenant>, MultiTenant<H7AllocationMultipleDatasourceTenant> {
    Long id
    Long version
    String tenantId
    String name

    static mapping = {
        datasources(['analytics', 'reporting'])
    }
}

@Entity
class H7AllocationAllDatasourceTenant implements GormEntity<H7AllocationAllDatasourceTenant>, MultiTenant<H7AllocationAllDatasourceTenant> {
    Long id
    Long version
    String tenantId
    String name

    static mapping = {
        datasource ConnectionSource.ALL
    }
}
