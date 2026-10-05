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
        this.datastore = new MongoDatastore(config, Memo, NumberedMemo)
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
