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
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * Verifies that a {@code @Service}+{@code @CurrentTenant} class routes correctly in
 * DATABASE multi-tenancy mode - i.e. that the TenantService lookup resolves the primary
 * (multi-connection) datastore rather than a per-tenant child datastore that only knows
 * its own single connection.
 */
@RestoreSystemProperties
class DatabaseMultiTenantServiceRoutingSpec extends Specification {

    @AutoCleanup HibernateDatastore datastore

    void cleanup() {
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)
    }

    void "a @Service + @CurrentTenant class routes to the correct tenant datasource in DATABASE mode"() {
        given:
        String dbName = "h5_dbtenantsvc_${UUID.randomUUID().toString().replace('-', '')}"
        Map config = [
                'grails.gorm.multiTenancy.mode'              : MultiTenancySettings.MultiTenancyMode.DATABASE,
                'grails.gorm.multiTenancy.tenantResolverClass': DbTenantServiceResolver,
                'dataSource.url'                              : "jdbc:h2:mem:${dbName};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'                          : 'create-drop',
                'dataSource.dialect'                           : H2Dialect.name,
                'hibernate.flush.mode'                         : 'COMMIT',
                'hibernate.hbm2ddl.auto'                       : 'create-drop',
                "dataSources.tenantA.url"                      : "jdbc:h2:mem:${dbName}_a;LOCK_TIMEOUT=10000".toString(),
                "dataSources.tenantB.url"                      : "jdbc:h2:mem:${dbName}_b;LOCK_TIMEOUT=10000".toString(),
        ]
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), DbTenantWidget)
        DbTenantWidgetService service = datastore.getService(DbTenantWidgetService)

        when: 'operating as tenantA'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'tenantA')
        service.save(new DbTenantWidget(name: 'widgetA'))

        and: 'operating as tenantB'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'tenantB')
        service.save(new DbTenantWidget(name: 'widgetB'))

        then: 'each tenant only sees its own data - no ConfigurationException/DataSource-not-found from resolving the wrong (child) datastore'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'tenantA')
        service.count() == 1
        service.findByName('widgetA') != null

        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'tenantB')
        service.count() == 1
        service.findByName('widgetB') != null
    }
}

class DbTenantServiceResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {
    @Override
    Iterable<Serializable> resolveTenantIds() {
        ['tenantA', 'tenantB']
    }
}

@Entity
class DbTenantWidget implements GormEntity<DbTenantWidget>, MultiTenant<DbTenantWidget> {
    Long id
    Long version
    String tenantId
    String name

    static constraints = {
        tenantId nullable: true
    }
}

@CurrentTenant
@Service(DbTenantWidget)
@Transactional
abstract class DbTenantWidgetService {
    abstract DbTenantWidget save(DbTenantWidget widget)
    abstract Number count()
    abstract DbTenantWidget findByName(String name)
}
