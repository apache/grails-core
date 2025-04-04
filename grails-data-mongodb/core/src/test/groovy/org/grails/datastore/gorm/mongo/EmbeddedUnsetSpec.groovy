package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import spock.lang.Issue

class EmbeddedUnsetSpec extends GormDatastoreSpec {

    @Issue('https://github.com/grails/grails-data-mapping/issues/718')
    void "Test unset value from embedded collection"() {
        given:
        EmbeddedPetOwner o = new EmbeddedPetOwner(name:"bob", pets:[new EmbeddedPet(name:"fido")])
        o.save(flush:true)

        session.clear()

        when:
        EmbeddedPetOwner o2 = EmbeddedPetOwner.findByName("bob")

        then:
        o2.pets[0].name == "fido"

        when:
        o2.pets[0].name = null
//        o2.markDirty('pets')
        o2.save(flush:true)
        then:
        !o2.hasErrors()
        o2.pets[0].name == null

        session.clear()
        when:
        EmbeddedPetOwner o3 = EmbeddedPetOwner.findByName("bob")
        then:
        o3.pets[0].name == null
    }
    @Override
    List getDomainClasses() {
        [EmbeddedPetOwner, EmbeddedPet]
    }
}

@Entity
class EmbeddedPetOwner {
    String name

    List<EmbeddedPet> pets
    static embedded = ['pets']
}
@Entity
class EmbeddedPet {
    String name
    static constraints = {
        name nullable:true
    }
}