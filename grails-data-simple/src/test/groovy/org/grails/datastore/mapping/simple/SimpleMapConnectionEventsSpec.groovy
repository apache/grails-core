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

import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver

/**
 * A save through the datastore of another connection, of a tenant's database or of a schema added at runtime runs
 * the domain class events and sets the timestamps, as a save through the default connection does.
 */
@RestoreSystemProperties
class SimpleMapConnectionEventsSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore

    void 'a save through another connection runs the events and sets the timestamps'() {
        given:
        datastore = new SimpleMapDatastore(['secondary'], ConnectionNote)
        ConnectionNote note = new ConnectionNote(text: 'draft')

        when:
        ConnectionNote.secondary.withTransaction {
            note.secondary.save(flush: true)
        }

        then:
        ConnectionNote.secondary.withTransaction { ConnectionNote.secondary.count() } == 1
        note.text == 'DRAFT'
        note.dateCreated != null
        note.lastUpdated != null
    }

    void 'with a discriminator column, another connection keeps the tenant handling it had and sets the timestamps'() {
        given: 'no tenant from the resolver'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        datastore = new SimpleMapDatastore(
                DatastoreUtils.createPropertyResolver([
                        (Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                        (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
                ]),
                [ConnectionSource.DEFAULT, 'secondary'],
                DiscriminatorNote
        )
        DiscriminatorNote note = new DiscriminatorNote(text: 'draft', tenantId: 't1')

        when:
        List<DiscriminatorNote> found = Tenants.withId('t1') {
            DiscriminatorNote.secondary.withTransaction {
                note.secondary.save(flush: true)
                DiscriminatorNote.secondary.list()
            }
        }

        then:
        found*.text == ['draft']
        note.dateCreated != null
    }

    void 'a save for a tenant with a database of its own runs the events and sets the timestamps'() {
        given:
        datastore = new SimpleMapDatastore(
                DatastoreUtils.createPropertyResolver([
                        (Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DATABASE,
                        (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
                ]),
                [ConnectionSource.DEFAULT, 'foo'],
                TenantEventNote
        )
        TenantEventNote note = new TenantEventNote(text: 'draft')

        when:
        Tenants.withId('foo') {
            note.save(flush: true)
        }

        then:
        Tenants.withId('foo') { TenantEventNote.count() } == 1
        note.text == 'DRAFT'
        note.dateCreated != null
        note.lastUpdated != null
    }

    void 'a save for a tenant with a schema added at runtime runs the events and sets the timestamps'() {
        given:
        datastore = new SimpleMapDatastore(
                DatastoreUtils.createPropertyResolver([
                        (Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.SCHEMA,
                        (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
                ]),
                [ConnectionSource.DEFAULT],
                TenantEventNote
        )
        datastore.addTenantForSchema('foo')
        TenantEventNote note = new TenantEventNote(text: 'draft')

        when:
        Tenants.withId('foo') {
            note.save(flush: true)
        }

        then:
        Tenants.withId('foo') { TenantEventNote.count() } == 1
        note.text == 'DRAFT'
        note.dateCreated != null
        note.lastUpdated != null
    }
}

@Entity
class DiscriminatorNote implements MultiTenant<DiscriminatorNote> {

    String text
    String tenantId
    Date dateCreated

    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class ConnectionNote {

    String text
    Date dateCreated
    Date lastUpdated

    def beforeInsert() {
        text = text.toUpperCase()
    }

    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class TenantEventNote implements MultiTenant<TenantEventNote> {

    String text
    Date dateCreated
    Date lastUpdated

    def beforeInsert() {
        text = text.toUpperCase()
    }
}
