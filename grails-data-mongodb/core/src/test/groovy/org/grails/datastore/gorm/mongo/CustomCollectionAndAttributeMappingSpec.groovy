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

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Tests for the case where a custom mapping is used.
 */
class CustomCollectionAndAttributeMappingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([CCAAMPerson])
    }

    void "Test that custom collection and attribute names are correctly used"() {
        when: "An entity with custom collection and attribute naming is persisted"
        def p = new CCAAMPerson(groupId: 10, pets: [new CCAAMPet(name: "Joe")]).save(flush: true)
        def dbo = CCAAMPerson.collection.find()
                .first()
        then: "The underlying mongo collection is correctly populated"
        CCAAMPerson.collection.namespace.collectionName == "persons"
        dbo.gid == 10
        dbo.ps != null
        dbo.ps.size() == 1
        dbo.ps[0].nom == "Joe"

        when: "An entity is queried"
        manager.session.clear()
        p = CCAAMPerson.get(p.id)

        then: "It is returned in the correct state"
        p.groupId == 10
        p.pets.size() == 1
        p.pets[0].name == "Joe"

        when: "An order by query is used"
        manager.session.clear()
        new CCAAMPerson(groupId: 5, pets: [new CCAAMPet(name: "Fred")]).save(flush: true)
        new CCAAMPerson(groupId: 15, pets: [new CCAAMPet(name: "Ed")]).save(flush: true)
        def results = CCAAMPerson.list(sort: "groupId")

        then: "The results are in the correct order"
        results.size() == 3
        results[0].groupId == 5
        results[1].groupId == 10
        results[2].groupId == 15

        when: "A dynamic finder is used in a query"
        manager.session.clear()
        results = CCAAMPerson.findAllByGroupId(10)

        then: "The results are correct"
        results.size() == 1
        results[0].groupId == 10
    }
}

@Entity
class CCAAMPerson {
    String id
    Integer groupId
    List<CCAAMPet> pets = []

    static mapWith = "mongo"

    static embedded = ['pets']

    static mapping = {
        collection 'persons'
        groupId attribute: 'gid'
        pets attribute: 'ps'
    }
}

class CCAAMPet {
    String name
    static mapping = {
        name attribute: "nom"
    }
}
