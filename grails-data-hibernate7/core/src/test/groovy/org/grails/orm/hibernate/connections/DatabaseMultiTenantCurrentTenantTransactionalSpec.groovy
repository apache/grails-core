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

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A class that is not a data service, annotated with {@code @CurrentTenant} and {@code @Transactional}, called
 * inside {@code Tenants.withId} in DATABASE mode while the tenant resolver resolves another tenant, as one that
 * resolves the tenant from the web request does outside a request.
 */
@Issue('https://github.com/apache/grails-core/issues/16801')
class DatabaseMultiTenantCurrentTenantTransactionalSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    void setup() {
        final dbName = "h7_ctx_${UUID.randomUUID().toString().replace('-', '')}"
        final config = [
                'grails.gorm.multiTenancy.mode'               : MultiTenancySettings.MultiTenancyMode.DATABASE,
                'grails.gorm.multiTenancy.tenantResolverClass': DefaultTenantResolver,
                'dataSource.url'                              : "jdbc:h2:mem:${dbName};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'                         : 'create-drop',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create-drop',
                'dataSources.test.url'                        : "jdbc:h2:mem:${dbName}_test;LOCK_TIMEOUT=10000".toString(),
        ]
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), TenantWidget)
    }

    void 'a @CurrentTenant @Transactional method inside Tenants.withId runs for the tenant withId binds'() {
        given:
        final service = new TenantWidgetService()

        when:
        Tenants.withId('test') {
            service.save('widget')
        }

        then:
        Tenants.withId('test') { TenantWidget.count() } == 1

        and: 'not for the tenant the resolver resolves'
        Tenants.withId(ConnectionSource.DEFAULT) { TenantWidget.count() } == 0

        and: 'the method sees the tenant withId binds'
        Tenants.withId('test') { service.currentTenantId() } == 'test'
    }

    void 'inside Tenants.withId the current tenant is the one withId binds, not the one the resolver resolves'() {
        expect:
        Tenants.withId('test') { Tenants.currentId() } == 'test'
    }
}

class DefaultTenantResolver implements TenantResolver {

    @Override
    Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        ConnectionSource.DEFAULT
    }
}

@Entity
class TenantWidget implements GormEntity<TenantWidget>, MultiTenant<TenantWidget> {

    Long id
    Long version
    String name
}

@Transactional
@CurrentTenant
class TenantWidgetService {

    void save(String name) {
        new TenantWidget(name: name).save(flush: true, failOnError: true)
    }

    Serializable currentTenantId() {
        Tenants.currentId()
    }
}
