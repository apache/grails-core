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
import org.bson.Document

class EmbeddedWithNonEmbeddedAssociationsSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Boat, Sailor, Captain])
    }

    void "Test that embedded collections can have non-embedded associations"() {
        given: "A domain model with embedded associations that have non-embedded associations"
        final captain = new Captain(name: "Bob")
        final firstMate = new Sailor(name: "Jim", captain: captain)
        def boat = new Boat(name: "The Float", captain: captain, firstMate: firstMate)
        boat.crew << new Sailor(name: "Fred", captain: captain)
        boat.crew << new Sailor(name: "Joe", captain: captain)
        captain.shipmates << new Sailor(name: "Jeff", captain: captain)
        captain.save()
        boat.save flush: true
        manager.session.clear()

        when: "The domain model is queried"
        boat = Boat.get(boat.id)
        Sailor jeff = Sailor.findByName("Jeff")

        then: "The right results are returned"
        boat != null
        boat.name == "The Float"
        boat.captain != null
        boat.captain.name == 'Bob'
        boat.crew.size() == 2
        boat.crew[0].name == 'Fred'
        boat.crew[0].captain != null
        boat.crew[0].captain.name == 'Bob'
        boat.captain.shipmates.size() == 1
        jeff.name == "Jeff"
        Captain.count() == 1
        Sailor.count() == 1
        Boat.count() == 1

        when: "The underlying Mongo document is queried"
        def boatDbo = Boat.collection.find().first()

        then: "It is correctly defined"
        boatDbo.name == "The Float"
        Captain.collection.find(new Document("_id", boatDbo.captain)).first().name == "Bob"
        boatDbo.firstMate instanceof Document
        boatDbo.firstMate.name == "Jim"
        boatDbo.crew.size() == 2
        boatDbo.crew[0].name == "Fred"
        boatDbo.crew[1].name == "Joe"
        boatDbo.crew[0].captain == boatDbo.captain
    }
}

@Entity
class Boat {
    String id
    String name
    Captain captain
    Sailor firstMate
    List<Sailor> crew = []
    static embedded = ['firstMate', 'crew']
}

@Entity
class Sailor {
    String id
    String name
    Captain captain
}

@Entity
class Captain {
    String id
    String name
    Set<Sailor> shipmates = []
    static hasMany = [shipmates: Sailor]
}
