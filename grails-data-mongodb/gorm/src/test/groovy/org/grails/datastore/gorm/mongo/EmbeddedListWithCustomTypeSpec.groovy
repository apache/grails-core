package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import org.bson.types.ObjectId
import grails.persistence.Entity
import spock.lang.Issue

/**
 */
class EmbeddedListWithCustomTypeSpec extends GormDatastoreSpec{

    @Issue('GPMONGODB-217')
    void "Test that custom types in an embedded list persist correctly"() {
        given:"An entity with a custom type property"
            final birthdate = new Date()
            def joan = new Person (name: 'joan', birthday: new Birthday(birthdate))

        when:"The person is persisted inside an embedded collection"
            def black = new Family(name: 'black', members: [joan])
            black.save(flush:true)
            session.clear()
            black = Family.findByName('black')

        then:"Custom type is persisted correctly"
            black != null
            black.members.size() == 1
            black.members[0].name == 'joan'
            black.members[0].birthday != null
            black.members[0].birthday.date == birthdate
    }

    @Override
    List getDomainClasses() {
        [Person,Family]
    }
}

@Entity
class Family {
    ObjectId id
    String name
    List<Person> members
    static hasMany = [members: Person]
//    static embedded = ['members']
}
