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
package org.grails.datastore.gorm.mongo.multitenancy

import org.bson.types.ObjectId
import org.springframework.core.convert.ConversionFailedException
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver

@RestoreSystemProperties
class MongoLookupByIdMultiTenancySpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    ObjectId ownId
    ObjectId otherId

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.gorm.multiTenancy.mode'               : 'DISCRIMINATOR',
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                (MongoSettings.SETTING_URL)                   : "mongodb://${mongoHost}:${mongoPort}/lookupByIdDb" as String,
        ]
        this.datastore = new MongoDatastore(config, Memo, NumberedMemo, KeyedMemo)
    }

    void setup() {
        Memo.DB.drop()
        otherId = saveMemo('other', 'Other')
        ownId = saveMemo('own', 'Own')
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'own')
    }

    void 'get, read and exists find an instance of the current tenant only'() {
        expect:
        Memo.withNewSession { Memo.get(ownId)?.title } == 'Own'
        Memo.withNewSession { Memo.read(ownId)?.title } == 'Own'
        Memo.withNewSession { Memo.exists(ownId) }
        Memo.withNewSession { Memo.get(ownId.toHexString())?.title } == 'Own'

        and: 'an instance of another tenant is not found'
        Memo.withNewSession { Memo.get(otherId) } == null
        Memo.withNewSession { Memo.read(otherId) } == null
        !Memo.withNewSession { Memo.exists(otherId) }
        Memo.withNewSession { Memo.get(otherId.toHexString()) } == null
    }

    void 'a lookup by an id that cannot be converted to the type of the identifier behaves as a lookup by key does'() {
        given:
        Long numberedId = NumberedMemo.withTenant('own') {
            NumberedMemo.withNewSession { new NumberedMemo(title: 'Own').save(flush: true).id }
        }

        expect:
        NumberedMemo.withNewSession { NumberedMemo.get(numberedId.toString())?.title } == 'Own'

        and: 'get, read and exists treat an id that cannot be converted like an id that does not exist'
        NumberedMemo.withNewSession { NumberedMemo.get('not-a-number') } == null
        NumberedMemo.withNewSession { NumberedMemo.read('not-a-number') } == null
        !NumberedMemo.withNewSession { NumberedMemo.exists('not-a-number') }

        when: 'getAll is given an id that cannot be converted'
        NumberedMemo.withNewSession { NumberedMemo.getAll('not-a-number', numberedId) }

        then: 'it throws, as it did before lookups by id were restricted to the current tenant'
        thrown(ConversionFailedException)

        when: 'load is given an id that cannot be converted'
        NumberedMemo.withNewSession { NumberedMemo.load('not-a-number') }

        then: 'it throws as well'
        thrown(ConversionFailedException)
    }

    void 'getAll returns null in place of an instance of another tenant'() {
        when:
        List<Memo> memos = Memo.withNewSession { Memo.getAll(otherId, ownId, new ObjectId()) }

        then: 'the instances are in the order of the ids'
        memos.size() == 3
        memos[0] == null
        memos[1].title == 'Own'
        memos[2] == null
    }

    void 'load returns a proxy that does not initialize for an instance of another tenant'() {
        expect: 'a proxy for an instance of the current tenant initializes'
        Memo.withNewSession { Memo.load(ownId).title } == 'Own'

        when: 'a proxy for an instance of another tenant is used'
        Memo.withNewSession { Memo.load(otherId).title }

        then:
        thrown(DataIntegrityViolationException)
    }

    void 'a lookup by id of an entity mapped with a composite id is restricted to the current tenant'() {
        given: 'GORM for MongoDB keeps the generated id of an entity mapped with a composite id'
        KeyedMemo.DB.drop()
        Long ownKeyedId = KeyedMemo.withTenant('own') {
            KeyedMemo.withNewSession { new KeyedMemo(code: 'A', region: 'north', title: 'Own').save(flush: true).id }
        }
        Long otherKeyedId = KeyedMemo.withTenant('other') {
            KeyedMemo.withNewSession { new KeyedMemo(code: 'B', region: 'south', title: 'Other').save(flush: true).id }
        }

        expect:
        KeyedMemo.withNewSession { KeyedMemo.get(ownKeyedId)?.title } == 'Own'

        and: 'an instance of another tenant is not found'
        KeyedMemo.withNewSession { KeyedMemo.get(otherKeyedId) } == null
        KeyedMemo.withNewSession { KeyedMemo.read(otherKeyedId) } == null
        !KeyedMemo.withNewSession { KeyedMemo.exists(otherKeyedId) }
        KeyedMemo.withNewSession { KeyedMemo.getAll(otherKeyedId, ownKeyedId) }*.title == [null, 'Own']

        when: 'a proxy for an instance of another tenant is used'
        KeyedMemo.withNewSession { KeyedMemo.load(otherKeyedId).title }

        then:
        thrown(DataIntegrityViolationException)
    }

    void 'a lookup by id inside a transaction finds an instance saved earlier in that transaction'() {
        when: 'instances are saved inside a transaction, which GORM for MongoDB does not flush before a query'
        Map<String, Object> lookups = Memo.withNewSession {
            Memo.withTransaction {
                Memo saved = new Memo(title: 'New').save()
                Memo unvalidated = new Memo(title: 'Unvalidated').save(validate: false)
                List<Memo> all = Memo.getAll(saved.id, unvalidated.id)
                [unvalidatedTenantId: unvalidated.tenantId,
                 get                : Memo.get(saved.id).is(saved),
                 exists             : Memo.exists(saved.id),
                 getAll             : all[0].is(saved) && all[1].is(unvalidated),
                 load               : Memo.load(saved.id).is(saved),
                 getUnvalidated     : Memo.get(unvalidated.id).is(unvalidated)]
            }
        }

        then: 'the session returns them, including one that gets its tenant id only when it is inserted'
        lookups == [unvalidatedTenantId: null, get: true, exists: true, getAll: true, load: true, getUnvalidated: true]

        and: 'the transaction inserts both for the current tenant'
        Memo.withNewSession { Memo.countByTitleInList(['New', 'Unvalidated']) } == 2
        Memo.withTenant('other') { Memo.withNewSession { Memo.countByTitleInList(['New', 'Unvalidated']) } } == 0
    }

    void 'a lookup by id does not flush the session'() {
        when: 'an instance is changed and the session is used for lookups by id before it is flushed'
        String stored = Memo.withNewSession {
            Memo own = Memo.get(ownId)
            own.title = 'Changed'
            own.save()
            Memo.get(otherId)
            Memo.exists(new ObjectId())
            Memo.getAll(otherId, new ObjectId())
            Memo.withNewSession { Memo.get(ownId).title }
        }

        then: 'the change has not been written'
        stored == 'Own'
    }

    void 'an instance of another tenant that the session already holds is not returned'() {
        when: 'the session holds an instance of another tenant'
        Map<String, Object> lookups = Memo.withNewSession {
            Memo other = Memo.withTenant('other') { Memo.get(otherId) }
            [held  : other?.title,
             get   : Memo.get(otherId),
             read  : Memo.read(otherId),
             exists: Memo.exists(otherId),
             getAll: Memo.getAll(otherId, ownId)*.title]
        }

        then: 'a lookup by id for the current tenant does not find it'
        lookups == [held: 'Other', get: null, read: null, exists: false, getAll: [null, 'Own']]

        when: 'a proxy for it is used'
        Memo.withNewSession {
            Memo.withTenant('other') { Memo.get(otherId) }
            Memo.load(otherId).title
        }

        then:
        thrown(DataIntegrityViolationException)
    }

    void 'load returns the instance the session already holds for the current tenant'() {
        expect:
        Memo.withNewSession {
            Memo own = Memo.get(ownId)
            Memo.load(ownId).is(own)
        }
    }

    void 'a tenant resolver that resolves the default connection source does not lift the restriction'() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, ConnectionSource.DEFAULT)

        expect: 'a lookup by id agrees with a query'
        Memo.withNewSession { Memo.findById(ownId) } == null
        Memo.withNewSession { Memo.get(ownId) } == null
        Memo.withNewSession { Memo.read(otherId) } == null
        !Memo.withNewSession { Memo.exists(ownId) }
        Memo.withNewSession { Memo.getAll(ownId, otherId) } == [null, null]

        when:
        Memo.withNewSession { Memo.load(ownId).title }

        then:
        thrown(DataIntegrityViolationException)
    }

    void 'a lookup by id inside withTenant is restricted to that tenant'() {
        expect:
        Memo.withTenant('other') { Memo.withNewSession { Memo.get(otherId)?.title } } == 'Other'
        Memo.withTenant('other') { Memo.withNewSession { Memo.get(ownId) } } == null
    }

    void 'a lookup by id inside withoutId is not restricted to a tenant'() {
        expect:
        Tenants.withoutId(datastore) {
            Memo.withNewSession { [Memo.get(ownId)?.title, Memo.get(otherId)?.title] }
        } == ['Own', 'Other']
    }

    void 'a proxy created under a tenant is not restricted to a tenant when it is initialized inside withoutId'() {
        expect:
        Memo.withNewSession {
            Memo own = Memo.load(ownId)
            Memo other = Memo.load(otherId)
            Tenants.withoutId(datastore) { [own.title, other.title] }
        } == ['Own', 'Other']
    }

    void 'a lookup by id without a current tenant throws TenantNotFoundException'() {
        given:
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)

        when:
        Memo.withNewSession { Memo.get(ownId) }

        then:
        thrown(TenantNotFoundException)
    }

    void 'getAll without ids needs no current tenant'() {
        given:
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY_NAME)

        expect:
        Memo.withNewSession { Memo.getAll([]) } == []
    }

    private static ObjectId saveMemo(String tenantId, String title) {
        Memo.withTenant(tenantId) {
            Memo.withNewSession { new Memo(title: title).save(flush: true).id }
        }
    }
}

@Entity
class Memo implements MultiTenant<Memo>, MongoEntity<Memo> {
    ObjectId id
    String tenantId
    String title
}

@Entity
class NumberedMemo implements MultiTenant<NumberedMemo>, MongoEntity<NumberedMemo> {
    Long id
    String tenantId
    String title
}

@Entity
class KeyedMemo implements MultiTenant<KeyedMemo>, MongoEntity<KeyedMemo> {
    String code
    String region
    String tenantId
    String title

    static mapping = {
        id composite: ['code', 'region']
    }
}
