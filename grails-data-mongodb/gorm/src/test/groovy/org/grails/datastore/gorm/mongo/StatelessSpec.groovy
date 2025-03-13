package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec

class StatelessSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Volcano]
    }

    void "stateless and self-assigned ids can be used together"() {
        given:
        Volcano v = new Volcano(country: "Spain")
        v.id = "Teide"
        v.insert flush: true
        session.clear()

        when:
        v = Volcano.get("Teide")

        then:
        v.id == "Teide"
        v.country == "Spain"

        when:
        session.clear()
        v = Volcano.get("Teide")
        v.country = 'España'
        v.save flush: true
        session.clear()
        v = Volcano.get("Teide")

        then:
        v.id == "Teide"
        v.country == "España"
    }


}


