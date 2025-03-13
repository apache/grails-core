package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import com.mongodb.DBRef
import com.mongodb.DBObject
import org.bson.types.ObjectId

/**
 *
 */
class EmbeddedWithNonEmbeddedCollectionsSpec extends GormDatastoreSpec{
    @Override
    List getDomainClasses() {
        [Ship, Crew, Sailor, Captain]
    }

    void "Test that embedded collections can have non-embedded collections"() {
        given:"A domain model with embedded associations that have non-embedded collections"
        final captain = new Captain(name: "Bob")
        final firstMate = new Sailor(name:"Jim", captain:captain)
        def ship = new Ship(name:"The Float")
        ship.crew.firstMate = firstMate
        ship.crew.sailors << new Sailor(name:"Fred", captain:captain)
        ship.crew.sailors << new Sailor(name:"Joe", captain:captain)
        ship.crew.reserves << new Sailor(name:"Tristan", captain:captain)
        ship.crew.reserves << new Sailor(name:"Roger", captain:captain)
        captain.shipmates << new Sailor(name:"Jeff", captain: captain)
        captain.save flush: true,validate:false
        ship.save flush:true,validate:false
        session.clear()

        when:"The underlying Mongo document is queried"
        def shipDbo = Ship.collection.find().first()
        Sailor fred = Sailor.findByName("Fred")
        Sailor joe = Sailor.findByName("Joe")

        then:"It is correctly defined"
        shipDbo.name == "The Float"
        shipDbo.crew != null
        shipDbo.crew.firstMate == firstMate.id
        shipDbo.crew.sailors.size() == 2
        shipDbo.crew.sailors == [fred.id, joe.id]
        shipDbo.crew.reserves.size() == 2
        shipDbo.crew.reserves[0] instanceof Map
        shipDbo.crew.reserves[0].$id == Sailor.findByName('Tristan').id
        shipDbo.crew.reserves[0].$ref == 'sailor'

        when:"The domain model is queried"
        session.clear()
        ship =  Ship.get(ship.id)

        then:"The right results are returned"
        ship != null
        ship.name == "The Float"
        ship.crew != null
        ship.crew.sailors.size() == 2
        ship.crew.sailors[0].name == 'Fred'
        ship.crew.sailors[0].captain != null
        ship.crew.sailors[0].captain.name == 'Bob'
        ship.crew.reserves.size() == 2
        ship.crew.reserves[1].name == 'Roger'
        ship.crew.firstMate != null
        ship.crew.firstMate.name == 'Jim'
        Sailor.count() == 6
        Ship.count() == 1
    }
}

@Entity
class Ship implements MongoEntity<Ship> {
    String id
    String name
    Crew crew = new Crew()
    static embedded = ['crew']
}

@Entity
class Crew implements MongoEntity<Ship>{
    String id
    String name
    Sailor firstMate
    List<Sailor> sailors = []
    List<Sailor> reserves = []

    static hasMany = [
            sailors:Sailor,
            reserves:Sailor
    ]

    static mapping = {
        reserves reference: true
    }
}
