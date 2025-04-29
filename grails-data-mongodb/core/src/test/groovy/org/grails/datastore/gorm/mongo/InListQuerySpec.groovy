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
package org.grails.datastore.gorm.mongo

import grails.gorm.tests.Person
import grails.gorm.tests.Pet

import org.apache.grails.data.testing.tck.domains.PetType
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

class InListQuerySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses += [Pet, Person, PetType]
    }

    @Issue('https://github.com/grails/gorm-mongodb/issues/11')
    void "Test that in list works for where queries and single-ended associations"() {
        given: "Some test data"
        createPets()
        manager.session.clear()

        when: "An in query is defined via a where query"
        def saurapod = PetType.findByName('Saurapod')
        def tyrannosaur = PetType.findByName('Tyrannosaur')
        def species = [saurapod, tyrannosaur]
        def results = Pet.where {
            type in species
        }.list()

        then: "The results are correct"
        results.size() == 2
        results.find() { Pet pet -> pet.name == 'T-rex' }
        results.find() { Pet pet -> pet.name == 'Dino' }
    }

    @Issue("GPMONGODB-160")
    void "Test that ne works for a single-ended association"() {
        given: "Some test data"
        createPets()
        manager.session.clear()

        when: "Querying an association in a given list"
        def saurapod = PetType.findByName('Saurapod')

        def results = Pet.withCriteria {
            ne 'type', saurapod
            order "name"
        }

        then: "The correct results are returned"
        results.size() == 2
        results[0].name == "Flipper"
        results[1].name == "T-rex"
    }

    @Issue('GPMONGODB-161')
    void "Test that in queries work for single-ended associations"() {
        given: "Some test data"
        createPets()
        manager.session.clear()

        when: "Querying an association in a given list"
        def list = PetType.withCriteria {
            or {
                eq 'name', 'Tyrannosaur'
                eq 'name', 'Saurapod'
            }
        }

        assert list.size() == 2
        def results = Pet.withCriteria {
            inList 'type', list
            order "name"
        }

        then: "The correct results are returned"
        results.size() == 2
        results[0].name == "Dino"
        results[1].name == "T-rex"
    }

    void "Test that in list queries work for simple types"() {
        given: "Some test data"
        createPets()
        manager.session.clear()

        when: "Querying a property in a given list of strings"
        def results = PetType.withCriteria {
            inList 'name', ['Tyrannosaur', 'Saurapod']
            order "name"
        }

        then: "The correct results are returned"
        results.size() == 2
        results[0].name == "Saurapod"
        results[1].name == "Tyrannosaur"


        when: "Querying a property in a given immutable list of GStrings"
        results = PetType.withCriteria {
            inList 'name', ["${'Tyrannosaur'}", "${'Saurapod'}"].asImmutable()
            order "name"
        }

        then: "The correct results are returned"
        results.size() == 2
        results[0].name == "Saurapod"
        results[1].name == "Tyrannosaur"

        when: "Querying an association in a given list of integers"
        results = Pet.withCriteria {
            inList 'age', [10, 5]
            order "name"
        }

        then: "The correct results are returned"
        results.size() == 2
        results[0].name == "Dino"
        results[1].name == "Flipper"
    }

    void createPets() {
        def owner = new Person(firstName: "Fred", lastName: "Flintstone").save()
        assert owner != null
        def saurapod = new PetType(name: "Saurapod").save()
        def tyrannosaur = new PetType(name: "Tyrannosaur").save()
        def plesiosaur = new PetType(name: "Plesiosaur").save()
        assert new Pet(name: "Dino", owner: owner, type: saurapod, age: 5).save()
        assert new Pet(name: "T-rex", owner: owner, type: tyrannosaur, age: 4).save()
        assert new Pet(name: "Flipper", owner: owner, type: plesiosaur, age: 10).save(flush: true)
    }
}
