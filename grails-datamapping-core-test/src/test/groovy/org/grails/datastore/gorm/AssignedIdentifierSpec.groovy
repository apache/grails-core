package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

/**
 * Tests for usage of assigned identifiers
 */
class AssignedIdentifierSpec extends GormDatastoreSpec{

    void "Test that entities can be saved, retrieved and updated with assigned ids"() {
        when:"An entity is saved with an assigned id"
            def r = new River(name:"Amazon", country: "Brazil")
            r.save flush:true
            session.clear()
            r = River.get("Amazon")

        then:"The entity can be retrieved"
            r != null
            r.name == "Amazon"
            r.country == "Brazil"

        when:"The entity is updated"
            r.country = "Argentina"
            r.save flush:true
            session.clear()
            r = River.get("Amazon")

        then:"The update is applied"
            r != null
            r.name == "Amazon"
            r.country == "Argentina"

        when:"The entity is deleted"
            r.delete(flush:true)

        then:"It is gone"
            River.count() == 0
            River.get("Amazon") == null
    }

    @Override
    List getDomainClasses() {
        [River]
    }
}

@Entity
class River {
    String name
    String country
    static mapping = {
        id name:'name', generator:'assigned'
    }
}
