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
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.GormEntity
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
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, [], H5AllocationDefaultTenant)

        then:
        hasAllApis(H5AllocationDefaultTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H5AllocationDefaultTenant, 'tenantA')

        when:
        def result = H5AllocationDefaultTenant.withTenant('tenantA') { true }

        then:
        result
        !hasAnyApis(H5AllocationDefaultTenant, 'tenantA')
    }

    void "DISCRIMINATOR multi tenant default datasource with multiple configured datasources creates non-default APIs lazily"() {
        given:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, ['analytics', 'reporting'], H5AllocationDefaultTenant)

        expect:
        hasAllApis(H5AllocationDefaultTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H5AllocationDefaultTenant, 'analytics')
        !hasAnyApis(H5AllocationDefaultTenant, 'reporting')

        when:
        def staticApi = GormEnhancer.findStaticApi(H5AllocationDefaultTenant, 'analytics')
        def instanceApi = GormEnhancer.findInstanceApi(H5AllocationDefaultTenant, 'analytics')
        def validationApi = GormEnhancer.findValidationApi(H5AllocationDefaultTenant, 'analytics')

        then:
        hasAllApis(H5AllocationDefaultTenant, 'analytics')
        !hasAnyApis(H5AllocationDefaultTenant, 'reporting')
        !staticApi.is(GormEnhancer.findStaticApi(H5AllocationDefaultTenant, ConnectionSource.DEFAULT))
        instanceApi.is(GormEnhancer.findInstanceApi(H5AllocationDefaultTenant, 'analytics'))
        validationApi.is(GormEnhancer.findValidationApi(H5AllocationDefaultTenant, 'analytics'))
    }

    void "SCHEMA multi tenant single datasource creates tenant APIs lazily"() {
        given:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.SCHEMA, [], H5AllocationSchemaTenant)

        expect:
        hasAllApis(H5AllocationSchemaTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H5AllocationSchemaTenant, 'tenantA')
        !hasAnyApis(H5AllocationSchemaTenant, 'tenantB')

        when:
        def staticApi = GormEnhancer.findStaticApi(H5AllocationSchemaTenant, 'tenantA')
        def instanceApi = GormEnhancer.findInstanceApi(H5AllocationSchemaTenant, 'tenantA')
        def validationApi = GormEnhancer.findValidationApi(H5AllocationSchemaTenant, 'tenantA')

        then:
        hasAllApis(H5AllocationSchemaTenant, 'tenantA')
        !hasAnyApis(H5AllocationSchemaTenant, 'tenantB')
        // Strict-mode (SCHEMA) entities have no tenant-less DEFAULT API to resolve (that requires a
        // bound tenant); verify the tenant API is a stable, distinct per-tenant allocation via its
        // cached identity instead.
        staticApi.is(GormEnhancer.findStaticApi(H5AllocationSchemaTenant, 'tenantA'))
        instanceApi.is(GormEnhancer.findInstanceApi(H5AllocationSchemaTenant, 'tenantA'))
        validationApi.is(GormEnhancer.findValidationApi(H5AllocationSchemaTenant, 'tenantA'))
    }

    void "DATABASE multi tenant creates tenant APIs lazily"() {
        given:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DATABASE, ['tenantA', 'tenantB'], H5AllocationDatabaseTenant)

        expect:
        hasAllApis(H5AllocationDatabaseTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H5AllocationDatabaseTenant, 'tenantA')
        !hasAnyApis(H5AllocationDatabaseTenant, 'tenantB')

        when:
        def staticApi = GormEnhancer.findStaticApi(H5AllocationDatabaseTenant, 'tenantA')
        def instanceApi = GormEnhancer.findInstanceApi(H5AllocationDatabaseTenant, 'tenantA')
        def validationApi = GormEnhancer.findValidationApi(H5AllocationDatabaseTenant, 'tenantA')

        then:
        hasAllApis(H5AllocationDatabaseTenant, 'tenantA')
        !hasAnyApis(H5AllocationDatabaseTenant, 'tenantB')
        // Strict-mode (DATABASE) entities have no tenant-less DEFAULT API to resolve (that requires a
        // bound tenant); verify the tenant API is a stable, distinct per-tenant allocation via its
        // cached identity instead.
        staticApi.is(GormEnhancer.findStaticApi(H5AllocationDatabaseTenant, 'tenantA'))
        instanceApi.is(GormEnhancer.findInstanceApi(H5AllocationDatabaseTenant, 'tenantA'))
        validationApi.is(GormEnhancer.findValidationApi(H5AllocationDatabaseTenant, 'tenantA'))
    }

    void "DISCRIMINATOR multi tenant explicit datasource allocates only mapped datasource APIs"() {
        when:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, ['analytics', 'reporting'], H5AllocationAnalyticsTenant)

        then:
        hasAllApis(H5AllocationAnalyticsTenant, ConnectionSource.DEFAULT)
        hasAllApis(H5AllocationAnalyticsTenant, 'analytics')
        !hasAnyApis(H5AllocationAnalyticsTenant, 'reporting')
    }

    void "DISCRIMINATOR multi tenant explicit multiple datasources allocates only mapped datasource APIs"() {
        when:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, ['analytics', 'reporting', 'audit'], H5AllocationMultipleDatasourceTenant)

        then:
        hasAllApis(H5AllocationMultipleDatasourceTenant, ConnectionSource.DEFAULT)
        hasAllApis(H5AllocationMultipleDatasourceTenant, 'analytics')
        hasAllApis(H5AllocationMultipleDatasourceTenant, 'reporting')
        !hasAnyApis(H5AllocationMultipleDatasourceTenant, 'audit')
    }

    void "SCHEMA multi tenant re-registering a tenant via addTenantForSchema evicts stale lazy-cached APIs for that qualifier"() {
        given: "a SCHEMA datastore and the first addTenantForSchema call creates tenantA's child datastore"
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.SCHEMA, [], H5AllocationSchemaTenant)
        datastore.addTenantForSchema('tenantA')

        and: "tenantA APIs are lazily populated on first access"
        GormEnhancer.findStaticApi(H5AllocationSchemaTenant, 'tenantA')
        GormEnhancer.findInstanceApi(H5AllocationSchemaTenant, 'tenantA')
        GormEnhancer.findValidationApi(H5AllocationSchemaTenant, 'tenantA')

        expect: "the cache holds APIs for tenantA after the first access"
        hasAllApis(H5AllocationSchemaTenant, 'tenantA')

        when: "addTenantForSchema is called again, re-creating the child datastore (as happens in per-test setup)"
        datastore.addTenantForSchema('tenantA')

        then: "stale cached APIs for tenantA are evicted so the next access re-creates them against the new session factory"
        !hasAnyApis(H5AllocationSchemaTenant, 'tenantA')

        and: "DEFAULT APIs are not affected"
        hasAllApis(H5AllocationSchemaTenant, ConnectionSource.DEFAULT)
    }

    void "DISCRIMINATOR multi tenant ALL datasource creates non-default datasource APIs lazily"() {
        given:
        datastore = newDatastore(MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR, ['analytics', 'reporting'], H5AllocationAllDatasourceTenant)

        expect:
        hasAllApis(H5AllocationAllDatasourceTenant, ConnectionSource.DEFAULT)
        !hasAnyApis(H5AllocationAllDatasourceTenant, 'analytics')
        !hasAnyApis(H5AllocationAllDatasourceTenant, 'reporting')

        when:
        GormEnhancer.findStaticApi(H5AllocationAllDatasourceTenant, 'analytics')
        GormEnhancer.findInstanceApi(H5AllocationAllDatasourceTenant, 'analytics')
        GormEnhancer.findValidationApi(H5AllocationAllDatasourceTenant, 'analytics')

        then:
        hasAllApis(H5AllocationAllDatasourceTenant, 'analytics')
        !hasAnyApis(H5AllocationAllDatasourceTenant, 'reporting')
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
                'grails.gorm.multiTenancy.tenantResolverClass': H5AllocationTenantResolver,
                'dataSource.url': "jdbc:h2:mem:${databaseName(name)};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': mode == MultiTenancySettings.MultiTenancyMode.SCHEMA ? 'update' : 'create-drop',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.hbm2ddl.auto': mode == MultiTenancySettings.MultiTenancyMode.SCHEMA ? 'create' : 'create-drop'
        ]
    }

    private static String databaseName(String name) {
        "h5_${name}_${UUID.randomUUID().toString().replace('-', '')}"
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

class H5AllocationTenantResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {

    @Override
    Iterable<Serializable> resolveTenantIds() {
        ['tenantA', 'tenantB']
    }
}

@Entity
class H5AllocationDefaultTenant implements GormEntity<H5AllocationDefaultTenant>, MultiTenant<H5AllocationDefaultTenant> {
    Long id
    Long version
    String tenantId
    String name
}

@Entity
class H5AllocationSchemaTenant implements GormEntity<H5AllocationSchemaTenant>, MultiTenant<H5AllocationSchemaTenant> {
    Long id
    Long version
    String tenantId
    String name
}

@Entity
class H5AllocationDatabaseTenant implements GormEntity<H5AllocationDatabaseTenant>, MultiTenant<H5AllocationDatabaseTenant> {
    Long id
    Long version
    String tenantId
    String name
}

@Entity
class H5AllocationAnalyticsTenant implements GormEntity<H5AllocationAnalyticsTenant>, MultiTenant<H5AllocationAnalyticsTenant> {
    Long id
    Long version
    String tenantId
    String name

    static mapping = {
        datasource 'analytics'
    }
}

@Entity
class H5AllocationMultipleDatasourceTenant implements GormEntity<H5AllocationMultipleDatasourceTenant>, MultiTenant<H5AllocationMultipleDatasourceTenant> {
    Long id
    Long version
    String tenantId
    String name

    static mapping = {
        datasources(['analytics', 'reporting'])
    }
}

@Entity
class H5AllocationAllDatasourceTenant implements GormEntity<H5AllocationAllDatasourceTenant>, MultiTenant<H5AllocationAllDatasourceTenant> {
    Long id
    Long version
    String tenantId
    String name

    static mapping = {
        datasource ConnectionSource.ALL
    }
}
