package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import spock.lang.Ignore

@Ignore // moved to 'gorm-ex
class DBObjectConversionSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Boat, Sailor, Captain]
    }

    void "Test that it is possible to convert DBObjects to GORM entities"() {
        given:"A domain model with embedded associations that have non-embedded associations"
            createCrew()

        when:"A DBObject is read then converted to an entity"

        def doc = Boat.collection.find().first()
        Boat boat = doc as Boat

        then:"The results are returned correctly"
            boat != null
            boat.name == "The Float"
            boat.captain != null
            boat.captain.name == 'Bob'
            boat.crew.size() == 2
            boat.crew[0].name == 'Fred'
            boat.crew[0].captain != null
            boat.crew[0].captain.name == 'Bob'
            boat.captain.shipmates.size() == 1
            Captain.count() == 1
            Sailor.count() == 1
            Boat.count() == 1
    }

    void "Test that it is possible to convert DBCursors to GORM entities"() {
        given:"A domain model with embedded associations that have non-embedded associations"
            createCrew()

        when:"A DBCursor is read then converted to an entity"
            Boat boat = Boat.collection.find() as Boat

        then:"The results are returned correctly"
            boat != null
            boat.name == "The Float"
            boat.captain != null
            boat.captain.name == 'Bob'
            boat.crew.size() == 2
            boat.crew[0].name == 'Fred'
            boat.crew[0].captain != null
            boat.crew[0].captain.name == 'Bob'
            boat.captain.shipmates.size() == 1
            Captain.count() == 1
            Sailor.count() == 1
            Boat.count() == 1
    }

    @Ignore
    void "Test that it is possible to convert DBCursors to a list of GORM entities"() {
        given:"A domain model with embedded associations that have non-embedded associations"
            createCrew()

        when:"A DBCursor is read then converted to an entity"

            List boats = Boat.withNewSession { Boat.collection.find().toList(Boat) }

        then:"The results are returned correctly"
            boats != null
            boats.size() == 1
            def boat = boats.get(0)
            boat != null
            boat.name == "The Float"
            boat.captain != null
            boat.captain.name == 'Bob'
            boat.crew.size() == 2
            boat.crew[0].name == 'Fred'
            boat.crew[0].captain != null
            boat.crew[0].captain.name == 'Bob'
            boat.captain.shipmates.size() == 1
            Captain.count() == 1
            Sailor.count() == 1
            Boat.count() == 1
    }

    void "Test that an entity can be round-tripped to dbo and back"() {
        given:"A domain model with embedded associations that have non-embedded associations"
        createCrew()

        when:"The model is converted to a dbo and back"
        Boat boat = Boat.findAll().get(0).dbo as Boat

        then:"The copy matches the original"
        boat != null
        boat.name == "The Float"
        boat.captain != null
        boat.captain.name == 'Bob'
        boat.crew.size() == 2
        boat.crew[0].name == 'Fred'
        boat.crew[0].captain != null
        boat.crew[0].captain.name == 'Bob'
        boat.captain.shipmates.size() == 1

        when:"The association is updated"
        boat.crew.pop()
        boat.save(flush: true)
        boat = boat.dbo as Boat

        then:"The model is correct"
        boat != null
        boat.name == "The Float"
        boat.captain != null
        boat.captain.name == 'Bob'
        boat.crew.size() == 1
        boat.crew[0].name == 'Fred'
        boat.crew[0].captain != null
        boat.crew[0].captain.name == 'Bob'
        boat.captain.shipmates.size() == 1
    }

    private createCrew() {
        final captain = new Captain(name: "Bob")
        final firstMate = new Sailor(name: "Jim", captain: captain)
        def boat = new Boat(name: "The Float", captain: captain, firstMate: firstMate)
        boat.crew << new Sailor(name: "Fred", captain: captain)
        boat.crew << new Sailor(name: "Joe", captain: captain)
        captain.shipmates << new Sailor(name: "Jeff", captain: captain)
        captain.save()
        boat.save flush: true
        session.clear()
    }
}
