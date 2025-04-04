package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import grails.persistence.Entity

import org.bson.types.ObjectId

class CustomTypeMarshallingSpec extends GormDatastoreSpec {

    void "Test basic crud with custom types"() {
        given: "A custom type registered for the Birthday class"
            final now = new Date()
            def p = new Person(name:"Fred", birthday: new Birthday(now))
            p.save(flush:true)
            session.clear()

        when: "We query the person"
            p = Person.findByName("Fred")

        then: "The birthday is returned"
            p != null
            p.name == "Fred"
            p.birthday != null

        when: "We query with a custom type"
           p = Person.findByBirthday(new Birthday(now))

        then:
            p != null

        when: "A range query is executed"

            p = Person.findByBirthdayBetween(new Birthday(now-1), new Birthday(now+1))
            def p2 = Person.findByBirthdayBetween(new Birthday(now+1), new Birthday(now+2))

        then:
            p != null
            p2 == null
    }

    @Override
    List getDomainClasses() {
        [Person]
    }
}

@Entity
class Person implements MongoEntity<Person> {
    ObjectId id
    String name
    Birthday birthday
}

class Birthday implements Comparable {
    Date date

    Birthday(Date date) {
        this.date = date
    }

    int compareTo(t) {
        date.compareTo(t.date)
    }
}
