package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec

/**
 * @author Graeme Rocher
 */
class EmbeddedCollectionWithOneToOneSpec extends GormDatastoreSpec {

    void "Test that embedded collections with one to one associations can be persisted correctly"() {
        when:""
            def buildingInstance = new Building(buildingName:"WorldTradeCentre")
            buildingInstance.save(flush:true,failOnError:true)
            session.clear()
            buildingInstance = Building.findByBuildingName("WorldTradeCentre")
        then:""
            buildingInstance != null
            buildingInstance.buildingName ==  "WorldTradeCentre"

        when:""
            buildingInstance.rooms = []
            buildingInstance.rooms.add(new Room(roomNo:"A001"))
            buildingInstance.rooms.add(new Room(roomNo:"A002"))
            buildingInstance.rooms.add(new Room(roomNo:"A003"))
            buildingInstance.save(flush: true)
            session.clear()
            buildingInstance = Building.findByBuildingName("WorldTradeCentre")
        then:""
            buildingInstance != null
            buildingInstance.buildingName ==  "WorldTradeCentre"
            buildingInstance.rooms.size() == 3

        when:""
            def sony = new RoomCompany(companyName:"Sony")
            sony.save()
            buildingInstance.rooms.getAt(0).refCompany = sony
            buildingInstance.save(flush: true)
            session.clear()
            buildingInstance = Building.findByBuildingName("WorldTradeCentre")

        then:""
            buildingInstance != null
            buildingInstance.buildingName ==  "WorldTradeCentre"
            buildingInstance.rooms.size() == 3
            buildingInstance.rooms[0].roomNo == "A001"
            buildingInstance.rooms[0].refCompany != null
            buildingInstance.rooms[0].refCompany.companyName == "Sony"

        when:""
            def sharp=  new RoomCompany(companyName:"Sharp")
            sharp.save()
            buildingInstance.rooms.getAt(1).refCompany = sharp
            buildingInstance.save(flush: true)
            buildingInstance = Building.findByBuildingName("WorldTradeCentre")

        then:""
            buildingInstance != null
            buildingInstance.buildingName ==  "WorldTradeCentre"
            buildingInstance.rooms.size() == 3
            buildingInstance.rooms[0].roomNo == "A001"
            buildingInstance.rooms[0].refCompany != null
            buildingInstance.rooms[0].refCompany.companyName == "Sony"
            buildingInstance.rooms[1].roomNo == "A002"
            buildingInstance.rooms[1].refCompany != null
            buildingInstance.rooms[1].refCompany.companyName == "Sharp"

        when:""
            buildingInstance.buildingName = "WorldTradeCentre 2nd"
            buildingInstance.save(flush: true)
            session.clear()
            buildingInstance = Building.findByBuildingName("WorldTradeCentre 2nd")

        then:""
            buildingInstance != null
            buildingInstance.buildingName ==  "WorldTradeCentre 2nd"
            buildingInstance.rooms.size() == 3
            buildingInstance.rooms[0].roomNo == "A001"
            buildingInstance.rooms[0].refCompany != null
            buildingInstance.rooms[0].refCompany.companyName == "Sony"
            buildingInstance.rooms[1].roomNo == "A002"
            buildingInstance.rooms[1].refCompany != null
            buildingInstance.rooms[1].refCompany.companyName == "Sharp"
    }
    @Override
    List getDomainClasses() {
        [Building, Room, RoomCompany]
    }
}
