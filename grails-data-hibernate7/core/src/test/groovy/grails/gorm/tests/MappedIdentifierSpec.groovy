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

import groovy.transform.EqualsAndHashCode

import org.hibernate.Hibernate

import grails.gorm.annotation.Entity

class MappedIdentifierSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(MappedIdentOwner, MappedIdentNamedRecord, MappedIdentCompositeRecord)
    }

    void 'ident returns the generated identifier and null before persistence'() {
        given:
        def owner = new MappedIdentOwner(name: 'owner')

        expect:
        owner.ident() == null

        when:
        owner.save(flush: true, failOnError: true)

        then:
        owner.ident() == owner.id
        owner.ident() != null
    }

    void 'ident uses the mapped identifier name'() {
        given:
        def record = new MappedIdentNamedRecord(code: 'NAMED-1', payload: 'content')

        expect:
        record.ident() == 'NAMED-1'

        when:
        record.save(flush: true, failOnError: true)
        MappedIdentNamedRecord.withSession { it.clear() }

        then:
        MappedIdentNamedRecord.get(record.ident()).payload == 'content'
    }

    void 'ident copies only composite key fields into a separate identifier'() {
        given:
        def owner = new MappedIdentOwner(name: 'owner')
        def record = new MappedIdentCompositeRecord(owner: owner, code: 'PARAMETER', payload: 'content')

        when:
        Serializable identifier = record.ident()

        then:
        identifier instanceof MappedIdentCompositeRecord
        !identifier.is(record)
        identifier.owner.is(owner)
        identifier.code == 'PARAMETER'
        identifier.payload == null
        record.id == null
    }

    void 'a composite identifier containing an association supports get and lazy load'() {
        given:
        def owner = new MappedIdentOwner(name: 'owner').save(failOnError: true)
        def record = new MappedIdentCompositeRecord(owner: owner, code: 'PARAMETER', payload: 'content')
                .save(flush: true, failOnError: true)
        Serializable identifier = record.ident()
        assert identifier != null
        MappedIdentCompositeRecord.withSession { it.clear() }

        when:
        def reloaded = MappedIdentCompositeRecord.get(identifier)

        then:
        reloaded != null
        reloaded.code == 'PARAMETER'
        reloaded.owner.id == owner.id
        reloaded.payload == 'content'

        when:
        MappedIdentCompositeRecord.withSession { it.clear() }
        def proxy = MappedIdentCompositeRecord.load(identifier)

        then:
        proxy != null
        !Hibernate.isInitialized(proxy)

        when:
        String payload = proxy.payload

        then:
        payload == 'content'
        Hibernate.isInitialized(proxy)
    }

    void 'ident on an uninitialized composite-key proxy returns the identifier it was created with'() {
        given:
        def owner = new MappedIdentOwner(name: 'owner').save(failOnError: true)
        new MappedIdentCompositeRecord(owner: owner, code: 'PARAMETER', payload: 'content')
                .save(flush: true, failOnError: true)
        def key = new MappedIdentCompositeRecord(owner: owner, code: 'PARAMETER', payload: 'not part of the key')
        MappedIdentCompositeRecord.withSession { it.clear() }
        def proxy = MappedIdentCompositeRecord.load(key)

        expect:
        !Hibernate.isInitialized(proxy)

        when:
        Serializable identifier = proxy.ident()

        then:
        identifier.is(key)
        identifier.payload == 'not part of the key'
        !Hibernate.isInitialized(proxy)
    }
}

@Entity
class MappedIdentOwner implements Serializable {
    String name
}

@Entity
class MappedIdentNamedRecord implements Serializable {
    String code
    String payload

    static mapping = {
        id name: 'code', generator: 'assigned'
    }
}

@Entity
@EqualsAndHashCode(includes = ['owner', 'code'])
class MappedIdentCompositeRecord implements Serializable {
    MappedIdentOwner owner
    String code
    String payload

    static mapping = {
        id composite: ['owner', 'code']
    }
}
