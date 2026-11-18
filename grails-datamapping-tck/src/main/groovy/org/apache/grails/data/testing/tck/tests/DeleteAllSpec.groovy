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
import spock.lang.Issue

class DeleteAllSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.registerDomainClasses(Person)
    }

    def 'Test that many objects can be deleted at once using multiple arguments'() {
        given:
        def bob = new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        def fred = new Person(firstName: 'Fred', lastName: 'Flintstone').save(flush: true)
        def joe = new Person(firstName: 'Joe', lastName: 'Doe').save(flush: true)
        Person.deleteAll(bob, fred, joe)
        manager.session.flush()

        when:
        def total = Person.count()
        then:
        total == 0
    }

    def 'Test that many objects can be deleted using an iterable'() {
        given:
        def bob = new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        def fred = new Person(firstName: 'Fred', lastName: 'Flintstone').save(flush: true)
        def joe = new Person(firstName: 'Joe', lastName: 'Doe').save(flush: true)

        Vector<Person> people = new Vector<Person>()
        people.add(bob)
        people.add(fred)
        people.add(joe)

        Person.deleteAll(people)
        manager.session.flush()

        when:
        def total = Person.count()
        then:
        total == 0
    }

    def 'Test that many objects can be deleted at once using multiple arguments and flushes'() {
        given:
        def bob = new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        def fred = new Person(firstName: 'Fred', lastName: 'Flintstone').save(flush: true)
        def joe = new Person(firstName: 'Joe', lastName: 'Doe').save(flush: true)
        Person.deleteAll(flush: true, bob, fred, joe)

        when:
        def total = Person.count()
        then:
        total == 0
    }

    def 'Test that many objects can be deleted using an iterable and flushes'() {
        given:
        def bob = new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        def fred = new Person(firstName: 'Fred', lastName: 'Flintstone').save(flush: true)
        def joe = new Person(firstName: 'Joe', lastName: 'Doe').save(flush: true)

        Vector<Person> people = new Vector<Person>()
        people.add(bob)
        people.add(fred)
        people.add(joe)

        Person.deleteAll(flush: true, people)

        when:
        def total = Person.count()
        then:
        total == 0
    }

    @Issue('https://github.com/apache/grails-data-mapping/issues/969')
    def 'Test deleteAll on a where query converts identifier types'() {
        given:
        new Person(firstName: 'Bob', lastName: 'Builder').save(flush: true)
        new Person(firstName: 'Fred', lastName: 'Flintstone').save(flush: true)

        expect:
        Person.count() == 2

        when: 'deleting by a where query whose id list holds a narrower numeric type'
        def idList = [Person.findByFirstName('Fred').id as Integer]
        Person.where {
            id in idList
        }.deleteAll()

        then:
        Person.count() == 1
        Person.findByFirstName('Bob')
    }
}
