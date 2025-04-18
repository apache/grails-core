package grails.gorm.tests

import grails.gorm.annotation.Entity
import spock.lang.Issue

/**
 * Created by graemerocher on 15/08/2017.
 */
class OrphanDeleteSpec extends GormDatastoreSpec {


    @Issue('https://github.com/grails/gorm-neo4j/issues/6')
    void "test cascade delete orphan results in removing orphaned nodes"() {
        when:
        new Contact()
                .addToPhones(phoneNumber: "1234")
                .addToPhones(phoneNumber: "4567")
                .save(flush:true)

        then:
        Contact.count == 1
        Phone.count == 2

        when:"The child is removed from the parent"
        Contact c = Contact.first()
        Phone phone = c.phones.find() { it.phoneNumber == '1234'}
        c.phones.remove(phone)
        c.save(flush:true)
        session.clear()

        then:"The child was also deleted"
        Contact.count == 1
        Contact.first().phones.size() == 1
        Contact.first().phones.first().phoneNumber == '4567'
        Phone.count == 1
    }

    @Override
    List getDomainClasses() {
        [Contact, Phone]
    }
}

@Entity
class Contact {
    static hasMany = [phones: Phone]
    static mapping = {
        phones cascade: "all-delete-orphan"
    }
}

@Entity
class Phone {
    String phoneNumber

    static belongsTo = Person
}