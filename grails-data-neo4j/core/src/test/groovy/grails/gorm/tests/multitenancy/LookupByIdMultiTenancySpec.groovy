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
package grails.gorm.tests.multitenancy

import org.springframework.core.convert.ConversionFailedException
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.neo4j.Neo4jEntity
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver

@RestoreSystemProperties
class LookupByIdMultiTenancySpec extends Specification {

    @Shared
    @AutoCleanup
    Neo4jDatastore datastore

    Long ownId
    Long otherId

    void setupSpec() {
        File tempDir = File.createTempDir()
        tempDir.deleteOnExit()
        // No explicit URL: the embedded server then binds a random free port, so this spec cannot
        // collide with the embedded server of another spec running in a parallel test fork.
        Map config = [
                'grails.gorm.multiTenancy.mode'               : 'DISCRIMINATOR',
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                'grails.neo4j.options.encryptionLevel'        : 'NONE',
                (Settings.SETTING_NEO4J_TYPE)                 : 'embedded',
                (Settings.SETTING_NEO4J_LOCATION)             : tempDir
        ]
        datastore = new Neo4jDatastore(config, Memo)
    }

    void setup() {
        datastore.boltDriver.session().withCloseable { session ->
            session.beginTransaction().withCloseable { tx ->
                tx.run('MATCH (n:Memo) DETACH DELETE n')
                tx.commit()
            }
        }
        otherId = saveMemo('other', 'Other')
        ownId = saveMemo('own', 'Own')
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'own')
    }

    void 'get, read and exists find an instance of the current tenant only'() {
        expect:
        Memo.withNewSession { Memo.get(ownId)?.title } == 'Own'
        Memo.withNewSession { Memo.read(ownId)?.title } == 'Own'
        Memo.withNewSession { Memo.exists(ownId) }
        Memo.withNewSession { Memo.get(ownId.toString())?.title } == 'Own'

        and: 'an instance of another tenant is not found'
        Memo.withNewSession { Memo.get(otherId) } == null
        Memo.withNewSession { Memo.read(otherId) } == null
        !Memo.withNewSession { Memo.exists(otherId) }
        Memo.withNewSession { Memo.get(otherId.toString()) } == null
    }

    void 'a lookup by an id that cannot be converted to the type of the identifier behaves as a lookup by key does'() {
        expect: 'get, read and exists treat an id that cannot be converted like an id that does not exist'
        Memo.withNewSession { Memo.get('not-a-number') } == null
        Memo.withNewSession { Memo.read('not-a-number') } == null
        !Memo.withNewSession { Memo.exists('not-a-number') }

        when: 'getAll is given an id that cannot be converted'
        Memo.withNewSession { Memo.getAll('not-a-number', ownId) }

        then:
        thrown(ConversionFailedException)

        when: 'load is given an id that cannot be converted'
        Memo.withNewSession { Memo.load('not-a-number') }

        then:
        thrown(ConversionFailedException)
    }

    void 'getAll returns null in place of an instance of another tenant'() {
        when:
        List<Memo> memos = Memo.withNewSession { Memo.getAll(otherId, ownId, Long.MAX_VALUE) }

        then: 'the instances are in the order of the ids'
        memos.size() == 3
        memos[0] == null
        memos[1].title == 'Own'
        memos[2] == null

        and: 'the same holds for a list of ids'
        Memo.withNewSession { Memo.getAll([ownId, otherId]) }*.title == ['Own', null]
    }

    void 'load returns a proxy that does not initialize for an instance of another tenant'() {
        expect: 'a proxy for an instance of the current tenant initializes'
        Memo.withNewSession { Memo.load(ownId).title } == 'Own'

        when: 'a proxy for an instance of another tenant is used'
        Memo.withNewSession { Memo.load(otherId).title }

        then:
        thrown(DataIntegrityViolationException)
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

        and: 'the tenant id default restricts it as it restricts a query'
        Memo.withTenant(ConnectionSource.DEFAULT) {
            Memo.withNewSession { [Memo.findById(ownId), Memo.get(ownId), Memo.getAll(ownId, otherId)] }
        } == [null, null, [null, null]]
    }

    void 'a lookup by id through withTenant inside withoutId is restricted to that tenant as a query is'() {
        expect:
        Tenants.withoutId(datastore) {
            Memo.withNewSession {
                def other = Memo.withTenant('other')
                [other.findById(ownId), other.get(ownId), other.exists(ownId), other.getAll(ownId, otherId)*.title]
            }
        } == [null, null, false, [null, 'Other']]
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

    private static Long saveMemo(String tenantId, String title) {
        Memo.withTenant(tenantId) {
            Memo.withNewSession {
                Memo.withTransaction { new Memo(title: title).save(flush: true).id }
            }
        }
    }
}

@Entity
class Memo implements Neo4jEntity<Memo>, MultiTenant<Memo> {
    String title
    String tenantId
}
