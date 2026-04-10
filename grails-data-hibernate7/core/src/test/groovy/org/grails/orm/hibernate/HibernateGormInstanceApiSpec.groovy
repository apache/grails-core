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
        manager.addAllDomainClasses([PersonInstanceApi, BookInstanceApi, ConstrainedPerson])
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

    // -------------------------------------------------------------------------
    // lock
    // -------------------------------------------------------------------------

    def "lock acquires a pessimistic write lock on the entity"() {
        given:
        Long savedId = PersonInstanceApi.withTransaction {
            new PersonInstanceApi(name: 'LockUser', age: 22).save(flush: true, failOnError: true)
        }.id

        when:
        PersonInstanceApi.withTransaction {
            def person = PersonInstanceApi.get(savedId)
            person.lock()
        }

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // save — shouldValidate/deepValidate/shouldFail branches
    // -------------------------------------------------------------------------

    def "save with validate:false skips validation"() {
        given:
        def person = new ConstrainedPerson(name: '')  // blank name violates constraint

        when:
        def result = ConstrainedPerson.withTransaction {
            person.save(validate: false, flush: true)
        }

        then: "saved without validation — blank name accepted"
        result != null
        result.id != null
    }

    def "save with deepValidate:false still runs validator without deep cascade"() {
        given:
        def person = new ConstrainedPerson(name: 'Alice')

        when:
        def result = ConstrainedPerson.withTransaction {
            person.save(deepValidate: false, flush: true)
        }

        then:
        result != null
        result.id != null
    }

    def "save with invalid entity returns null and sets errors when failOnError is false"() {
        given:
        def person = new ConstrainedPerson(name: '')  // blank violates constraint

        when:
        def result = ConstrainedPerson.withTransaction {
            person.save(flush: true)
        }

        then:
        result == null
        person.hasErrors()
        person.errors.fieldErrors.any { it.field == 'name' }
    }

    def "save with invalid entity and failOnError:true throws an exception"() {
        given:
        def person = new ConstrainedPerson(name: '')

        when:
        ConstrainedPerson.withTransaction {
            person.save(failOnError: true, flush: true)
        }

        then:
        thrown(Exception)
    }

    // -------------------------------------------------------------------------
    // shouldFlush — autoFlush=false (no flush arg)
    // -------------------------------------------------------------------------

    def "save without flush argument uses autoFlush setting"() {
        given: "autoFlush is false by default in the test datastore"
        def person = new PersonInstanceApi(name: 'AutoFlushTest', age: 55)

        when:
        PersonInstanceApi.withTransaction {
            person.save()  // no flush: argument — relies on autoFlush
            session.flush() // flush manually so we can verify
        }

        then:
        person.id != null
        PersonInstanceApi.get(person.id)?.name == 'AutoFlushTest'
    }

    // -------------------------------------------------------------------------
    // isDirty edge cases
    // -------------------------------------------------------------------------

    def "isDirty returns false for a non-attached (transient) instance"() {
        given:
        def person = new PersonInstanceApi(name: 'Transient', age: 10)
        // not saved — no EntityEntry in the session

        expect:
        !person.isDirty()
        !person.isDirty('name')
    }

    def "getDirtyPropertyNames returns empty list for a non-attached instance"() {
        given:
        def person = new PersonInstanceApi(name: 'Ghost', age: 99)

        expect:
        person.getDirtyPropertyNames() == []
    }

    // -------------------------------------------------------------------------
    // getPersistentValue edge cases
    // -------------------------------------------------------------------------

    def "getPersistentValue returns null for an unknown field name"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)

        expect:
        person.getPersistentValue('nonExistentField') == null
    }

    def "getPersistentValue returns null for a non-attached instance"() {
        given:
        def person = new PersonInstanceApi(name: 'Detached', age: 5)
        // not saved

        expect:
        person.getPersistentValue('name') == null
    }

    // -------------------------------------------------------------------------
    // autoRetrieveAssociations — sub-branches via BookInstanceApi
    // -------------------------------------------------------------------------

    def "save book with null author skips association retrieval (null propValue branch)"() {
        given:
        def book = new BookInstanceApi(title: 'Orphan Book')  // no author

        when:
        def result = BookInstanceApi.withTransaction {
            book.save(flush: true, validate: false)
        }

        then:
        result != null
        result.id != null
    }

    def "save book with already-managed author skips re-retrieval (contains branch)"() {
        given:
        def author = PersonInstanceApi.withTransaction {
            new PersonInstanceApi(name: 'Managed Author', age: 35).save(flush: true, failOnError: true)
        }
        // author is still in session after save — template.contains returns true
        def book = new BookInstanceApi(title: 'Managed Book', author: author)

        when:
        def result = BookInstanceApi.withTransaction {
            book.save(flush: true, validate: false)
        }

        then:
        result != null
        result.id != null
    }

    def "save book with detached author triggers association re-retrieval (full fetch path)"() {
        given:
        def author = PersonInstanceApi.withTransaction {
            new PersonInstanceApi(name: 'Detached Author', age: 42).save(flush: true, failOnError: true)
        }
        // Evict the author via the hibernate template so it is no longer in the session
        manager.hibernateDatastore.hibernateTemplate.evict(author)

        def book = new BookInstanceApi(title: 'Fetched Book', author: author)

        when:
        def result = BookInstanceApi.withTransaction {
            book.save(flush: true, validate: false)
        }

        then:
        result != null
        result.id != null
        result.author != null
        result.author.name == 'Detached Author'
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

@Entity
class ConstrainedPerson implements HibernateEntity<ConstrainedPerson> {
    String name
    static constraints = {
        name blank: false, maxSize: 100
    }
}
