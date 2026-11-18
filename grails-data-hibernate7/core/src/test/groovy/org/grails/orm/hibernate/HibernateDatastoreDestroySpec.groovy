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
package org.grails.orm.hibernate

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Verifies that destroying a {@link HibernateDatastore} releases what it created for its
 * additional connection sources, so that nothing keeps the destroyed datastore reachable.
 */
@RestoreSystemProperties
class HibernateDatastoreDestroySpec extends Specification {

    HibernateDatastore datastore

    void cleanup() {
        datastore?.destroy()
    }

    void "destroying a datastore removes the datastores of its additional data sources from the GORM registry"() {
        given: 'a datastore with a secondary data source'
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'dataSource.url'       : 'jdbc:h2:mem:destroyDefaultDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'  : 'create-drop',
                'dataSource.dialect'   : H2Dialect.name,
                'dataSources.secondary': [url: 'jdbc:h2:mem:destroySecondaryDB;LOCK_TIMEOUT=10000'],
        ]), DestroyMultiDataSourceBook)
        HibernateDatastore secondary = datastore.getDatastoreForConnection('secondary')
        GormRegistry registry = GormRegistry.instance

        expect: 'the registry resolves the secondary data source to its datastore'
        registry.getDatastore(DestroyMultiDataSourceBook, 'secondary').is(secondary)
        registry.datastoresByQualifier.values().any { it.is(secondary) }

        when:
        datastore.destroy()

        then: 'the registry no longer holds the secondary datastore'
        registry.getDatastore(DestroyMultiDataSourceBook, 'secondary') == null
        !registry.datastoresByQualifier.values().any { it.is(secondary) }
    }

    void "destroying a datastore closes the session factories of its schema tenants"() {
        given: 'a datastore using a schema per tenant'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'              : 'SCHEMA',
                'grails.gorm.multiTenancy.tenantResolverClass': DestroySchemaTenantsResolver,
                'dataSource.url'                             : 'jdbc:h2:mem:destroySchemaTenantDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                        : 'update',
                'dataSource.dialect'                         : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                     : 'create',
        ]), DestroySchemaTenantBook)

        and: 'a tenant added after the datastore was created'
        datastore.addTenantForSchema('destroyTenantC')

        and:
        List<String> tenantIds = ['destroyTenantA', 'destroyTenantB', 'destroyTenantC']
        List<SessionFactory> tenantSessionFactories = tenantIds.collect { String tenantId ->
            datastore.getDatastoreForConnection(tenantId).sessionFactory
        }
        List<HibernateDatastore> startupTenantDatastores = ['destroyTenantA', 'destroyTenantB'].collect { String tenantId ->
            datastore.getDatastoreForConnection(tenantId)
        }
        GormRegistry registry = GormRegistry.instance

        expect: 'every tenant has its own open session factory'
        tenantSessionFactories.unique(false) { System.identityHashCode(it) }.size() == tenantIds.size()
        !tenantSessionFactories.any { it.is(datastore.sessionFactory) }
        tenantSessionFactories.every { it.isOpen() }

        and: 'the registry holds the datastores of the tenants resolved at startup'
        startupTenantDatastores.every { HibernateDatastore tenant ->
            registry.datastoresByQualifier.values().any { it.is(tenant) }
        }

        when:
        datastore.destroy()

        then: 'the tenant session factories are closed'
        tenantSessionFactories.every { it.isClosed() }

        and: 'the registry no longer holds the tenant datastores'
        !startupTenantDatastores.any { HibernateDatastore tenant ->
            registry.datastoresByQualifier.values().any { it.is(tenant) }
        }
    }

    static class DestroySchemaTenantsResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {
        @Override
        Iterable<Serializable> resolveTenantIds() {
            ['destroyTenantA', 'destroyTenantB']
        }
    }
}

@Entity
class DestroyMultiDataSourceBook {
    String title

    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class DestroySchemaTenantBook implements MultiTenant<DestroySchemaTenantBook> {
    String title
}
