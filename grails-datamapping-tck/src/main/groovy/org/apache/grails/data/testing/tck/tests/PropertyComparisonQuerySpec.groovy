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

import org.apache.grails.data.testing.tck.domains.Dog
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Tests for criteria queries that compare two properties
 */
class PropertyComparisonQuerySpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([Dog])
    }

    void "Test geProperty query"() {
        given: "Some dead and alive dogs"
        new Dog(name: "Barney", age: 7, deathAge: 14).save()
        new Dog(name: "Fred", age: 13, deathAge: 13).save()
        new Dog(name: "Jack", age: 14, deathAge: 13).save()
        new Dog(name: "Joe", age: 4, deathAge: 14).save(flush: true)

        when: "We query for dogs that are alive"
        def results = Dog.withCriteria {
            geProperty 'age', 'deathAge'
            order 'name'
        }

        then: "3 dogs are found"
        Dog.count() == 4
        results.size() == 2
        results[0].name == "Fred"
        results[1].name == "Jack"
    }

    void "Test leProperty query"() {
        given: "Some dead and alive dogs"
        new Dog(name: "Barney", age: 7, deathAge: 14).save()
        new Dog(name: "Fred", age: 13, deathAge: 13).save()
        new Dog(name: "Jack", age: 14, deathAge: 13).save()
        new Dog(name: "Joe", age: 4, deathAge: 14).save(flush: true)

        when: "We query for dogs that are alive"
        def results = Dog.withCriteria {
            leProperty 'age', 'deathAge'
            order 'name'
        }

        then: "3 dogs are found"
        Dog.count() == 4
        results.size() == 3
        results[0].name == "Barney"
        results[1].name == "Fred"
        results[2].name == "Joe"
    }

    void "Test ltProperty query"() {
        given: "Some dead and alive dogs"
        new Dog(name: "Barney", age: 7, deathAge: 14).save()
        new Dog(name: "Fred", age: 13, deathAge: 13).save()
        new Dog(name: "Joe", age: 4, deathAge: 14).save(flush: true)

        when: "We query for dogs that are alive"
        def results = Dog.withCriteria {
            ltProperty 'age', 'deathAge'
            order 'name'
        }

        then: "2 dogs are found"
        Dog.count() == 3
        results.size() == 2
        results[0].name == "Barney"
        results[1].name == "Joe"
    }

    void "Test gtProperty query"() {
        given: "Some dead and alive dogs"
        new Dog(name: "Barney", age: 7, deathAge: 14).save()
        new Dog(name: "Fred", age: 13, deathAge: 13).save()
        new Dog(name: "Joe", age: 4, deathAge: 14).save(flush: true)

        when: "We query for dogs that are alive"
        def results = Dog.withCriteria {
            gtProperty 'deathAge', 'age'
            order 'name'
        }

        then: "2 dogs are found"
        Dog.count() == 3
        results.size() == 2
        results[0].name == "Barney"
        results[1].name == "Joe"
    }

    void "Test neProperty query"() {
        given: "Some dead and alive dogs"
        new Dog(name: "Barney", age: 7, deathAge: 14).save()
        new Dog(name: "Fred", age: 13, deathAge: 13).save()
        new Dog(name: "Joe", age: 4, deathAge: 14).save(flush: true)

        when: "We query for dogs that are alive"
        def results = Dog.withCriteria {
            neProperty 'age', 'deathAge'
            order 'name'
        }

        then: "2 dogs are found"
        Dog.count() == 3
        results.size() == 2
        results[0].name == "Barney"
        results[1].name == "Joe"
    }

    void "Test eqProperty query"() {
        given: "Some dead and alive dogs"
        new Dog(name: "Barney", age: 7, deathAge: 14).save()
        new Dog(name: "Fred", age: 13, deathAge: 13).save()
        new Dog(name: "Joe", age: 4, deathAge: 14).save(flush: true)

        when: "We query for dogs that died"
        def results = Dog.withCriteria {
            eqProperty 'age', 'deathAge'
        }

        then: "1 dog is found"
        Dog.count() == 3
        results.size() == 1
        results[0].name == "Fred"
    }
}
