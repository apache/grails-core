package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import org.bson.types.ObjectId
import grails.persistence.Entity

/**
 */
class ClearCollectionSpec extends GormDatastoreSpec{

    void "Test clear embedded mongo collection"() {
        given:"An entity with an embedded collection"
            Building  b = new Building(buildingName: "WTC", rooms: [new Room(roomNo: 1),new Room(roomNo: 1)]).save(flush:true, validate:false)
            session.clear()

        when:"The entity is queried"
            b = Building.get(b.id)

        then:"The entity was persisted correctly"
            b.buildingName == "WTC"
            b.rooms.size() == 2
            b.rooms[0].roomNo == "1"

        when:"The association is cleared"
            b.rooms.clear()
            b.save(flush: true)
            session.clear()
            b = Building.get(b.id)

        then:"It is empty"
            b.rooms.size() == 0

    }

    @Override
    List getDomainClasses() {
        [Building, Room, RoomCompany]
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
        rooms(blank:true,nullable:true)
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
