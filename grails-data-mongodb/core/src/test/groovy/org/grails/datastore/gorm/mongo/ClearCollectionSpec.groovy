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
import org.bson.types.ObjectId

class ClearCollectionSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Building, Room, RoomCompany])
    }

    void "Test clear embedded mongo collection"() {
        given: "An entity with an embedded collection"
        Building b = new Building(buildingName: "WTC", rooms: [new Room(roomNo: 1), new Room(roomNo: 1)]).save(flush: true, validate: false)
        manager.session.clear()

        when: "The entity is queried"
        b = Building.get(b.id)

        then: "The entity was persisted correctly"
        b.buildingName == "WTC"
        b.rooms.size() == 2
        b.rooms[0].roomNo == "1"

        when: "The association is cleared"
        b.rooms.clear()
        b.save(flush: true)
        manager.session.clear()
        b = Building.get(b.id)

        then: "It is empty"
        b.rooms.size() == 0
    }
}

@Entity
class Building {
    ObjectId id
    String buildingName
    List<Room> rooms

    static mapWith = "mongo"
    static mapping = {
        collection "building"
        version false
    }
    static constraints = {
        rooms(blank: true, nullable: true)
    }
    static embedded = ['rooms']
}

@Entity
class Room {
    ObjectId id
    String roomNo
    RoomCompany refCompany

    static mapWith = "mongo"
    static constraints = {
    }
}

@Entity
class RoomCompany {
    ObjectId id
    String companyName

    static mapWith = "mongo"
    static constraints = {
    }
    static mapping = {
        collection "company"
        version false
    }
}
