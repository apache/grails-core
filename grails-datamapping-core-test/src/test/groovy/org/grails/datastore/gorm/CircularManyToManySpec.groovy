package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class CircularManyToManySpec extends GormDatastoreSpec {

    void "Test that a circular one-to-many persists correctly"() {
        when:"A domain model with circular one-to-manys is created and queried"
            def p1 = new CircularPerson(name: "Fred").save()
            def p2 = new CircularPerson(name: "Bob").save()
            def p3 = new CircularPerson(name: "Joe").save()
            def p4 = new CircularPerson(name: "Homer").save()

            p1.addToFriends(p2)
            p1.addToFriends(p3)

            p1.addToEnemies(p4)

            p1.save(flush: true)

            p2.addToEnemies(p1)

            p2.save(flush: true)
            session.clear()

            p1 = CircularPerson.findByName("Fred")
            p2 = CircularPerson.findByName("Bob")

        then:"The persisted model is correct"
            p1.name == "Fred"
            p1.friends.size() == 2
            p1.friends.find { it.name == 'Bob' }
            p1.friends.find { it.name == 'Joe' }
            p1.enemies.size() == 1
            p1.enemies.find { it.name == 'Homer' }
            p2.enemies.size() == 1
            p2.enemies.find { it.name == 'Fred' }
    }

    @Override
    List getDomainClasses() {
        [CircularPerson]
    }
}

@Entity
class CircularPerson {

    Long id
    String name
    List<CircularPerson> friends = []

    static hasMany = [
        friends: CircularPerson,
        enemies: CircularPerson
    ]

    static mapping = {
        friends joinTable: "person_friends"
        enemies joinTable: "person_enemies"
    }
}
