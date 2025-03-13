package grails.gorm.tests.dirtychecking

import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 03/05/2017.
 */
class HibernateDirtyCheckingSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Person)

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10613')
    void    "Test that presence of beforeInsert doesn't impact dirty properties"() {
        given: 'a new person'
        def person = new Person(name: 'John', occupation: 'Grails developer').save(flush:true)

        when: 'the name is changed'
        person.name = 'Dave'

        then: 'the name field is dirty'
        person.getPersistentValue('name') == "John"
        person.dirtyPropertyNames.contains 'name'
        person.dirtyPropertyNames == ['name']
        person.isDirty('name')
        !person.isDirty('occupation')

        when:
        person.save(flush:true)

        then:
        person.getPersistentValue('name') == "Dave"
        person.dirtyPropertyNames == []
        !person.isDirty('name')
        !person.isDirty()

        when:
        person.occupation = "Civil Engineer"

        then:
        person.getPersistentValue('occupation') == "Grails developer"
        person.dirtyPropertyNames.contains 'occupation'
        person.dirtyPropertyNames == ['occupation']
        person.isDirty('occupation')
        !person.isDirty('name')
    }

    @Rollback
    void "test dirty checking on embedded"() {
        given: 'a new person'
        Person person = new Person(name: 'John', occupation: 'Grails developer', address: new Address(street: "Old Town", zip: "1234")).save(flush:true)

        when: 'the name is changed'
        person.address.street = "New Town"

        then:
        person.address.hasChanged()
        person.address.hasChanged("street")

        when:
        person.save(flush:true)

        then:
        !person.address.hasChanged()
        person.address.listDirtyPropertyNames().isEmpty()

        when:
        hibernateDatastore.sessionFactory.currentSession.clear()
        person = Person.first()

        then:
        person.address.street == "New Town"
    }

    @Rollback
    void "test dirty checking on boolean true -> false"() {
        given: 'a new person'
        new Person(name: 'John', occupation: 'Grails developer', employed: true).save(flush: true)
        hibernateDatastore.sessionFactory.currentSession.clear()
        Person person = Person.first()

        when:
        person.employed = false

        then:
        person.getPersistentValue('employed') == true
        person.dirtyPropertyNames == ['employed']
        person.isDirty('employed')

        when:
        person.save(flush:true)
        hibernateDatastore.sessionFactory.currentSession.clear()
        person = Person.first()

        then:
        person.employed == false
    }

    @Rollback
    void "test dirty checking on boolean false -> true"() {
        given: 'a new person'
        new Person(name: 'John', occupation: 'Grails developer', employed: false).save(flush: true)
        hibernateDatastore.sessionFactory.currentSession.clear()
        Person person = Person.first()

        when:
        person.employed = true

        then:
        person.getPersistentValue('employed') == false
        person.dirtyPropertyNames == ['employed']
        person.isDirty('employed')

        when:
        person.save(flush:true)
        hibernateDatastore.sessionFactory.currentSession.clear()
        person = Person.first()

        then:
        person.employed == true
    }

}


@Entity
class Person {

    String name
    String occupation
    boolean employed

    Address address
    static embedded = ['address']

    static constraints = {
        address nullable:true
    }

    def beforeInsert() {
        // Do nothing
    }
}

@DirtyCheck
class Address {
    String street
    String zip
}