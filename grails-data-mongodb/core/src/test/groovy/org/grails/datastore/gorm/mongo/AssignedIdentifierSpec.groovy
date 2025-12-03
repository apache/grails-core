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

import com.mongodb.MongoBulkWriteException
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * Tests for usage of assigned identifiers
 */
class AssignedIdentifierSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([River, Lake, Volcano])
    }

    void "Test that entities can be saved, retrieved and updated with assigned ids"() {
        when: "An entity is saved with an assigned id"
        def r = new River(name: "Amazon", country: "Brazil")
        r.save flush: true
        manager.session.clear()
        r = River.get("Amazon")

        then: "The entity can be retrieved"
        r != null
        r.name == "Amazon"
        r.country == "Brazil"

        when: "The entity is updated"
        r.country = "Argentina"
        r.save flush: true
        manager.session.clear()
        r = River.get("Amazon")

        then: "The update is applied"
        r != null
        r.name == "Amazon"
        r.country == "Argentina"

        when: "The entity is deleted"
        r.delete(flush: true)

        then: "It is gone"
        River.count() == 0
        River.get("Amazon") == null
    }

    @Issue("GPMONGODB-152")
    void "Test that saving a second object with an assigned identifier produces an error"() {
        when: "An entity is saved with an assigned id"
        def r = new River(name: "Amazon", country: "Brazil")
        r.save flush: true
        manager.session.clear()
        r = River.get("Amazon")

        then: "The entity can be retrieved"
        r != null
        r.name == "Amazon"
        r.country == "Brazil"

        when: "A second object with the same id is saved"
        manager.session.clear()
        r = new River(name: "Amazon", country: "Brazil")
        r.save flush: true

        then: "An error is produced"
        thrown MongoBulkWriteException

        when: "A new session is created"
        def totalRivers = 0
        River.withNewSession {
            r = new River(name: "Nile", country: "Egype")
            r.save flush: true
            totalRivers = River.count()
        }

        then: "It is possible to save new instances"
        totalRivers == 2
    }

    @Issue("GPMONGODB-170")
    void "Test that assigned identifiers work with the constructor"() {
        when: "An entity is saved with an assigned id"
        def l = new Lake(id: "Lake Ontario", country: "Canada")
        l.save flush: true
        manager.session.clear()
        l = Lake.get("Lake Ontario")

        then: "The object is correctly retrieved by assigned id"
        l != null
        l.id == "Lake Ontario"
        l.country == "Canada"
    }

    @Issue("GPMONGODB-170")
    void "Test that assigned identifiers work with property setting"() {
        when: "An entity is saved with an assigned id"
        def l = new Lake(country: "Canada")
        l.id = "Lake Ontario"
        l.save flush: true
        manager.session.clear()
        l = Lake.get("Lake Ontario")

        then: "The object is correctly retrieved by assigned id"
        l != null
        l.id == "Lake Ontario"
        l.country == "Canada"
    }

    void "Test that assigned identifiers work with stateless domains"() {
        when: "A stateless entity is saved with an assigned id"
        Volcano v = new Volcano(country: "Spain")
        v.id = "Teide"
        v.insert(flush: true)
        manager.session.clear()
        v = Volcano.get("Teide")

        then: "The object is correctly retrieved by assigned id"
        v.id == "Teide"
        v.country == "Spain"
    }
}

@Entity
class River {
    String name
    String country
    static mapping = {
        id name: 'name', generator: 'assigned'
    }
}

@Entity
class Lake {
    String id
    String country
    static mapping = {
        id generator: 'assigned'
    }
}

@Entity
class Volcano {

    String id
    String country
    static mapping = {
        id generator: 'assigned'
        stateless true
    }

}
