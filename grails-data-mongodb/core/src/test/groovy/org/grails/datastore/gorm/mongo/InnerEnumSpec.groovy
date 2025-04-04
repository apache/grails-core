package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

/**
 * Created by graemerocher on 05/01/16.
 */
class InnerEnumSpec extends GormDatastoreSpec {

    void "Test that inner enums are persisted"() {
        given:
        new InnerPerson(name: "Fred", state: InnerPerson.PersonState.GOOD).save(flush:true)
        session.clear()

        expect:
            InnerPerson.first().state == InnerPerson.PersonState.GOOD
    }

    @Override
    List getDomainClasses() {
        [InnerPerson]
    }
}

@Entity
class InnerPerson {
    String name
    PersonState state

    static constraints = {
    }

    enum PersonState {
        GOOD, BAD
    }
}