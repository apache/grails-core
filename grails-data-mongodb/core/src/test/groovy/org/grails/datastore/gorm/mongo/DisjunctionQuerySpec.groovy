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

import grails.gorm.tests.Pet

import org.apache.grails.data.testing.tck.domains.PetType
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

class DisjunctionQuerySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    def dogType
    def catType
    def birdType

    void setupSpec() {
        manager.domainClasses += [Pet, PetType]
    }

    @Issue('GPMONGODB-380')
    void "Find all pets of type cat or of type dog"() {
        given: "Some data"
        loadTestData()

        when: "All pets with type of dog or name of Jack are retrieved"
        def results = Pet.findAllByTypeOrName(dogType, 'Jack')

        then: "The correct number of pets were found"
        results.size() == 3

        and: "The correct pets were found"
        results.find { it.name == "Rocco" }
        results.find { it.name == "Max" }
        results.find { it.name == "Jack" }
    }

    @Issue('GPMONGODB-380')
    void "Find only pets of bird type or bird type"() {
        given: "Some data"
        loadTestData()

        when: "Only bird type or bird type pets are retrieved"
        def pet = Pet.findByTypeOrType(birdType, birdType)

        then: "The correct pet is returned"
        pet != null
        pet.name == "Big Bird"
    }

    @Issue('GPMONGODB-380')
    void "Count all dogs or pets with the name Jack"() {
        given: "Some data"
        loadTestData()

        expect:
        Pet.countByTypeOrName(dogType, 'Jack') == 3
    }

    private void loadTestData() {
        dogType = new PetType(name: 'dog').save()
        catType = new PetType(name: 'cat').save()
        birdType = new PetType(name: 'bird').save()

        new Pet(name: "Rocco", type: dogType).save()
        new Pet(name: "Max", type: dogType).save()
        new Pet(name: "Jack", type: catType).save()
        new Pet(name: "Big Bird", type: birdType).save(flush: true)

        manager.session.clear()
    }
}