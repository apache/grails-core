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
package org.grails.datastore.mapping.simple

import groovy.transform.CompileStatic

import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver

/**
 * The listener a domain class answers with is the one of the datastore its saves go through. The in-memory
 * datastore keeps the unqualified operations of every domain class on the datastore of the default connection, and
 * an explicit connection, or a tenant's database or schema, on its own.
 */
@RestoreSystemProperties
class SimpleMapAutoTimestampListenerSpec extends Specification {

    private static final Date PAST = new Date(110_000_000_000L)

    @AutoCleanup
    SimpleMapDatastore datastore

    void 'a domain class answers with the listener of the datastore that saves it'() {
        given:
        datastore = new SimpleMapDatastore(['secondary'], ListenerNote, SecondaryListenerNote)
        SimpleMapDatastore secondary = (SimpleMapDatastore) datastore.getDatastoreForConnection('secondary')

        expect: 'unqualified operations stay on the default connection, whatever the class is mapped to'
        ListenerNote.autoTimestampEventListener.is(datastore.autoTimestampEventListener)
        SecondaryListenerNote.autoTimestampEventListener.is(datastore.autoTimestampEventListener)

        and: 'an explicit connection has a listener of its own'
        ListenerNote.secondary.autoTimestampEventListener.is(secondary.autoTimestampEventListener)
        SecondaryListenerNote.secondary.autoTimestampEventListener.is(secondary.autoTimestampEventListener)
        !secondary.autoTimestampEventListener.is(datastore.autoTimestampEventListener)
    }

    void 'the first auto-timestamp listener of the mapping context is still the default connection\'s'() {
        given: 'code that looked the listener up through the mapping context, before domain classes answered with it'
        datastore = new SimpleMapDatastore(['secondary', 'tertiary'], ListenerNote)

        expect:
        datastore.mappingContext.eventListeners.find { it instanceof AutoTimestampEventListener }.is(datastore.autoTimestampEventListener)
    }

    void 'a transaction for a connection routes the unqualified lookup to that connection'() {
        given:
        datastore = new SimpleMapDatastore(['secondary'], ListenerNote)
        SimpleMapDatastore secondary = (SimpleMapDatastore) datastore.getDatastoreForConnection('secondary')

        expect:
        ListenerNote.secondary.withTransaction { ListenerNote.autoTimestampEventListener }.is(secondary.autoTimestampEventListener)
        new SecondaryListenerLookup().lookUp().is(secondary.autoTimestampEventListener)
        ListenerNote.autoTimestampEventListener.is(datastore.autoTimestampEventListener)
    }

    void 'only the listener of the connection that saves an instance turns its timestamps off'() {
        given:
        datastore = new SimpleMapDatastore(['secondary'], ListenerNote)
        def defaultListener = ListenerNote.autoTimestampEventListener
        def secondaryListener = ListenerNote.secondary.autoTimestampEventListener
        ListenerNote kept = new ListenerNote(text: 'kept', dateCreated: PAST, lastUpdated: PAST)
        ListenerNote overwritten = new ListenerNote(text: 'overwritten', dateCreated: PAST, lastUpdated: PAST)

        when:
        ListenerNote.secondary.withTransaction {
            secondaryListener.withoutTimestamps {
                kept.secondary.save(flush: true)
            }
            defaultListener.withoutTimestamps {
                overwritten.secondary.save(flush: true)
            }
        }

        then:
        kept.dateCreated == PAST
        kept.lastUpdated == PAST
        overwritten.dateCreated != PAST
        overwritten.lastUpdated != PAST
    }

    void 'every connection follows the auto-timestamp settings of the configuration'() {
        given:
        datastore = new SimpleMapDatastore(
                DatastoreUtils.createPropertyResolver([
                        (Settings.SETTING_AUTO_TIMESTAMP_INSERT_OVERWRITE) : false,
                        (Settings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS): false
                ]),
                ['secondary'],
                ListenerNote
        )
        ListenerNote onDefault = new ListenerNote(text: 'default', dateCreated: PAST)
        ListenerNote onSecondary = new ListenerNote(text: 'secondary', dateCreated: PAST)

        when:
        ListenerNote.withTransaction {
            onDefault.save(flush: true)
        }
        ListenerNote.secondary.withTransaction {
            onSecondary.secondary.save(flush: true)
        }

        then:
        onDefault.dateCreated == PAST
        onSecondary.dateCreated == PAST
        onSecondary.lastUpdated != null
        !ListenerNote.autoTimestampEventListener.cacheAutoTimestampAnnotations
        !ListenerNote.secondary.autoTimestampEventListener.cacheAutoTimestampAnnotations
    }

