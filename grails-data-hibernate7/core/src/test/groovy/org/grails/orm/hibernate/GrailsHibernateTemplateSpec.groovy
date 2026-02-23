/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.LockMode

class GrailsHibernateTemplateSpec extends HibernateGormDatastoreSpec {

    GrailsHibernateTemplate template

    @Override
    void setupSpec() {
        manager.addAllDomainClasses([TemplateBook])
    }

    void setup() {
        template = new GrailsHibernateTemplate(sessionFactory)
    }

    void cleanup() {
        session.clear()
    }

    // -------------------------------------------------------------------------
    // Flush mode constants
    // -------------------------------------------------------------------------

    void "flush mode constants have expected values"() {
        expect:
        GrailsHibernateTemplate.FLUSH_NEVER  == 0
        GrailsHibernateTemplate.FLUSH_AUTO   == 1
        GrailsHibernateTemplate.FLUSH_EAGER  == 2
        GrailsHibernateTemplate.FLUSH_COMMIT == 3
        GrailsHibernateTemplate.FLUSH_ALWAYS == 4
    }

    void "default flush mode is FLUSH_AUTO"() {
        expect:
        template.flushMode == GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "setFlushMode and getFlushMode round-trip"() {
        when:
        template.flushMode = GrailsHibernateTemplate.FLUSH_COMMIT

        then:
        template.flushMode == GrailsHibernateTemplate.FLUSH_COMMIT
    }

    // -------------------------------------------------------------------------
    // Constructor / configuration
    // -------------------------------------------------------------------------

    void "constructor exposes the sessionFactory"() {
        expect:
        template.sessionFactory == sessionFactory
    }

    void "cacheQueries defaults to false and can be changed"() {
        expect:
        !template.cacheQueries

        when:
        template.cacheQueries = true

        then:
        template.cacheQueries
    }

    void "exposeNativeSession defaults to true and can be changed"() {
        expect:
        template.exposeNativeSession

        when:
        template.exposeNativeSession = false

        then:
        !template.exposeNativeSession
    }

    void "osivReadOnly defaults to false and can be toggled"() {
        expect:
        !template.osivReadOnly

        when:
        template.osivReadOnly = true

        then:
        template.osivReadOnly
    }

    // -------------------------------------------------------------------------
    // execute(Closure) — read-only HQL query
    // -------------------------------------------------------------------------

    void "execute with Closure runs query inside a session"() {
        given:
        TemplateBook.withTransaction {
            new TemplateBook(title: "Groovy in Action", author: "Dierk König").save(flush: true, failOnError: true)
        }

        when:
        Long count = template.execute { sess ->
            sess.createQuery("select count(b) from TemplateBook b", Long).uniqueResult()
        }

        then:
        count >= 1L
    }

    void "execute with HibernateCallback runs query inside a session"() {
        given:
        TemplateBook.withTransaction {
            new TemplateBook(title: "Making Java Groovy", author: "Ken Kousen").save(flush: true, failOnError: true)
        }

        when:
        Long count = template.execute({ sess ->
            sess.createQuery("select count(b) from TemplateBook b", Long).uniqueResult()
        } as GrailsHibernateTemplate.HibernateCallback)

        then:
        count >= 1L
    }

    // -------------------------------------------------------------------------
    // executeWithNewSession
    // -------------------------------------------------------------------------

    void "executeWithNewSession uses an isolated session"() {
        when: "a query runs in a brand-new session"
        Long count = template.executeWithNewSession { sess ->
            sess.createQuery("select count(b) from TemplateBook b", Long).uniqueResult()
        }

        then: "the new session is functional and returns a non-null result"
        count != null
        count >= 0L
    }

    // -------------------------------------------------------------------------
    // get
    // -------------------------------------------------------------------------

    void "get returns the entity by id"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Programming Groovy 2", author: "Venkat Subramaniam").save(flush: true, failOnError: true)
        }
        session.clear()

        when:
        TemplateBook found = template.get(TemplateBook, saved.id)

