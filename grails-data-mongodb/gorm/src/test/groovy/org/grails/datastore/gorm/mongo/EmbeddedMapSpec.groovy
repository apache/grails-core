package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

/**
 * Created by graemerocher on 20/04/16.
 */
class EmbeddedMapSpec extends GormDatastoreSpec{

    @Issue('https://github.com/grails/grails-data-mapping/issues/691')
    void "Test that persisting and loading an embedded map works as expected"() {
        when:"An entity with a map is persisted"
        new EmbeddedMapPerson(name: "John Doe",
                moreinfo: [
                        qualifications: ["graduation": "B-Tech.", "undergraduate": "MS"],
                        experience: [company1:"TO THE NEW Digital"]
                ]).save(flush:true)

        session.clear()
        EmbeddedMapPerson p = EmbeddedMapPerson.first()

        then:"The entity can be retrieved correctly"
        p != null
        p.moreinfo
        p.moreinfo.qualifications == ["graduation": "B-Tech.", "undergraduate": "MS"]
        p.moreinfo.experience == [company1:"TO THE NEW Digital"]
    }

    @Override
    List getDomainClasses() {
        [EmbeddedMapPerson]
    }
}

@Entity
class EmbeddedMapPerson {
    String name
    Map moreinfo

    static constraints = {
    }

    static embedded = ['moreinfo']
}