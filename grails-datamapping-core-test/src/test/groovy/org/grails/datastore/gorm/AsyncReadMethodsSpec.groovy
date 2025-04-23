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

package org.grails.datastore.gorm

import grails.async.Promise
import grails.async.Promises
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.Person

/**
 */
class AsyncReadMethodsSpec extends GormDatastoreSpec{

    def "Test that normal GORM methods can be used within the doAsync method"() {
        given:"Some people"
            final p1 = new Person(firstName: "Homer", lastName: "Simpson").save()
            final p2 = new Person(firstName: "Bart", lastName: "Simpson").save()
            final p3 = new Person(firstName: "Barney", lastName: "Rubble").save(flush: true)
            session.clear()

        when:"We obtain a promise via the exec method"
            def promise = Person.async.task {
                list()
            }

            def results = promise.get()
        then:"The results are correct"
            results.size() == 3
            results[0].firstName == "Homer"
            results[1].firstName == "Bart"
            results[2].firstName == "Barney"
    }
    def "Test that the list method works async"()  {
        given:"Some people"
            new Person(firstName: "Homer", lastName: "Simpson").save()
            new Person(firstName: "Bart", lastName: "Simpson").save()
            new Person(firstName: "Barney", lastName: "Rubble").save(flush:true)
            session.clear()

        when:"We list all entities async"
            def promise = Person.async.list()

        then:"A promise is returned"
            promise instanceof Promise

        when:"The promise value is returned"
            def results = promise.get()

        then:"They are correct"
            results.size() == 3

    }

    def "Test multiples GORM promises using get method"() {
        given:"Some people"
            final p1 = new Person(firstName: "Homer", lastName: "Simpson").save()
            final p2 = new Person(firstName: "Bart", lastName: "Simpson").save()
            final p3 = new Person(firstName: "Barney", lastName: "Rubble").save(flush: true)
            session.clear()

        when:"We obtain multiple promises and await the response"
            def prom1 = Person.async.get(p1.id)
            def prom2 = Person.async.get(p2.id)
            def prom3 = Person.async.get(p3.id)
            def results = Promises.waitAll(prom1, prom2, prom3)

        then:"The results are correct"
            results.size() == 3
            results[0].firstName == "Homer"
            results[1].firstName == "Bart"
            results[2].firstName == "Barney"

        when:"We obtain the results with the getAll method async"
            results = Person.async.getAll(p1.id, p2.id, p3.id).get()

        then:"The results are correct"
            results.size() == 3
            results[0].firstName == "Homer"
            results[1].firstName == "Bart"
            results[2].firstName == "Barney"
    }

    def "Test multiples GORM promises using dynamic finder method"() {
        given:"Some people"
            final p1 = new Person(firstName: "Homer", lastName: "Simpson").save()
            final p2 = new Person(firstName: "Bart", lastName: "Simpson").save()
            final p3 = new Person(firstName: "Barney", lastName: "Rubble").save(flush: true)
            session.clear()

        when:"We obtain multiple promises and await the response"
            def prom1 = Person.async.findByFirstName("Homer")
            def prom2 = Person.async.findByFirstName("Bart")
            def prom3 = Person.async.findByFirstName("Barney")
            def results = Promises.waitAll(prom1, prom2, prom3)

        then:"The results are correct"
            results.size() == 3
            results[0].firstName == "Homer"
            results[1].firstName == "Bart"
            results[2].firstName == "Barney"
    }
}
