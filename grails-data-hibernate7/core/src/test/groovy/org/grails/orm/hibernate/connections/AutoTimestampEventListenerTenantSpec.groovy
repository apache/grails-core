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
package org.grails.orm.hibernate.connections

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A tenant with a database or a schema of its own has an auto-timestamp listener of its own, and a multi-tenant
 * domain class answers with the current tenant's.
 */
@Issue('https://github.com/apache/grails-core/issues/14509')
@RestoreSystemProperties
class AutoTimestampEventListenerTenantSpec extends Specification {

    private static final Date PAST = new Date(110_000_000_000L)

    @AutoCleanup
    HibernateDatastore datastore

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
    }

    void 'a tenant with a database of its own answers with the listener of that database'() {
        given:
        datastore = databasePerTenant()
        AutoTimestampEventListener books = listenerOf('books')
        AutoTimestampEventListener moreBooks = listenerOf('moreBooks')

        expect:
        !books.is(moreBooks)
        !books.is(datastore.autoTimestampEventListener)
        Tenants.withId('books') { TenantTimestampNote.autoTimestampEventListener }.is(books)
        Tenants.withId('moreBooks') { TenantTimestampNote.autoTimestampEventListener }.is(moreBooks)
        TenantTimestampNote.withTenant('moreBooks').autoTimestampEventListener.is(moreBooks)

        when: 'the current tenant comes from the resolver'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'moreBooks')

        then:
        TenantTimestampNote.autoTimestampEventListener.is(moreBooks)
    }

    void 'the listener of the current tenant turns off the timestamps of its saves, and another tenant\'s does not'() {
        given:
        datastore = databasePerTenant()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'books')
        TenantTimestampNote kept = new TenantTimestampNote(text: 'kept', dateCreated: PAST, lastUpdated: PAST)
        TenantTimestampNote stamped = new TenantTimestampNote(text: 'stamped', dateCreated: PAST, lastUpdated: PAST)

        when:
        TenantTimestampNote.withTransaction {
            TenantTimestampNote.autoTimestampEventListener.withoutTimestamps {
                kept.save(flush: true)
            }
            listenerOf('moreBooks').withoutTimestamps {
                stamped.save(flush: true)
            }
        }

        then:
        TenantTimestampNote.withNewTransaction { TenantTimestampNote.get(kept.id).dateCreated } == PAST
        TenantTimestampNote.withNewTransaction { TenantTimestampNote.get(kept.id).lastUpdated } == PAST
        TenantTimestampNote.withNewTransaction { TenantTimestampNote.get(stamped.id).dateCreated } != PAST
        TenantTimestampNote.withNewTransaction { TenantTimestampNote.get(stamped.id).lastUpdated } != PAST
    }

    void 'a class that is not multi-tenant answers inside a tenant block with the listener of the default database'() {
        given: 'Hibernate saves a class that is not multi-tenant to the default database inside a tenant block too'
        datastore = databasePerTenant()
        PlainTimestampNote kept = new PlainTimestampNote(text: 'kept', dateCreated: PAST, lastUpdated: PAST)

        when:
        def listener = Tenants.withId('books') {
            PlainTimestampNote.withTransaction {
                PlainTimestampNote.autoTimestampEventListener.withoutTimestamps {
                    kept.save(flush: true)
                }
            }
            PlainTimestampNote.autoTimestampEventListener
        }

        then:
        listener.is(datastore.autoTimestampEventListener)
        PlainTimestampNote.withNewTransaction { PlainTimestampNote.get(kept.id).dateCreated } == PAST
        PlainTimestampNote.withNewTransaction { PlainTimestampNote.get(kept.id).lastUpdated } == PAST
    }

    void 'a tenant with a schema of its own answers with the listener of that schema'() {
        given:
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : 'SCHEMA',
                'grails.gorm.multiTenancy.tenantResolverClass': TwoSchemaResolver,
                'dataSource.url'                              : 'jdbc:h2:mem:timestampSchemaDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                         : 'create-drop',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create'
        ]), TenantTimestampNote)
        AutoTimestampEventListener books = listenerOf('books')
        AutoTimestampEventListener moreBooks = listenerOf('moreBooks')
        TenantTimestampNote kept = new TenantTimestampNote(text: 'kept', dateCreated: PAST, lastUpdated: PAST)

        when:
        Tenants.withId('books') {
            TenantTimestampNote.withTransaction {
                TenantTimestampNote.autoTimestampEventListener.withoutTimestamps {
                    kept.save(flush: true)
                }
            }
        }

        then:
        !books.is(moreBooks)
        Tenants.withId('books') { TenantTimestampNote.autoTimestampEventListener }.is(books)
        TenantTimestampNote.withTenant('moreBooks').autoTimestampEventListener.is(moreBooks)
        Tenants.withId('books') { TenantTimestampNote.withNewTransaction { TenantTimestampNote.get(kept.id).dateCreated } } == PAST
    }

    private HibernateDatastore databasePerTenant() {
        new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : 'DATABASE',
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                'dataSource.url'                              : 'jdbc:h2:mem:timestampDefaultDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                         : 'create-drop',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create',
                'dataSources.books'                           : [url: 'jdbc:h2:mem:timestampBooksDB;LOCK_TIMEOUT=10000'],
                'dataSources.moreBooks'                       : [url: 'jdbc:h2:mem:timestampMoreBooksDB;LOCK_TIMEOUT=10000']
        ]), TenantTimestampNote, PlainTimestampNote)
    }

    private AutoTimestampEventListener listenerOf(String connectionName) {
        datastore.getDatastoreForConnection(connectionName).autoTimestampEventListener
    }

    static class TwoSchemaResolver extends SystemPropertyTenantResolver implements AllTenantsResolver {

        @Override
        Iterable<Serializable> resolveTenantIds() {
            ['books', 'moreBooks']
        }
    }
}

@Entity
class TenantTimestampNote implements MultiTenant<TenantTimestampNote> {

    String text
    Date dateCreated
    Date lastUpdated
}

@Entity
class PlainTimestampNote {

    String text
    Date dateCreated
    Date lastUpdated
}
