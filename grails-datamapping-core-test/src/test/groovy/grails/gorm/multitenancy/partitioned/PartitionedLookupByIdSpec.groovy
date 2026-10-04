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
package grails.gorm.multitenancy.partitioned

import org.springframework.dao.DataIntegrityViolationException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import org.grails.datastore.gorm.proxy.GroovyProxyFactory
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.datastore.mapping.simple.SimpleMapDatastore

@RestoreSystemProperties
class PartitionedLookupByIdSpec extends Specification {

    @Shared
    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()],
            getClass().getPackage()
    )

    Long ownId
    Long otherId

    void setup() {
        datastore.clearData()
        otherId = saveNote('other', 'Other')
        ownId = saveNote('own', 'Own')
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'own')
    }

    void 'get, read and exists find an instance of the current tenant only'() {
        expect:
        Note.withNewSession { Note.get(ownId)?.title } == 'Own'
        Note.withNewSession { Note.read(ownId)?.title } == 'Own'
        Note.withNewSession { Note.exists(ownId) }

        and: 'an instance of another tenant is not found'
        Note.withNewSession { Note.get(otherId) } == null
        Note.withNewSession { Note.read(otherId) } == null
        !Note.withNewSession { Note.exists(otherId) }
    }

    void 'get converts the id to the type of the identifier'() {
        expect:
        Note.withNewSession { Note.get(ownId.toString())?.title } == 'Own'
        Note.withNewSession { Note.get(otherId.toString()) } == null
        Note.withNewSession { Note.get(null) } == null
    }

    void 'getAll returns null in place of an instance of another tenant'() {
        when:
        List<Note> notes = Note.withNewSession { Note.getAll(otherId, ownId, 999L) }

        then: 'the instances are in the order of the ids'
        notes.size() == 3
        notes[0] == null
        notes[1].title == 'Own'
        notes[2] == null

        and: 'the same holds for a list of ids'
        Note.withNewSession { Note.getAll([ownId, otherId]) }*.title == ['Own', null]
    }

    void 'load returns a proxy that does not initialize for an instance of another tenant'() {
        expect: 'a proxy for an instance of the current tenant initializes'
        Note.withNewSession { Note.load(ownId).title } == 'Own'
        Note.withNewSession { Note.proxy(ownId).title } == 'Own'

        when: 'a proxy for an instance of another tenant is used'
        Long proxiedId = null
        Note.withNewSession {
            Note other = Note.load(otherId)
            proxiedId = other.id
            other.title
        }

        then: 'it has the id, but it fails to initialize like one for an instance that does not exist'
        proxiedId == otherId
        thrown(DataIntegrityViolationException)
    }

    void 'load returns the instance itself with a proxy factory that cannot initialize a proxy through a query'() {
        given:
        ProxyFactory proxyFactory = datastore.mappingContext.proxyFactory
        datastore.mappingContext.proxyFactory = new GroovyProxyFactory()

        expect:
        Note.withNewSession { Note.load(ownId)?.title } == 'Own'
        Note.withNewSession { Note.load(otherId) } == null

        cleanup:
        datastore.mappingContext.proxyFactory = proxyFactory
    }

    void 'a lookup by id inside withTenant is restricted to that tenant'() {
        expect:
        Note.withTenant('other') { Note.withNewSession { Note.get(otherId)?.title } } == 'Other'
        Note.withTenant('other') { Note.withNewSession { Note.get(ownId) } } == null
        Note.withTenant('other').get(ownId) == null
    }

    void 'a lookup by id inside withoutId is not restricted to a tenant'() {
        expect:
        Tenants.withoutId(datastore) {
            Note.withNewSession { [Note.get(ownId)?.title, Note.get(otherId)?.title] }
        } == ['Own', 'Other']
    }

    void 'a lookup by id without a current tenant throws TenantNotFoundException'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')

        when:
        Note.withNewSession { Note.get(ownId) }

        then:
        thrown(TenantNotFoundException)
    }

    void 'a lookup by id of an entity that is not multi-tenant needs no tenant'() {
        given:
        Long id = Label.withNewSession { new Label(name: 'Shared').save(flush: true).id }
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')

        expect:
        Label.withNewSession { Label.get(id)?.name } == 'Shared'
        Label.withNewSession { Label.getAll(id)*.name } == ['Shared']
        Label.withNewSession { Label.load(id).name } == 'Shared'
    }

    private static Long saveNote(String tenantId, String title) {
        Note.withTenant(tenantId) {
            Note.withNewSession { new Note(title: title).save(flush: true).id }
        }
    }
}

@Entity
class Note implements MultiTenant<Note> {
    String title
    String tenantId
}

@Entity
class Label {
    String name
}
