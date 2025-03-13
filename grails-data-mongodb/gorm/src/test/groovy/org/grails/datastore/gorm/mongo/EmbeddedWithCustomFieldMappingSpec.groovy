package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class EmbeddedWithCustomFieldMappingSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [EWCFMPerson, EWCFMPet]
    }

    void "Test that embedded collections map to the correct underlying attributes"() {
        when:"An entity with custom attribute mappings is persisted"
            def p = new EWCFMPerson(groupId:1)
            p.pets << new EWCFMPet(name:"Fred")
            p.save(flush:true)
            session.clear()

            p = EWCFMPerson.get(p.id)

        then:"The data can be correctly read"
            p != null
            p.pets.size() == 1
    }
}

@Entity
class EWCFMPerson {
    String id
    Integer groupId
    List<EWCFMPet> pets = []

    static mapWith = "mongo"

    static embedded = ['pets']

    static mapping = {
        collection 'persons'
        groupId field: 'gid'
        pets field: 'ps'
    }
}

@Entity
class EWCFMPet {
    static mapWith = "mongo"
    String id
    String name
}
