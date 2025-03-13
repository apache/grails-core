package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

/**
 * Tests the use of embedded collections in inheritance hierarchies.
 */
class EmbeddedCollectionAndInheritanceSpec extends GormDatastoreSpec {

    def "Test read and write embedded collection inherited from parent"() {
        when:"A embedded subclass entity is added to a collection"
            def p = new ECAISPerson()
            p.pets << new ECAISDog(name:"Joe",anotherField:"foo")
            p.save(flush:true)
            session.clear()

            p = ECAISPerson.get(p.id)

        then:"The dog is persisted correctly"
            p != null
            p.pets.size() == 1
            p.pets[0] instanceof ECAISDog
            p.pets[0].anotherField == 'foo'

        when:"An embedded subclass entity is updated in the collection"
            p.pets << new ECAISDog(name:"Fred", anotherField: 'bar')
            p.save(flush:true)
            session.clear()
            p = ECAISPerson.get(p.id)

        then:"The dogs are persisted correctly"
            p != null
            p.pets.size() == 2
            p.pets[0] instanceof ECAISDog
            p.pets[0].name == "Joe"
            p.pets[0].anotherField == 'foo'
            p.pets[1] instanceof ECAISDog
            p.pets[1].name == "Fred"
            p.pets[1].anotherField == 'bar'

        when:"An update is made to an embedded collection entry but not the collection itself"
            p.pets[0].name = "Changed"
            p.pets[0].anotherField = "ChangedAnotherField"
            p.save(flush:true)
            session.clear()
            p = ECAISPerson.get(p.id)

        then:"The update is correctly applied"
            p != null
            p.pets.size() == 2
            p.pets[0] instanceof ECAISDog
            p.pets[0].name == "Changed"
            p.pets[0].anotherField == "ChangedAnotherField"
            p.pets[1] instanceof ECAISDog
            p.pets[1].name == "Fred"
            p.pets[1].anotherField == 'bar'

        when:"An embedded entity is removed from a collection"
            p.pets.remove(0)
            p.save(flush:true)
            session.clear()
            p = ECAISPerson.get(p.id)

        then:"The update is correctly applied"
            p != null
            p.pets.size() == 1
            p.pets[0] instanceof ECAISDog
            p.pets[0].name == "Fred"
            p.pets[0].anotherField == 'bar'
    }

    @Override
    List getDomainClasses() {
        [ECAISPerson, ECAISPet, ECAISDog]
    }
}

@Entity
class ECAISPerson {
   String id
   List<ECAISPet>pets = []
   static hasMany = [pets:ECAISPet]
   static embedded = ['pets']
}

@Entity
class ECAISPet {
   String id
   String name
}

@Entity
class ECAISDog extends ECAISPet {
    String id
    String name
    String anotherField
}
