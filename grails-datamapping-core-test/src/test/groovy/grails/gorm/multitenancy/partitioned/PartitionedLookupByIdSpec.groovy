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

import org.springframework.core.convert.ConversionFailedException
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
import org.grails.datastore.mapping.core.connections.ConnectionSource
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

    void 'a lookup by an id that cannot be converted to the type of the identifier behaves as a lookup by key does'() {
        expect: 'get, read and exists treat an id that cannot be converted like an id that does not exist'
        Note.withNewSession { Note.get('not-a-number') } == null
        Note.withNewSession { Note.read('not-a-number') } == null
        !Note.withNewSession { Note.exists('not-a-number') }

        when: 'getAll is given an id that cannot be converted'
        Note.withNewSession { Note.getAll('not-a-number', ownId) }

        then: 'it throws, as it did before lookups by id were restricted to the current tenant'
        thrown(ConversionFailedException)

        when: 'load is given an id that cannot be converted'
        Note.withNewSession { Note.load('not-a-number') }

        then: 'it throws as well'
        thrown(ConversionFailedException)
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

    void 'a lookup by id of an entity mapped with a composite id is restricted to the current tenant'() {
        given: 'the in-memory datastore keeps the generated id of an entity mapped with a composite id'
        Long ownKeyedId = KeyedNote.withTenant('own') {
            KeyedNote.withNewSession { new KeyedNote(code: 'A', region: 'north', title: 'Own').save(flush: true).id }
        }
        Long otherKeyedId = KeyedNote.withTenant('other') {
            KeyedNote.withNewSession { new KeyedNote(code: 'B', region: 'south', title: 'Other').save(flush: true).id }
        }

        expect:
        KeyedNote.withNewSession { KeyedNote.get(ownKeyedId)?.title } == 'Own'

        and: 'an instance of another tenant is not found'
        KeyedNote.withNewSession { KeyedNote.get(otherKeyedId) } == null
        KeyedNote.withNewSession { KeyedNote.read(otherKeyedId) } == null
        !KeyedNote.withNewSession { KeyedNote.exists(otherKeyedId) }
        KeyedNote.withNewSession { KeyedNote.getAll(otherKeyedId, ownKeyedId) }*.title == [null, 'Own']

        when: 'a proxy for an instance of another tenant is used'
        KeyedNote.withNewSession { KeyedNote.load(otherKeyedId).title }

        then:
        thrown(DataIntegrityViolationException)
    }

    void 'a lookup by id inside a transaction finds an instance saved earlier in that transaction'() {
        when: 'instances are saved inside a transaction and looked up by id before it commits'
        Map<String, Object> lookups = Note.withNewSession {
            Note.withTransaction {
                Note saved = new Note(title: 'New').save()
                Note unvalidated = new Note(title: 'Unvalidated').save(validate: false)
                List<Note> all = Note.getAll(saved.id, unvalidated.id)
                [unvalidatedTenantId: unvalidated.tenantId,
                 get                : Note.get(saved.id).is(saved),
                 exists             : Note.exists(saved.id),
                 getAll             : all[0].is(saved) && all[1].is(unvalidated),
                 load               : Note.load(saved.id).is(saved),
                 getUnvalidated     : Note.get(unvalidated.id).is(unvalidated)]
            }
        }

        then: 'the session returns them, including one that gets its tenant id only when it is inserted'
        lookups == [unvalidatedTenantId: null, get: true, exists: true, getAll: true, load: true, getUnvalidated: true]

        and: 'the transaction inserts both for the current tenant'
        Note.withNewSession { Note.countByTitleInList(['New', 'Unvalidated']) } == 2
        Note.withTenant('other') { Note.withNewSession { Note.countByTitleInList(['New', 'Unvalidated']) } } == 0
    }

    void 'a lookup by id does not flush the session'() {
        when: 'an instance is changed and the session is used for lookups by id before it is flushed'
        String stored = Note.withNewSession {
            Note own = Note.get(ownId)
            own.title = 'Changed'
            own.save()
            Note.get(otherId)
            Note.exists(999L)
            Note.getAll(otherId, 999L)
            Note.withNewSession { Note.get(ownId).title }
        }

        then: 'the change has not been written'
        stored == 'Own'
    }

    void 'an instance of another tenant that the session already holds is not returned'() {
        when: 'the session holds an instance of another tenant'
        Map<String, Object> lookups = Note.withNewSession {
            Note other = Note.withTenant('other') { Note.get(otherId) }
            [held  : other?.title,
             get   : Note.get(otherId),
             read  : Note.read(otherId),
             exists: Note.exists(otherId),
             getAll: Note.getAll(otherId, ownId)*.title]
        }

        then: 'a lookup by id for the current tenant does not find it'
        lookups == [held: 'Other', get: null, read: null, exists: false, getAll: [null, 'Own']]

        when: 'a proxy for it is used'
        Note.withNewSession {
            Note.withTenant('other') { Note.get(otherId) }
            Note.load(otherId).title
        }

        then:
        thrown(DataIntegrityViolationException)
    }

    void 'load returns the instance the session already holds for the current tenant'() {
        expect: 'an instance saved in the session'
        Note.withNewSession {
            Note saved = new Note(title: 'Saved').save(flush: true)
            Note.load(saved.id).is(saved)
        }
    }

    void 'a tenant resolver that resolves the default connection source does not lift the restriction'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, ConnectionSource.DEFAULT)

        expect: 'a lookup by id agrees with a query'
        Note.withNewSession { Note.findById(ownId) } == null
        Note.withNewSession { Note.get(ownId) } == null
        Note.withNewSession { Note.read(otherId) } == null
        !Note.withNewSession { Note.exists(ownId) }
        Note.withNewSession { Note.getAll(ownId, otherId) } == [null, null]

        when:
        Note.withNewSession { Note.load(ownId).title }

        then:
        thrown(DataIntegrityViolationException)
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

    void 'Tenants.isWithoutId is true inside withoutId only'() {
        expect:
        !Tenants.isWithoutId()
        Tenants.withoutId(datastore) { Tenants.isWithoutId() }
        !Tenants.withoutId(datastore) { Note.withTenant('own') { Tenants.isWithoutId() } }
        !Note.withTenant('own') { Tenants.isWithoutId() }

        when: 'the tenant resolver resolves the default connection source'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, ConnectionSource.DEFAULT)

        then:
        Tenants.currentId(datastore) == ConnectionSource.DEFAULT
        !Tenants.isWithoutId()
    }

    void 'a proxy created under a tenant is not restricted to a tenant when it is initialized inside withoutId'() {
        expect:
        Note.withNewSession {
            Note own = Note.load(ownId)
            Note other = Note.load(otherId)
            Tenants.withoutId(datastore) { [own.title, other.title] }
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

    void 'getAll without ids needs no current tenant'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')

        expect:
        Note.withNewSession { Note.getAll([]) } == []
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
class KeyedNote implements MultiTenant<KeyedNote> {
    String code
    String region
    String title
    String tenantId

    static mapping = {
        id composite: ['code', 'region']
    }
}

@Entity
class Label {
    String name
}
