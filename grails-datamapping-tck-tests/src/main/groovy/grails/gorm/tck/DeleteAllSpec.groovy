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

package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.Person

class DeleteAllSpec extends GormDatastoreSpec {

    def "Test that many objects can be deleted at once using multiple arguments"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder").save(flush: true)
            def fred = new Person(firstName:"Fred", lastName:"Flintstone").save(flush: true)
            def joe = new Person(firstName:"Joe", lastName:"Doe").save(flush: true)
            Person.deleteAll(bob, fred, joe)
            session.flush()

        when:
            def total = Person.count()
        then:
            total == 0
    }
    def "Test that many objects can be deleted using an iterable"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder").save(flush: true)
            def fred = new Person(firstName:"Fred", lastName:"Flintstone").save(flush: true)
            def joe = new Person(firstName:"Joe", lastName:"Doe").save(flush: true)

            Vector<Person> people = new Vector<Person>()
            people.add(bob)
            people.add(fred)
            people.add(joe)

            Person.deleteAll(people)
            session.flush()

        when:
            def total = Person.count()
        then:
            total == 0
    }

    def "Test that many objects can be deleted at once using multiple arguments and flushes"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder").save(flush: true)
            def fred = new Person(firstName:"Fred", lastName:"Flintstone").save(flush: true)
            def joe = new Person(firstName:"Joe", lastName:"Doe").save(flush: true)
            Person.deleteAll(flush: true, bob, fred, joe)

        when:
            def total = Person.count()
            then:
            total == 0
    }
    def "Test that many objects can be deleted using an iterable and flushes"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder").save(flush: true)
            def fred = new Person(firstName:"Fred", lastName:"Flintstone").save(flush: true)
            def joe = new Person(firstName:"Joe", lastName:"Doe").save(flush: true)

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
}
