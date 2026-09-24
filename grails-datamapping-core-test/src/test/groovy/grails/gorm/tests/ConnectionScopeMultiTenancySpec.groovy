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
package grails.gorm.tests

import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore

/**
 * A connection scope routes the unqualified operations on a multi-tenant entity exactly as naming the connection
 * does. In DATABASE mode the tenants are connections, so the two interact.
 */
@RestoreSystemProperties
class ConnectionScopeMultiTenancySpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            DatastoreUtils.createPropertyResolver([(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DATABASE,
                                                   (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
                                                   (Settings.SETTING_DB_CREATE)            : 'create-drop']),
            [ConnectionSource.DEFAULT, 'foo', 'bar'],
            ScopedTenantBook
    )

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'foo')
        new ScopedTenantBook(title: 'The Stand').save(flush: true)
        new ScopedTenantBook(title: 'The Shining').save(flush: true)
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'bar')
        new ScopedTenantBook(title: 'It').save(flush: true)
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'foo')
    }

    void 'test a scope for the default connection resolves the current tenant, as naming the default connection does'() {
        expect: 'naming the default connection reaches the current tenant, foo'
        ScopedTenantBook.'default'.count() == 2

        and: 'so does a block for it inside a block for another connection, rather than the default connection itself'
        ScopedTenantBook.bar.withTransaction {
            ScopedTenantBook.'default'.withTransaction { ScopedTenantBook.count() }
        } == 2

        and: 'the same through the registry, where the scope is pushed inside an enclosing one'
        GormRegistry.withConnectionScope(ScopedTenantBook, 'bar') {
            GormRegistry.withConnectionScope(ScopedTenantBook, ConnectionSource.DEFAULT) { ScopedTenantBook.count() }
        } == 2
    }

    void 'test a new session for a tenant runs the calls on the class for that tenant'() {
        expect: 'the tenant named here, not the one the resolver reports'
        GormRegistry.findStaticApi(ScopedTenantBook).withNewSession('bar') { ScopedTenantBook.count() } == 1
        ScopedTenantBook.count() == 2
    }

    void 'test a transaction opened for a connection leaves a multi-tenant class to its tenant'() {
        expect: 'the current tenant, foo, not the connection the transaction was opened for'
        new TenantBookService().countInTransactionForBar() == 2

        and: 'naming the connection on the class still routes it'
        ScopedTenantBook.bar.count() == 1
    }

    void 'test a scope takes precedence over a tenant switched to inside it, as a named connection does'() {
        expect:
        GormRegistry.withConnectionScope(ScopedTenantBook, 'bar') {
            Tenants.withId(datastore, 'foo') { ScopedTenantBook.count() }
        } == 1

        and:
        Tenants.withId(datastore, 'foo') { ScopedTenantBook.bar.count() } == 1
    }
}

@Entity
class ScopedTenantBook implements MultiTenant<ScopedTenantBook> {
    String title
}

class TenantBookService {

    @Transactional('bar')
    Number countInTransactionForBar() {
        ScopedTenantBook.count()
    }
}
