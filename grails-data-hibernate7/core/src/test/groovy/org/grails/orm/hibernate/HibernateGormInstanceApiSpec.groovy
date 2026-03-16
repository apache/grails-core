/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

// HibernateGormInstanceApiSpec.groovy
package org.grails.orm.hibernate

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.springframework.validation.Errors

class HibernateGormInstanceApiSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HibernateGormInstanceApiSpecPerson, HibernateGormInstanceApiSpecJob])
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
        person.delete(flush: true)
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

    void "test save with validate:false skips validation"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "Betty", lastName: "Rubble", age: 36)
        when:
        def result = person.save(validate: false)
        then:
        result != null
        result.id != null
    }

    void "test save with validate:true explicit"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "Betty", lastName: "Rubble", age: 36)
        when:
        def result = person.save(validate: true)
        then:
        result != null
    }

    void "test save with deepValidate:false"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "Betty", lastName: "Rubble", age: 36)
        when:
        def result = person.save(deepValidate: false)
        then:
        result != null
        result.id != null
    }

    void "test save with explicit flush:false"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "Betty", lastName: "Rubble", age: 36)
        when:
        def result = person.save(flush: false)
        then:
        result != null
    }

    void "test merge with params"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Fred")
        person.lastName = "Smith"
        when:
        def merged = person.merge(flush: true)
        then:
        merged.lastName == "Smith"
    }

    void "test isDirty returns false for new unsaved instance"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "New", lastName: "Person", age: 25)
        expect:
        !person.isDirty()
    }

    void "test isDirty(field) returns false for new unsaved instance"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "New", lastName: "Person", age: 25)
        expect:
        !person.isDirty("firstName")
    }

    void "test isDirty(field) returns false for field that has not changed"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Fred")
        person.lastName = "Changed"
        expect:
        !person.isDirty("firstName")
        person.isDirty("lastName")
    }

    void "test getDirtyPropertyNames returns empty list for new unsaved instance"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "New", lastName: "Person", age: 25)
        expect:
        person.getDirtyPropertyNames() == []
    }

    void "test getPersistentValue returns null for new unsaved instance"() {
        given:
        def person = new HibernateGormInstanceApiSpecPerson(firstName: "New", lastName: "Person", age: 25)
        expect:
        person.getPersistentValue("firstName") == null
    }

    void "test getPersistentValue returns null for unknown field name"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Fred")
        expect:
        person.getPersistentValue("nonExistentField") == null
    }

    void "test save succeeds on entity with ToOne association already in session"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Fred")
        def job = new HibernateGormInstanceApiSpecJob(title: "Programmer", person: person)
        when:
        def result = job.save(flush: true)
        then:
        result != null
        result.id != null
    }

    void "test save failure on entity with ToOne association calls handleValidationError"() {
        given:
        def person = HibernateGormInstanceApiSpecPerson.findByFirstName("Fred")
        def job = new HibernateGormInstanceApiSpecJob(title: null, person: person)
        when:
        def result = job.save()
        then:
        result == null
        job.errors.hasErrors()
    }
}

@Entity
class HibernateGormInstanceApiSpecJob {
    String title
    HibernateGormInstanceApiSpecPerson person

    static belongsTo = [person: HibernateGormInstanceApiSpecPerson]

    static constraints = {
        title nullable: false
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