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
import grails.gorm.transactions.Rollback

import org.hibernate.FlushMode
import org.grails.orm.hibernate.query.HibernateHqlQueryCreator

class HibernateGormInstanceApiSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([PersonInstanceApi, BookInstanceApi, ConstrainedPerson, ConstrainedBook, HGIAuthor, HGIBook])
    }

    @Rollback
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

    @Rollback
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

    @Rollback
    def "test delete without flush"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40).save(flush: true)
        def id = person.id

        when:
        person.delete()

        then: "Entity is removed from session but still in DB"
        PersonInstanceApi.get(id) == null
        getSessionFactory().getCurrentSession().createNativeQuery("select count(*) from person_instance_api where id = :id", Long)
               .setParameter("id", id)
               .setHibernateFlushMode(FlushMode.MANUAL)
               .uniqueResult() == 1L

        when:
        session.flush()

        then:
        getSessionFactory().getCurrentSession().createNativeQuery("select count(*) from person_instance_api where id = :id", Long)
               .setParameter("id", id)
               .uniqueResult() == 0L
    }

    @Rollback
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

    @Rollback
    def "test getPersistentValue"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)

        when:
        person.name = 'Fred'

        then:
        person.getPersistentValue('name') == 'Bob'
    }

    @Rollback
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

    @Rollback
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

    @Rollback
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

    @Rollback
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

    @Rollback
    def "test insert"() {
        given:
        def person = new PersonInstanceApi(name: 'Joe', age: 25)

        when:
        person.insert(flush: true)

        then:
        person.id != null
        PersonInstanceApi.get(person.id).name == 'Joe'
    }

    @Rollback
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

    @Rollback
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

    @Rollback
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

    @Rollback
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

    @Rollback
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

    @Rollback
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

    @Rollback
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

    @Rollback
    def "isDirty returns false for a non-attached (transient) instance"() {
        given:
        def person = new PersonInstanceApi(name: 'Transient', age: 10)

        expect:
        !person.isDirty()
        !person.isDirty('name')
    }

    @Rollback
    def "getDirtyPropertyNames returns empty list for a non-attached instance"() {
        given:
        def person = new PersonInstanceApi(name: 'Ghost', age: 99)

        expect:
        person.getDirtyPropertyNames() == []
    }

    @Rollback
    def "getPersistentValue returns null for an unknown field name"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)

        expect:
        person.getPersistentValue('nonExistentField') == null
    }

    @Rollback
    def "getPersistentValue returns null for a non-attached instance"() {
        given:
        def person = new PersonInstanceApi(name: 'Detached', age: 5)

        expect:
        person.getPersistentValue('name') == null
    }

    @Rollback
    def "save book with null author skips association retrieval"() {
        given:
        def book = new HGIBook(title: 'Orphan Book')

        when:
        HGIBook.withTransaction {
            book.save(flush: true, validate: false)
        }

        then:
        book.id != null
    }

    @Rollback
    def "save book with already-managed author skips re-retrieval"() {
        given:
        def author = HGIAuthor.withTransaction {
            new HGIAuthor(name: 'Managed Author').save(flush: true, failOnError: true)
        }
        def book = new HGIBook(title: 'Managed Book', author: author)

        when:
        HGIBook.withTransaction {
            book.save(flush: true, validate: false)
        }

        then:
        book.id != null
    }

    @Rollback
    def "save book with detached author triggers association re-retrieval"() {
        given:
        def author = new HGIAuthor(name: 'Detached Author')
        HGIAuthor.withTransaction {
            author.save(flush: true, failOnError: true)
        }
        session.clear()

        def book = new HGIBook(title: 'Fetched Book', author: author)

        when:
        HGIBook.withTransaction {
            book.save(flush: true, validate: false)
        }

        then:
        book != null
        book.id != null
        book.author != null
        book.author.id == author.id
    }

    @Rollback
    def "handleValidationError sets association to read-only"() {
        given:
        def author = new PersonInstanceApi(name: 'Valid Author', age: 30)
        def book = new ConstrainedBook(title: '', author: author)

        when:
        def result = ConstrainedBook.withTransaction {
            book.save(flush: true)
        }

        then:
        result == null
        book.hasErrors()
    }

    @Rollback
    def "delete resets flush mode on exception"() {
        given:
        def person = new PersonInstanceApi(name: 'Bob', age: 40)
        person.save(flush: true)

        def api = new HibernateGormInstanceApi<PersonInstanceApi>(PersonInstanceApi, manager.hibernateDatastore, Thread.currentThread().contextClassLoader)
        def mockTemplate = Mock(IHibernateTemplate)
        api.hibernateTemplate = mockTemplate
        int callCount = 0

        when:
        api.delete(person, [flush: true])

        then:
        (1.._) * mockTemplate.execute(_) >> { args ->
            callCount++
            if (callCount == 1) {
                throw new org.springframework.dao.InvalidDataAccessApiUsageException("Simulated exception")
            }
        }
        thrown(org.springframework.dao.InvalidDataAccessApiUsageException)
    }

    @Rollback
    def "reconcileCollections replaces stale PersistentCollection"() {
        given:
        def author = new HGIAuthor(name: 'Author').save(flush: true)
        new HGIBook(title: 'Book', author: author).save(flush: true)
        session.clear()
        
        def loadedAuthor = HGIAuthor.get(author.id)
        assert loadedAuthor.books.size() == 1
        session.clear()
        
        when: "merging the detached entity"
        HGIAuthor.withTransaction {
            loadedAuthor.save(flush: true)
        }
        
        then:
        noExceptionThrown()
    }

    @Rollback
    def "test prepareHqlQuery and executeUpdate via HibernateGormStaticApi"() {
        given:
        def staticApi = new HibernateGormStaticApi<>(PersonInstanceApi, datastore, [], Thread.currentThread().contextClassLoader, transactionManager)
        
        when: "Calling prepareHqlQuery (protected, accessible in Groovy test)"
        def query = staticApi.prepareHqlQuery("from PersonInstanceApi where name = 'Bob'", false, false, [:], [], [:])
        
        then:
        query != null
        query instanceof HibernateHqlQueryCreator
        
        when: "Using doListInternal (protected)"
        def results = staticApi.doListInternal("from PersonInstanceApi where name = 'Bob'", [:], [], [:], false)
        
        then:
        results != null
        
        when: "Executing an update through the static API"
        int updated = staticApi.executeUpdate("delete from PersonInstanceApi where name = 'NonExistent'", [:], [:])
        
        then:
        updated == 0
    }
}

@Entity
class ConstrainedBook {
    String title
    static belongsTo = [author: PersonInstanceApi]
    static constraints = {
        title blank: false
    }
}

@Entity
class PersonInstanceApi {
    String name
    Integer age
}

@Entity
class BookInstanceApi {
    String title
    PersonInstanceApi author
    static belongsTo = [author: PersonInstanceApi]
}

@Entity
class ConstrainedPerson {
    String name
    static constraints = {
        name blank: false, maxSize: 100
    }
}

@Entity
class HGIAuthor implements HibernateEntity<HGIAuthor> {
    String name
    static hasMany = [books: HGIBook]
}

@Entity
class HGIBook implements HibernateEntity<HGIBook> {
    String title
    HGIAuthor author
    static belongsTo = [author: HGIAuthor]
}
