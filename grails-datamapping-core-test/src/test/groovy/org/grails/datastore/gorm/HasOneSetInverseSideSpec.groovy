package org.grails.datastore.gorm

import grails.persistence.Entity
import spock.lang.Issue
import grails.gorm.tests.GormDatastoreSpec

class HasOneSetInverseSideSpec extends GormDatastoreSpec{

    @Issue('GRAILS-8757')
    void "Test that saving a one-to-one automatically sets the inverse side"() {
        when:"A bidirectional one-to-one is saved"
            def address = new HouseAddress(street:"Street 001")
            def house = new House(name:"Some house", address: address)

            house.save(flush:true)

        then:"The inverse side is autmotically set"
            house.id != null
            address.house != null

        when:"The association is queried"
            session.clear()
            house = House.get(house.id)

        then:"The data model is valid"
            house.id != null
            house.address != null
            house.address.house != null
    }

    @Override
    List getDomainClasses() {
        [House, HouseAddress]
    }
}

@Entity
class House {
    Long id
    String name

    HouseAddress address
    static hasOne = [address: HouseAddress]
}

@Entity
class HouseAddress {
    Long id
    String street
    House house
}
