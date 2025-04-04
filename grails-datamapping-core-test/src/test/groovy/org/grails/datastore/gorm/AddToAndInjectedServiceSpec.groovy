package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.Issue

class AddToAndInjectedServiceSpec extends GormDatastoreSpec {

    @Issue('GRAILS-9119')
    void "Test add to method with injected service present"() {
        given:"A domain with an addTo relationship"
            def pirate = new Pirate(name: 'Billy')
            def ship = new Ship()
        when:"The addTo method is called"
            ship.addToPirates(pirate)

        then:"It adds an associated entity correctly"
            assert 1 == ship.pirates.size()
    }

    @Override
    List getDomainClasses() {
        [Pirate, Ship]
    }
}

@Entity
class Pirate {
    Long id
    String name
    def pirateShipService
}

@Entity
class Ship {
    Long id
    Set pirates
    static hasMany = [pirates: Pirate]
}
