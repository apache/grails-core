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

import org.apache.grails.data.testing.tck.domains.SimpleCountry
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Tests for querying the size of collections etc.
 */
class SizeQuerySpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([SimpleCountry, Person])
    }

    void 'Test sizeLe criterion'() {
        given: 'A country with only 1 resident'
        Person p = new Person(firstName: 'Fred', lastName: 'Flinstone')
        SimpleCountry c = new SimpleCountry(name: 'Dinoville')
                .addToResidents(p)
                .save(flush: true)

        new SimpleCountry(name: 'Springfield')
                .addToResidents(firstName: 'Homer', lastName: 'Simpson')
                .addToResidents(firstName: 'Bart', lastName: 'Simpson')
                .addToResidents(firstName: 'Marge', lastName: 'Simpson')
                .save(flush: true)

        new SimpleCountry(name: 'Miami')
                .addToResidents(firstName: 'Dexter', lastName: 'Morgan')
                .save(flush: true)

        when: 'We query for countries with less than or equal to 1 resident'
        def results = SimpleCountry.where {
            residents.size() <= 1
        }.list()

        then: 'We get the correct result back'
        results.size() == 2
        results.any { it.name == 'Miami' }
        results.any { it.name == 'Dinoville' }

        when: 'We query for countries with less than or equal to 3 resident'
        results = SimpleCountry.where {
            sizeLe 'residents', 3
        }.list()

        then: 'We get the correct result back'
        results.size() == 3

        when: 'We query for countries with less than or equal to 0 residents'
        results = SimpleCountry.where {
            sizeLe 'residents', 0
        }.list()

        then: 'we get no results back'
        results.size() == 0
    }

    void 'Test sizeGe criterion'() {
        given: 'A country with only 1 resident'
        Person p = new Person(firstName: 'Fred', lastName: 'Flinstone')
        SimpleCountry c = new SimpleCountry(name: 'Dinoville')
                .addToResidents(p)
                .save(flush: true)

        new SimpleCountry(name: 'Springfield')
                .addToResidents(firstName: 'Homer', lastName: 'Simpson')
                .addToResidents(firstName: 'Bart', lastName: 'Simpson')
                .addToResidents(firstName: 'Marge', lastName: 'Simpson')
                .save(flush: true)

        new SimpleCountry(name: 'Miami')
                .addToResidents(firstName: 'Dexter', lastName: 'Morgan')
                .save(flush: true)

        when: 'We query for countries with greater than or equal to 1 resident'
        def results = SimpleCountry.where {
            residents.size() >= 1
        }.list()

        then: 'We get the correct result back'
        results.size() == 3

        when: 'We query for countries with greater than or equal to 3 resident'
        results = SimpleCountry.where {
            sizeGe 'residents', 3
        }.list()

        then: 'We get the correct result back'
        results.size() == 1
        results[0].name == 'Springfield'

        when: 'We query for countries with greater than or equal to 4 residents'
        results = SimpleCountry.where {
            sizeGe 'residents', 4
        }.list()

        then: 'we get no results back'
        results.size() == 0
    }

    void 'Test sizeLt criterion'() {
        given: 'A country with only 1 resident'
        Person p = new Person(firstName: 'Fred', lastName: 'Flinstone')
        SimpleCountry c = new SimpleCountry(name: 'Dinoville')
                .addToResidents(p)
                .save(flush: true)

        new SimpleCountry(name: 'Springfield')
                .addToResidents(firstName: 'Homer', lastName: 'Simpson')
                .addToResidents(firstName: 'Bart', lastName: 'Simpson')
                .addToResidents(firstName: 'Marge', lastName: 'Simpson')
                .save(flush: true)

        new SimpleCountry(name: 'Miami')
                .addToResidents(firstName: 'Dexter', lastName: 'Morgan')
                .save(flush: true)

        when: 'We query for countries with less than 2 resident'
        def results = SimpleCountry.where {
            residents.size() < 2
        }.list()

        then: 'We get the correct result back'
        results.size() == 2
        results.any { it.name == 'Miami' }
        results.any { it.name == 'Dinoville' }

        when: 'We query for countries with less than 4 resident'
        results = SimpleCountry.where {
            sizeLt 'residents', 4
        }.list()

        then: 'We get the correct result back'
        results.size() == 3

        when: 'We query for countries with less than 1 residents'
        results = SimpleCountry.where {
            sizeLt 'residents', 1
        }.list()

        then: 'we get no results back'
        results.size() == 0
    }

    void 'Test sizeGt criterion'() {
        given: 'A country with only 1 resident'
        Person p = new Person(firstName: 'Fred', lastName: 'Flinstone')
        SimpleCountry c = new SimpleCountry(name: 'Dinoville')
                .addToResidents(p)
                .save(flush: true)

        new SimpleCountry(name: 'Springfield')
                .addToResidents(firstName: 'Homer', lastName: 'Simpson')
                .addToResidents(firstName: 'Bart', lastName: 'Simpson')
                .addToResidents(firstName: 'Marge', lastName: 'Simpson')
                .save(flush: true)

        new SimpleCountry(name: 'Miami')
                .addToResidents(firstName: 'Dexter', lastName: 'Morgan')
                .save(flush: true)

        when: 'We query for countries with more than 1 resident'
        def results = SimpleCountry.where {
            residents.size() > 1
        }.list()

        then: 'We get the correct result back'
        results.size() == 1
        results[0].name == 'Springfield'

        when: 'We query for countries with more than 0 resident'
        results = SimpleCountry.where {
            sizeGt 'residents', 0
        }.list()

        then: 'We get the correct result back'
        results.size() == 3

        when: 'We query for countries with more than 3 residents'
        results = SimpleCountry.where {
            sizeGt 'residents', 3
        }.list()

        then: 'we get no results back'
        results.size() == 0
    }

    void 'Test sizeEq criterion'() {
        given: 'A country with only 1 resident'
        Person p = new Person(firstName: 'Fred', lastName: 'Flinstone')
        SimpleCountry c = new SimpleCountry(name: 'Dinoville')
                .addToResidents(p)
                .save(flush: true)

        new SimpleCountry(name: 'Springfield')
                .addToResidents(firstName: 'Homer', lastName: 'Simpson')
                .addToResidents(firstName: 'Bart', lastName: 'Simpson')
                .addToResidents(firstName: 'Marge', lastName: 'Simpson')
                .save(flush: true)

        new SimpleCountry(name: 'Miami')
                .addToResidents(firstName: 'Dexter', lastName: 'Morgan')
                .save(flush: true)

        when: 'We query for countries with 1 resident'
        def results = SimpleCountry.where {
            residents.size() == 1
        }.list()

        then: 'We get the correct result back'
        results.size() == 2
        results.any { it.name == 'Miami' }
        results.any { it.name == 'Dinoville' }

        when: 'We query for countries with 3 resident'
        results = SimpleCountry.where {
            sizeEq 'residents', 3
        }.list()

        then: 'We get the correct result back'
        results.size() == 1
        results[0].name == 'Springfield'

        when: 'We query for countries with 2 residents'
        results = SimpleCountry.where {
            sizeEq 'residents', 2
        }.list()

        then: 'we get no results back'
        results.size() == 0
    }

    void 'Test sizeNe criterion'() {
        given: 'A country with only 1 resident'
        Person p = new Person(firstName: 'Fred', lastName: 'Flinstone')
        SimpleCountry c = new SimpleCountry(name: 'Dinoville')
                .addToResidents(p)
                .save(flush: true)

        new SimpleCountry(name: 'Springfield')
                .addToResidents(firstName: 'Homer', lastName: 'Simpson')
                .addToResidents(firstName: 'Bart', lastName: 'Simpson')
                .addToResidents(firstName: 'Marge', lastName: 'Simpson')
                .save(flush: true)

        new SimpleCountry(name: 'Miami')
                .addToResidents(firstName: 'Dexter', lastName: 'Morgan')
                .save(flush: true)

        when: 'We query for countries with not 1 resident'
        def results = SimpleCountry.where {
            residents.size() != 1
        }.list()

        then: 'We get the correct result back'
        results.size() == 1
        results[0].name == 'Springfield'

        when: 'We query for countries with not 3 resident'
        results = SimpleCountry.where {
            sizeNe 'residents', 3
        }.list()

        then: 'We get the correct result back'
        results.size() == 2
        results.any { it.name == 'Miami' }
        results.any { it.name == 'Dinoville' }

        when: 'We query for countries with 2 residents'
        results = SimpleCountry.where {
            sizeNe 'residents', 1
            sizeNe 'residents', 3
        }.list()

        then: 'we get no results back'
        results.size() == 0
    }
}