    void 'a tenant with a database of its own answers with the listener of that database'() {
        given:
        datastore = new SimpleMapDatastore(
                DatastoreUtils.createPropertyResolver([
                        (Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DATABASE,
                        (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
                ]),
                [ConnectionSource.DEFAULT, 'foo', 'bar'],
                TenantListenerNote
        )
        def foo = datastore.getDatastoreForConnection('foo').autoTimestampEventListener
        def bar = datastore.getDatastoreForConnection('bar').autoTimestampEventListener

        expect:
        !foo.is(bar)
        Tenants.withId('foo') { TenantListenerNote.autoTimestampEventListener }.is(foo)
        Tenants.withId('bar') { TenantListenerNote.autoTimestampEventListener }.is(bar)
        TenantListenerNote.withTenant('bar').autoTimestampEventListener.is(bar)

        and: 'statically compiled code reaches it through the operations withTenant returns'
        StaticTenantListenerLookup.forTenant('foo').is(foo)
        StaticTenantListenerLookup.forTenant('bar').is(bar)

        when: 'the current tenant comes from the resolver'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'foo')

        then:
        TenantListenerNote.autoTimestampEventListener.is(foo)
    }

    void 'a class that is not multi-tenant answers inside a tenant block with the listener of the datastore that saves it'() {
        given:
        datastore = new SimpleMapDatastore(
                DatastoreUtils.createPropertyResolver([
                        (Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DATABASE,
                        (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
                ]),
                [ConnectionSource.DEFAULT, 'foo'],
                TenantListenerNote, ListenerNote
        )
        ListenerNote note = new ListenerNote(text: 'kept', dateCreated: PAST, lastUpdated: PAST)

        when:
        def listener = Tenants.withId('foo') {
            ListenerNote.autoTimestampEventListener.withoutTimestamps {
                note.save(flush: true)
            }
            ListenerNote.autoTimestampEventListener
        }

        then: 'the timestamps were turned off for the save'
        note.dateCreated == PAST
        note.lastUpdated == PAST

        and: 'by the listener of the datastore that holds the saved instance'
        SimpleMapDatastore foo = (SimpleMapDatastore) datastore.getDatastoreForConnection('foo')
        List<SimpleMapDatastore> holding = [datastore, foo].findAll { SimpleMapDatastore ds ->
            DatastoreUtils.execute(ds, { Session session -> session.retrieve(ListenerNote, note.id) } as SessionCallback) != null
        }
        holding.size() == 1
        listener.is(holding.first().autoTimestampEventListener)
    }

    void 'a tenant with a schema added at runtime answers with the listener of that schema'() {
        given:
        datastore = new SimpleMapDatastore(
                DatastoreUtils.createPropertyResolver([
                        (Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.SCHEMA,
                        (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
                ]),
                [ConnectionSource.DEFAULT],
                TenantListenerNote
        )
        datastore.addTenantForSchema('foo')
        TenantListenerNote note = new TenantListenerNote(text: 'kept', dateCreated: PAST, lastUpdated: PAST)

        when:
        Tenants.withId('foo') {
            TenantListenerNote.autoTimestampEventListener.withoutTimestamps {
                note.save(flush: true)
            }
        }

        then:
        Tenants.withId('foo') { TenantListenerNote.autoTimestampEventListener }.is(datastore.getDatastoreForConnection('foo').autoTimestampEventListener)
        TenantListenerNote.withTenant('foo').autoTimestampEventListener.is(datastore.getDatastoreForConnection('foo').autoTimestampEventListener)
        note.dateCreated == PAST
        note.lastUpdated == PAST
    }
}

@Entity
class ListenerNote {

    String text
    Date dateCreated
    Date lastUpdated

    static mapping = {
        datasource 'ALL'
    }
}

@CompileStatic
class StaticTenantListenerLookup {

    static AutoTimestampEventListener forTenant(String tenantId) {
        TenantListenerNote.withTenant(tenantId).autoTimestampEventListener
    }
}

class SecondaryListenerLookup {

    @Transactional(connection = 'secondary')
    AutoTimestampEventListener lookUp() {
        ListenerNote.autoTimestampEventListener
    }
}

@Entity
class SecondaryListenerNote {

    String text
    Date dateCreated
    Date lastUpdated

    static mapping = {
        datasource 'secondary'
    }
}

@Entity
class TenantListenerNote implements MultiTenant<TenantListenerNote> {

    String text
    Date dateCreated
    Date lastUpdated
}
