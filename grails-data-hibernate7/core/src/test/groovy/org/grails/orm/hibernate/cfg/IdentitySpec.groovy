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

package org.grails.orm.hibernate.cfg

import spock.lang.Specification
import spock.lang.Unroll

class IdentitySpec extends Specification {

    def "test toString includes generator, column and type"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity(generator: 'sequence', column: 'my_id', type: Integer)

        expect:
        identity.toString() == 'id[generator:sequence, column:my_id, type:class java.lang.Integer]'
    }

    def "test toString uses defaults"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity()

        expect:
        identity.toString() == 'id[generator:native, column:id, type:class java.lang.Long]'
    }

    def "test naturalId configures NaturalId delegate"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity()

        when:
        identity.naturalId {
            mutable = true
            propertyNames = ['email']
        }

        then:
        identity.natural != null
        identity.natural.mutable == true
        identity.natural.propertyNames == ['email']
    }

    def "test naturalId returns this"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity()

        when:
        HibernateSimpleIdentity returned = identity.naturalId { }

        then:
        returned.is(identity)
    }

    def "test configureNew with closure"() {
        when:
        HibernateSimpleIdentity identity = HibernateSimpleIdentity.configureNew {
            generator = 'uuid'
            column = 'uuid_id'
            type = String
        }

        then:
        identity.generator == 'uuid'
        identity.column == 'uuid_id'
        identity.type == String
    }

    def "test configureExisting with map"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity()

        when:
        HibernateSimpleIdentity result = HibernateSimpleIdentity.configureExisting(identity, [generator: 'assigned', column: 'pk'])

        then:
        result.is(identity)
        result.generator == 'assigned'
        result.column == 'pk'
    }

    def "test configureExisting with closure"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity()

        when:
        HibernateSimpleIdentity result = HibernateSimpleIdentity.configureExisting(identity) {
            generator = 'increment'
            name = 'myId'
        }

        then:
        result.is(identity)
        result.generator == 'increment'
        result.name == 'myId'
    }

    def "test getProperties returns empty Properties when params is empty"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity()

        when:
        Properties props = identity.getProperties()

        then:
        props != null
        props.isEmpty()
    }

    def "test getProperties returns Properties populated from params"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity(params: [sequenceName: 'my_seq', allocationSize: '50'])

        when:
        Properties props = identity.getProperties()

        then:
        props.getProperty('sequenceName') == 'my_seq'
        props.getProperty('allocationSize') == '50'
    }

    def "test getProperties with null params returns empty Properties"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity()
        identity.params = null

        when:
        Properties props = identity.getProperties()

        then:
        props != null
        props.isEmpty()
    }

    @Unroll
    def "test determineGeneratorName with generator=#generatorName and useSequence=#useSequence"() {
        given:
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity(generator: generatorName)

        expect:
        identity.determineGeneratorName(useSequence) == expected

        where:
        generatorName | useSequence | expected
        'native'      | false       | 'native'
        'native'      | true        | 'sequence-identity'
        'identity'    | false       | 'identity'
        'identity'    | true        | 'identity'
        'sequence'    | true        | 'sequence'
        'increment'   | false       | 'increment'
        null          | false       | 'native'
        null          | true        | 'sequence-identity'
    }

    @Unroll
    def "test static determineGeneratorName with mappedId=#mappedIdPresent and useSequence=#useSequence"() {
        given:
        HibernateSimpleIdentity identity = mappedIdPresent ? new HibernateSimpleIdentity(generator: generatorName) : null

        expect:
        HibernateSimpleIdentity.determineGeneratorName(identity, useSequence) == expected

        where:
        mappedIdPresent | generatorName | useSequence | expected
        true            | 'native'      | false       | 'native'
        true            | 'native'      | true        | 'sequence-identity'
        true            | 'uuid'        | false       | 'uuid'
        false           | null          | false       | 'native'
        false           | null          | true        | 'sequence-identity'
    }

    def "test getPropertyNames"() {
        expect:
        new HibernateSimpleIdentity(name: "id").getPropertyNames() == ["id"] as String[]
        new HibernateSimpleIdentity(name: null).getPropertyNames() == [] as String[]
    }
}
