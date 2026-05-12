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

import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author Graeme Rocher
 */
class EmbeddedCollectionWithOneToOneSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Building, Room, RoomCompany])
    }

    void "Test that embedded collections with one to one associations can be persisted correctly"() {
        when: ""
        def buildingInstance = new Building(buildingName: "WorldTradeCentre")
        buildingInstance.save(flush: true, failOnError: true)
        manager.session.clear()
        buildingInstance = Building.findByBuildingName("WorldTradeCentre")
        then: ""
        buildingInstance != null
        buildingInstance.buildingName == "WorldTradeCentre"

        when: ""
        buildingInstance.rooms = []
        buildingInstance.rooms.add(new Room(roomNo: "A001"))
        buildingInstance.rooms.add(new Room(roomNo: "A002"))
        buildingInstance.rooms.add(new Room(roomNo: "A003"))
        buildingInstance.save(flush: true)
        manager.session.clear()
        buildingInstance = Building.findByBuildingName("WorldTradeCentre")
        then: ""
        buildingInstance != null
        buildingInstance.buildingName == "WorldTradeCentre"
        buildingInstance.rooms.size() == 3

        when: ""
        def sony = new RoomCompany(companyName: "Sony")
        sony.save()
        buildingInstance.rooms.getAt(0).refCompany = sony
        buildingInstance.save(flush: true)
        manager.session.clear()
        buildingInstance = Building.findByBuildingName("WorldTradeCentre")

        then: ""
        buildingInstance != null
        buildingInstance.buildingName == "WorldTradeCentre"
        buildingInstance.rooms.size() == 3
        buildingInstance.rooms[0].roomNo == "A001"
        buildingInstance.rooms[0].refCompany != null
        buildingInstance.rooms[0].refCompany.companyName == "Sony"

        when: ""
        def sharp = new RoomCompany(companyName: "Sharp")
        sharp.save()
        buildingInstance.rooms.getAt(1).refCompany = sharp
        buildingInstance.save(flush: true)
        buildingInstance = Building.findByBuildingName("WorldTradeCentre")

        then: ""
        buildingInstance != null
        buildingInstance.buildingName == "WorldTradeCentre"
        buildingInstance.rooms.size() == 3
        buildingInstance.rooms[0].roomNo == "A001"
        buildingInstance.rooms[0].refCompany != null
        buildingInstance.rooms[0].refCompany.companyName == "Sony"
        buildingInstance.rooms[1].roomNo == "A002"
        buildingInstance.rooms[1].refCompany != null
        buildingInstance.rooms[1].refCompany.companyName == "Sharp"

        when: ""
        buildingInstance.buildingName = "WorldTradeCentre 2nd"
        buildingInstance.save(flush: true)
        manager.session.clear()
        buildingInstance = Building.findByBuildingName("WorldTradeCentre 2nd")

        then: ""
        buildingInstance != null
        buildingInstance.buildingName == "WorldTradeCentre 2nd"
        buildingInstance.rooms.size() == 3
        buildingInstance.rooms[0].roomNo == "A001"
        buildingInstance.rooms[0].refCompany != null
        buildingInstance.rooms[0].refCompany.companyName == "Sony"
        buildingInstance.rooms[1].roomNo == "A002"
        buildingInstance.rooms[1].refCompany != null
        buildingInstance.rooms[1].refCompany.companyName == "Sharp"
    }
}
