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
package org.grails.orm.hibernate

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity

class HibernateGormInstanceApiSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([PersonInstanceApi, BookInstanceApi])
    }

    def "test save and get"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)

        when:
        person.save(flush: true)

        then:
        person.id != null

        when:
        def found = PersonInstanceApi.get(person.id)

        then:
        found != null
        found.name == 'Bob'
        found.age == 40
    }

    def "test delete"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)
        def id = person.id

        expect:
        PersonInstanceApi.get(id) != null

        when:
        person.delete(flush: true)

        then:
        PersonInstanceApi.get(id) == null
    }

    def "test isDirty"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)

        when:
        person.name = 'Fred'

        then:
        person.isDirty()
        person.isDirty('name')
        !person.isDirty('age')
        person.getDirtyPropertyNames() == ['name']
    }

    def "test getPersistentValue"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)

        when:
        person.name = 'Fred'

        then:
        person.getPersistentValue('name') == 'Bob'
    }

    def "test discard"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)
        person.name = 'Fred'

        when:
        person.discard()
        
        then:
        !person.isAttached()
        
        when:
        def found = PersonInstanceApi.get(person.id)
        
        then:
        found.name == 'Bob'
    }

    def "test attach and merge"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)
        person.discard()
        
        expect:
        !person.isAttached()
        
        when:
        person.name = 'Fred'
        person = person.attach()
        
        then:
        person.isAttached()
        
        when:
        person.save(flush: true)
        def found = PersonInstanceApi.get(person.id)
        
        then:
        found.name == 'Fred'
    }

    def "merge on new instance assigns id and sets version to 0"() {
        given:
        def person = new PersonInstanceApi(name: 'Alice', age: 30)

        when:
        def merged = person.merge(flush: true)

        then:
        merged.id != null
        person.id == merged.id
        merged.version == 0
    }

    def "merge on detached instance keeps id and increments version"() {
        given:
        def person = new PersonInstanceApi(name: 'Alice', age: 30)
        person.save(flush: true)
        def originalId = person.id
        person.discard()
        person.name = 'Alice Updated'

        when:
        def merged = person.merge(flush: true)

        then:
        merged.id == originalId
        person.id == originalId
        merged.version == 1
        PersonInstanceApi.get(originalId).name == 'Alice Updated'
    }

    def "test insert"() {
        given:
        def person = new PersonInstanceApi(name: 'Joe', age: 25)

        when:
        person.insert(flush: true)

        then:
        person.id != null
        PersonInstanceApi.get(person.id).name == 'Joe'
    }

    def "test refresh"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)
        
        when:
        person.name = 'Fred'
        // name is "Fred" in memory, but "Bob" in DB
        person.refresh()
        
        then:
        person.name == 'Bob'
    }
}

@Entity
class PersonInstanceApi implements HibernateEntity<PersonInstanceApi> {
    String name
    Integer age
}

@Entity
class BookInstanceApi implements HibernateEntity<BookInstanceApi> {
    String title
    static belongsTo = [author: PersonInstanceApi]
}
