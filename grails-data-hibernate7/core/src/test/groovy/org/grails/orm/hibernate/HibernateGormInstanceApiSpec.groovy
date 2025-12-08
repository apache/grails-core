// HibernateGormInstanceApiSpec.groovy
package org.grails.orm.hibernate

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.specs.entities.Club
import grails.persistence.Entity
import org.springframework.validation.Errors

class HibernateGormInstanceApiSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HibernateGormInstanceApiSpecPerson])
    }


    def setup() {
        new HibernateGormInstanceApiSpecPerson(firstName: "Fred", lastName: "Flintstone", age: 40).save(flush: true)
        new HibernateGormInstanceApiSpecPerson(firstName: "Wilma", lastName: "Flintstone", age: 35).save(flush: true)
    }

    void "test save with validation success"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "Barney", lastName: "Rubble", age: 38)
        when:
        def result = person.save()
        then:
        result != null
        result.id != null
    }

    void "test save with validation failure"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: null, lastName: "Rubble", age: 38)
        when:
        def result = person.save()
        then:
        result == null
        person.errors.hasErrors()
    }

    void "test save with failOnError"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: null, lastName: "Rubble", age: 38)
        when:
        person.save(failOnError: true)
        then:
        thrown(Exception)
    }

    void "test merge"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Fred")
        person.lastName = "Smith"
        when:
        def merged = person.merge()
        then:
        merged.lastName == "Smith"
    }

    void "test insert"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "Betty", lastName: "Rubble", age: 36)
        when:
        def inserted = person.insert()
        then:
        inserted.id != null
    }

    void "test discard"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Wilma")
        when:
        person.discard()
        then:
        !person.isAttached()
    }

    void "test delete"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Fred")
        when:
        person.remove(flush: true)
        then:
        HibernateGormInstanceApiSpecPerson.findByFirstName("Fred") == null
    }

    void "test isAttached"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Wilma")
        expect:
        person.isAttached()
    }

    void "test lock and attach"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Wilma")
        when:
        def locked = person.lock()
        def attached = person.attach()
        then:
        locked == person
        attached == person
    }

    void "test refresh"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Wilma")
        person.lastName = "Changed"
        when:
        person.refresh()
        then:
        person.lastName == "Flintstone"
    }

    void "test isDirty for field"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Wilma")
        person.lastName = "Changed"
        expect:
        person.isDirty("lastName")
    }

    void "test isDirty for instance"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Wilma")
        person.lastName = "Changed"
        expect:
        person.isDirty()
    }

    void "test getDirtyPropertyNames"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Wilma")
        person.lastName = "Changed"
        when:
        def dirtyNames = person.getDirtyPropertyNames()
        then:
        dirtyNames.contains("lastName")
    }

    void "test getPersistentValue"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Wilma")
        def originalLastName = person.lastName
        person.lastName = "Changed"
        when:
        def persistedValue = person.getPersistentValue("lastName")
        then:
        persistedValue == originalLastName
    }

    void "test handleValidationError sets errors"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: null, lastName: "Rubble", age: 38)
        person.save()
        expect:
        person.errors instanceof Errors
        person.errors.hasErrors()
    }
}

@Entity
class HibernateGormInstanceApiSpecPerson {
    String firstName
    String lastName
    Integer age

    static constraints = {
        firstName nullable: false
        age min: 0
    }
}