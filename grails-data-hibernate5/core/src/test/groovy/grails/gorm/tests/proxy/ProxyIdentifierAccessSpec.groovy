/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.gorm.tests.proxy

import groovy.transform.EqualsAndHashCode

import org.hibernate.Hibernate
import spock.lang.Unroll

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec

class ProxyIdentifierAccessSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ProxyCompositeKeyRecord, ProxyNamedKeyRecord, ProxyBusinessIdentifierRecord)
    }

    void 'the mapped id remains accessible on a detached proxy without initialization'() {
        given:
        def record = new ProxyBusinessIdentifierRecord(identifier: 'business').save(flush: true, failOnError: true)
        Long id = record.id
        ProxyBusinessIdentifierRecord.withSession { it.clear() }
        def proxy = ProxyBusinessIdentifierRecord.load(id)
        ProxyBusinessIdentifierRecord.withSession { it.clear() }

        expect:
        proxy.getId() == id
        proxy.id == id
        proxy.getProperty('id') == id
        proxy['id'] == id
        proxy.ident() == id
        !Hibernate.isInitialized(proxy)
    }

    @Unroll
    void 'composite key #access preserves getter and dynamic property behavior'(String access, Closure readId, boolean initializes) {
        given:
        new ProxyCompositeKeyRecord(code: 'COMPOSITE', edition: 2, payload: 'content').save(flush: true, failOnError: true)
        def key = new ProxyCompositeKeyRecord(code: 'COMPOSITE', edition: 2)
        ProxyCompositeKeyRecord.withSession { it.clear() }
        def proxy = ProxyCompositeKeyRecord.load(key)
        assert !Hibernate.isInitialized(proxy)

        expect: 'the complete identifier is available without loading the entity'
        proxy.ident().code == 'COMPOSITE'
        proxy.ident().edition == 2
        !Hibernate.isInitialized(proxy)

        when:
        Object id = readId(proxy)

        then:
        Hibernate.isInitialized(proxy) == initializes
        if (initializes) {
            assert id == null
            assert proxy.payload == 'content'
        } else {
            assert id instanceof ProxyCompositeKeyRecord
            assert id.code == 'COMPOSITE'
            assert id.edition == 2
        }

        where:
        access            | readId                    | initializes
        'getId()'         | { it.getId() }             | true
        'id'              | { it.id }                  | false
        'getProperty(id)' | { it.getProperty('id') }    | false
        'getAt(id)'       | { it['id'] }               | false
    }

    void 'a renamed identifier getter stays lazy and its overload executes normally'() {
        given:
        new ProxyNamedKeyRecord(code: 'NAMED', payload: 'content').save(flush: true, failOnError: true)
        ProxyNamedKeyRecord.withSession { it.clear() }
        def proxy = ProxyNamedKeyRecord.load('NAMED')

        expect:
        proxy.getCode() == 'NAMED'
        proxy.ident() == 'NAMED'
        !Hibernate.isInitialized(proxy)

        when:
        String result = proxy.getCode('prefix-')

        then:
        result == 'prefix-NAMED'
        Hibernate.isInitialized(proxy)
    }

    @Unroll
    void 'renamed key #access preserves getter and dynamic property behavior'(String access, Closure readId, Object expectedId, boolean initializes) {
        given:
        new ProxyNamedKeyRecord(code: 'NAMED', payload: 'content').save(flush: true, failOnError: true)
        ProxyNamedKeyRecord.withSession { it.clear() }
        def proxy = ProxyNamedKeyRecord.load('NAMED')
        assert !Hibernate.isInitialized(proxy)

        when:
        Object id = readId(proxy)

        then:
        id == expectedId
        Hibernate.isInitialized(proxy) == initializes

        where:
        access            | readId                 | expectedId | initializes
        'getId()'         | { it.getId() }          | null       | true
        'id'              | { it.id }               | 'NAMED'    | false
        'getProperty(id)' | { it.getProperty('id') } | 'NAMED'    | false
        'getAt(id)'       | { it['id'] }            | 'NAMED'    | false
    }

    void 'an identifier business property is not replaced with the primary key'() {
        given:
        def record = new ProxyBusinessIdentifierRecord(identifier: 'external-reference').save(flush: true, failOnError: true)
        Long id = record.id
        ProxyBusinessIdentifierRecord.withSession { it.clear() }
        def proxy = ProxyBusinessIdentifierRecord.load(id)

        when:
        String identifier = proxy.getIdentifier()

        then:
        identifier == 'external-reference'
        Hibernate.isInitialized(proxy)
    }
}

@Entity
@EqualsAndHashCode(includes = ['code', 'edition'])
class ProxyCompositeKeyRecord implements Serializable {
    String code
    Integer edition
    String payload

    static mapping = {
        id composite: ['code', 'edition']
    }
}

@Entity
class ProxyNamedKeyRecord implements Serializable {
    String code
    String payload

    static mapping = {
        id name: 'code', generator: 'assigned'
    }

    String getCode(String prefix) {
        prefix + code
    }
}

@Entity
class ProxyBusinessIdentifierRecord implements Serializable {
    String identifier
}
