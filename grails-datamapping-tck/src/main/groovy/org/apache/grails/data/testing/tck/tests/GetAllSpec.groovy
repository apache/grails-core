/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.Person

/**
 * The contract for {@code getAll(ids)}: the result is positionally aligned with the ids supplied,
 * so callers can zip the two lists together. Every adapter must honour it.
 */
class GetAllSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.registerDomainClasses(Person)
    }

    def 'getAll returns entities in the order the ids were supplied, not the order the database chose'() {
        given:
        def bob = new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        def fred = new Person(firstName: 'Fred', lastName: 'Flintstone').save(flush: true)
        def joe = new Person(firstName: 'Joe', lastName: 'Doe').save(flush: true)
        manager.session.clear()

        when:
        def results = Person.getAll(joe.id, bob.id, fred.id)

        then:
        results.size() == 3
        results*.firstName == ['Joe', 'Bob', 'Fred']
    }

    def 'getAll preserves duplicate ids, returning one element per requested id'() {
        given:
        def bob = new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        def fred = new Person(firstName: 'Fred', lastName: 'Flintstone').save(flush: true)
        manager.session.clear()

        when:
        def results = Person.getAll(bob.id, fred.id, bob.id)

        then:
        results.size() == 3
        results*.firstName == ['Bob', 'Fred', 'Bob']
    }

    def 'getAll yields a null at the position of an id that resolves to no row'() {
        given:
        def bob = new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        def fred = new Person(firstName: 'Fred', lastName: 'Flintstone').save(flush: true)
        def missingId = fred.id
        fred.delete(flush: true)
        manager.session.clear()

        when:
        def results = Person.getAll(bob.id, missingId)

        then: 'the slot is kept so positions still line up with the requested ids'
        results.size() == 2
        results[0].firstName == 'Bob'
        results[1] == null
    }

    def 'getAll returns an empty list for empty input'() {
        given:
        new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        manager.session.clear()

        expect:
        Person.getAll([]) == []
    }
}
