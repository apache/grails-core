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
package grails.gorm.specs.dirtychecking

import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import spock.lang.Issue

/**
 * Created by graemerocher on 03/05/2017.
 */
class HibernateDirtyCheckingSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([Person])
    }

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10613')
    void "Test that presence of beforeInsert doesn't impact dirty properties"() {
        given: 'a new person'
        def person = new Person(name: 'John', occupation: 'Grails developer').save(flush: true)

        when: 'the name is changed'
        person.name = 'Dave'

        then: 'the name field is dirty'
        person.getPersistentValue('name') == "John"
        person.dirtyPropertyNames.contains 'name'
        person.dirtyPropertyNames == ['name']
        person.isDirty('name')
        !person.isDirty('occupation')

        when:
        person.save(flush: true)

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
    //TODO Embedded class not working
    void "test dirty checking on embedded"() {
        given: 'a new person'
        Person person = new Person(name: 'John', occupation: 'Grails developer', address: new Address(street: "Old Town", zip: "1234")).save(flush: true)

        when: 'the name is changed'
        person.address.street = "New Town"

        then:
        person.address.hasChanged()
        person.address.hasChanged("street")

        when:
        person.save(flush: true)

        then:
        !person.address.hasChanged()
        person.address.listDirtyPropertyNames().isEmpty()

        when:
        manager.hibernateDatastore.sessionFactory.currentSession.clear()
        person = Person.first()

        then:
        person.address.street == "New Town"
    }

    @Rollback
    void "test dirty checking on boolean true -> false"() {
        given: 'a new person'
        new Person(name: 'John', occupation: 'Grails developer', employed: true).save(flush: true)
        manager.hibernateDatastore.sessionFactory.currentSession.clear()
        Person person = Person.first()

        when:
        person.employed = false

        then:
        person.getPersistentValue('employed') == true
        person.dirtyPropertyNames == ['employed']
        person.isDirty('employed')

        when:
        person.save(flush: true)
        manager.hibernateDatastore.sessionFactory.currentSession.clear()
        person = Person.first()

        then:
        person.employed == false
    }

    @Rollback
    void "test dirty checking on boolean false -> true"() {
        given: 'a new person'
        new Person(name: 'John', occupation: 'Grails developer', employed: false).save(flush: true)
        manager.hibernateDatastore.sessionFactory.currentSession.clear()
        Person person = Person.first()

        when:
        person.employed = true

        then:
        person.getPersistentValue('employed') == false
        person.dirtyPropertyNames == ['employed']
        person.isDirty('employed')

        when:
        person.save(flush: true)
        manager.hibernateDatastore.sessionFactory.currentSession.clear()
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
        address nullable: true
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