        then:
        found != null
        found.id   == saved.id
        found.title == "Programming Groovy 2"
    }

    void "get returns null for non-existent id"() {
        expect:
        template.get(TemplateBook, -1L) == null
    }

    void "get with LockMode returns the entity"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Clean Code", author: "Robert Martin").save(flush: true, failOnError: true)
        }
        session.clear()

        when:
        TemplateBook found = TemplateBook.withTransaction {
            template.get(TemplateBook, saved.id, LockMode.PESSIMISTIC_WRITE)
        }

        then:
        found != null
        found.id == saved.id
    }

    // -------------------------------------------------------------------------
    // load (lazy reference)
    // -------------------------------------------------------------------------

    void "load returns a reference for a persisted entity"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Effective Java", author: "Joshua Bloch").save(flush: true, failOnError: true)
        }
        session.clear()

        when:
        TemplateBook ref = template.load(TemplateBook, saved.id)

        then:
        ref != null
        ref.id == saved.id
    }

    // -------------------------------------------------------------------------
    // loadAll
    // -------------------------------------------------------------------------

    void "loadAll returns all persisted instances of the class"() {
        given:
        TemplateBook.withTransaction {
            new TemplateBook(title: "Book A", author: "Author A").save(flush: true, failOnError: true)
            new TemplateBook(title: "Book B", author: "Author B").save(flush: true, failOnError: true)
        }

        when:
        List<TemplateBook> all = template.loadAll(TemplateBook)

        then:
        all.size() >= 2
        all.every { it instanceof TemplateBook }
    }

    // -------------------------------------------------------------------------
    // persist
    // -------------------------------------------------------------------------

    void "persist saves a new entity and assigns an id"() {
        given:
        TemplateBook book = new TemplateBook(title: "Domain-Driven Design", author: "Eric Evans")

        when:
        TemplateBook.withTransaction {
            template.persist(book)
            template.flush()
        }

        then:
        book.id != null
    }

    // -------------------------------------------------------------------------
    // merge
    // -------------------------------------------------------------------------

    void "merge returns a managed copy of the detached entity"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Refactoring", author: "Martin Fowler").save(flush: true, failOnError: true)
        }
        session.clear()
        saved.title = "Refactoring (2nd Edition)"

        when:
        TemplateBook managed = TemplateBook.withTransaction {
            template.merge(saved) as TemplateBook
        }

        then:
        managed != null
        managed.id    == saved.id
        managed.title == "Refactoring (2nd Edition)"
    }

    // -------------------------------------------------------------------------
    // remove
    // -------------------------------------------------------------------------

    void "remove deletes the entity"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "The Pragmatic Programmer", author: "Dave Thomas").save(flush: true, failOnError: true)
        }
        Long id = saved.id

        when:
        TemplateBook.withTransaction {
            TemplateBook managed = template.get(TemplateBook, id)
            template.remove(managed)
            template.flush()
        }

        then:
        template.get(TemplateBook, id) == null
    }

    // -------------------------------------------------------------------------
    // contains / evict
    // -------------------------------------------------------------------------

    void "contains returns true for a managed entity and false after evict"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Head First Java", author: "Kathy Sierra").save(flush: true, failOnError: true)
        }

        when:
        boolean before = template.contains(saved)
        template.evict(saved)
        boolean after = template.contains(saved)

        then:
        before
        !after
    }

    // -------------------------------------------------------------------------
    // refresh
    // -------------------------------------------------------------------------

    void "refresh reloads the entity state from the database"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Spring in Action", author: "Craig Walls").save(flush: true, failOnError: true)
        }

        when: "the in-memory state is mutated without flushing"
        saved.title = "mutated"

        and: "refresh restores the persisted state"
        template.refresh(saved)

        then:
        saved.title == "Spring in Action"
    }

    // -------------------------------------------------------------------------
    // flush / clear
    // -------------------------------------------------------------------------

    void "flush() flushes pending changes to the database"() {
        given:
        TemplateBook book = new TemplateBook(title: "Seven Languages", author: "Bruce Tate")
        TemplateBook.withTransaction {
            template.persist(book)
            template.flush()
        }

        when:
        Long count = template.execute { sess ->
            sess.createQuery("select count(b) from TemplateBook b where b.title = :t", Long)
               .setParameter("t", "Seven Languages")
               .uniqueResult()
        }

        then:
        count == 1L
    }

    void "clear() detaches all entities from the session"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Java Concurrency in Practice", author: "Brian Goetz").save(flush: true, failOnError: true)
        }

        when:
        boolean before = template.contains(saved)
        template.clear()
        boolean after = template.contains(saved)

        then:
        before
        !after
    }

    // -------------------------------------------------------------------------
    // lock(Object, LockMode)
    // -------------------------------------------------------------------------

    void "lock(entity, lockMode) acquires a pessimistic lock on the entity"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Java Performance", author: "Scott Oaks").save(flush: true, failOnError: true)
        }

        when:
        TemplateBook.withTransaction {
            template.lock(saved, LockMode.PESSIMISTIC_WRITE)
        }

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // lock(Class, Serializable, LockMode)
    // -------------------------------------------------------------------------

    void "lock(class, id, lockMode) retrieves and locks the entity"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Thinking in Java", author: "Bruce Eckel").save(flush: true, failOnError: true)
        }
        session.clear()

        when:
        TemplateBook locked = TemplateBook.withTransaction {
            template.lock(TemplateBook, saved.id, LockMode.PESSIMISTIC_READ)
        }

        then:
        locked != null
        locked.id == saved.id
    }

    // -------------------------------------------------------------------------
    // getSession
    // -------------------------------------------------------------------------

    void "getSession returns the current Hibernate session"() {
        when:
        def sess = template.session

        then:
        sess != null
    }

    // -------------------------------------------------------------------------
    // applyFlushModeOnlyToNonExistingTransactions flag
    // -------------------------------------------------------------------------

    void "applyFlushModeOnlyToNonExistingTransactions can be toggled"() {
        expect:
        !template.applyFlushModeOnlyToNonExistingTransactions

        when:
        template.applyFlushModeOnlyToNonExistingTransactions = true

        then:
        template.applyFlushModeOnlyToNonExistingTransactions
    }
}

@Entity
class TemplateBook implements HibernateEntity<TemplateBook> {
    String title
    String author
}
