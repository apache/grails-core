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
import grails.gorm.tests.Person
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Tests the nullification of properties
 */
class NullifyPropertySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses += [Pet, Person]
    }

    void "Test nullify basic property"() {
        given: "A an entity with a basic property"

        def pet = new Pet(name: "Spike")
        pet.save flush: true
        manager.session.clear()
        pet = Pet.get(pet.id)

        when: "A property is nulled"
        pet.name = null
        pet.save flush: true, validate: false
        manager.session.clear()
        pet = Pet.get(pet.id)

        then: "It is null when retrieved"
        pet != null
        pet.name == null
    }

    void "Test nullify to-one association"() {
        given: "A an entity with a to-one association"

        def bob = new Person(firstName: "Bob", lastName: "Builder")
        bob.save()
        def pet = new Pet(name: "Spike", owner: bob)
        pet.save flush: true
        manager.session.clear()
        pet = Pet.get(pet.id)
        assert pet.owner != null

        when: "A property is nulled"
        pet.owner = null
        pet.save flush: true
        manager.session.clear()
        pet = Pet.get(pet.id)

        then: "It is null when retrieved"
        pet != null
        pet.owner == null
    }
}
