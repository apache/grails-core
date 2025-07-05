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
 * Tests the use of embedded collections in inheritance hierarchies.
 */
class EmbeddedCollectionAndInheritanceSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([ECAISPerson, ECAISPet, ECAISDog])
    }

    def "Test read and write embedded collection inherited from parent"() {
        when: "A embedded subclass entity is added to a collection"
        def p = new ECAISPerson()
        p.pets << new ECAISDog(name: "Joe", anotherField: "foo")
        p.save(flush: true)
        manager.session.clear()

        p = ECAISPerson.get(p.id)

        then: "The dog is persisted correctly"
        p != null
        p.pets.size() == 1
        p.pets[0] instanceof ECAISDog
        p.pets[0].anotherField == 'foo'

        when: "An embedded subclass entity is updated in the collection"
        p.pets << new ECAISDog(name: "Fred", anotherField: 'bar')
        p.save(flush: true)
        manager.session.clear()
        p = ECAISPerson.get(p.id)

        then: "The dogs are persisted correctly"
        p != null
        p.pets.size() == 2
        p.pets[0] instanceof ECAISDog
        p.pets[0].name == "Joe"
        p.pets[0].anotherField == 'foo'
        p.pets[1] instanceof ECAISDog
        p.pets[1].name == "Fred"
        p.pets[1].anotherField == 'bar'

        when: "An update is made to an embedded collection entry but not the collection itself"
        p.pets[0].name = "Changed"
        p.pets[0].anotherField = "ChangedAnotherField"
        p.save(flush: true)
        manager.session.clear()
        p = ECAISPerson.get(p.id)

        then: "The update is correctly applied"
        p != null
        p.pets.size() == 2
        p.pets[0] instanceof ECAISDog
        p.pets[0].name == "Changed"
        p.pets[0].anotherField == "ChangedAnotherField"
        p.pets[1] instanceof ECAISDog
        p.pets[1].name == "Fred"
        p.pets[1].anotherField == 'bar'

        when: "An embedded entity is removed from a collection"
        p.pets.remove(0)
        p.save(flush: true)
        manager.session.clear()
        p = ECAISPerson.get(p.id)

        then: "The update is correctly applied"
        p != null
        p.pets.size() == 1
        p.pets[0] instanceof ECAISDog
        p.pets[0].name == "Fred"
        p.pets[0].anotherField == 'bar'
    }
}

@Entity
class ECAISPerson {
    String id
    List<ECAISPet> pets = []
    static hasMany = [pets: ECAISPet]
    static embedded = ['pets']
}

@Entity
class ECAISPet {
    String id
    String name
}

@Entity
class ECAISDog extends ECAISPet {
    String id
    String name
    String anotherField
}
