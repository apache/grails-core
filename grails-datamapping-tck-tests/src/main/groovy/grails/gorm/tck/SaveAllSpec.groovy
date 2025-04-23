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

class SaveAllSpec extends GormDatastoreSpec {

    def "Test that many objects can be saved at once using multiple arguments"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder")
            def fred = new Person(firstName:"Fred", lastName:"Flintstone")
            def joe = new Person(firstName:"Joe", lastName:"Doe")

            Person.saveAll(bob, fred, joe)

        when:
            def total = Person.count()
            def results = Person.list()
        then:
            total == 3
            results.every { it.id != null } == true
    }

    def "Test that many objects can be saved at once using a list"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder")
            def fred = new Person(firstName:"Fred", lastName:"Flintstone")
            def joe = new Person(firstName:"Joe", lastName:"Doe")

            Person.saveAll([bob, fred, joe])

        when:
            def total = Person.count()
            def results = Person.list()
        then:
            total == 3
            results.every { it.id != null } == true
    }

    def "Test that many objects can be saved at once using an iterable"() {
        given:
            def bob = new Person(firstName:"Bob", lastName:"Builder")
            def fred = new Person(firstName:"Fred", lastName:"Flintstone")
            def joe = new Person(firstName:"Joe", lastName:"Doe")

            Vector<Person> personVector = new Vector<Person>()
            personVector.add(bob)
            personVector.add(fred)
            personVector.add(joe)

            Person.saveAll(personVector)

        when:
            def total = Person.count()
            def results = Person.list()
        then:
            total == 3
            results.every { it.id != null } == true
    }
}
