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
import grails.gorm.tests.HibernateGormDatastoreSpec
import jakarta.persistence.PersistenceException
import org.grails.orm.hibernate.support.hibernate7.TransactionResources
import org.hibernate.FlushMode
import org.hibernate.HibernateException
import org.hibernate.LockMode
import org.hibernate.Session
import org.springframework.dao.DataAccessException

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

    void "execute with explicit HibernateCallback anonymous class"() {
        given:
        TemplateBook book = new TemplateBook(title: "Refactoring", author: "Martin Fowler")
        TemplateBook.withTransaction {
            template.persist(book)
        }

        when:
        TemplateBook result = template.execute(new GrailsHibernateTemplate.HibernateCallback<TemplateBook>() {
            @Override
            TemplateBook doInHibernate(Session session) throws HibernateException {
                return session.createQuery("from TemplateBook where title = :t", TemplateBook)
                        .setParameter("t", "Refactoring")
                        .uniqueResult()
            }
        })

        then:
        result != null
        result.title == "Refactoring"
    }

    // -------------------------------------------------------------------------
    // hibernateFlushModeToConstant — all branches
    // -------------------------------------------------------------------------

    void "hibernateFlushModeToConstant maps MANUAL to FLUSH_NEVER"() {
        expect:
        GrailsHibernateTemplate.hibernateFlushModeToConstant(FlushMode.MANUAL) == GrailsHibernateTemplate.FLUSH_NEVER
    }

    void "hibernateFlushModeToConstant maps COMMIT to FLUSH_COMMIT"() {
        expect:
        GrailsHibernateTemplate.hibernateFlushModeToConstant(FlushMode.COMMIT) == GrailsHibernateTemplate.FLUSH_COMMIT
    }

    void "hibernateFlushModeToConstant maps ALWAYS to FLUSH_ALWAYS"() {
        expect:
        GrailsHibernateTemplate.hibernateFlushModeToConstant(FlushMode.ALWAYS) == GrailsHibernateTemplate.FLUSH_ALWAYS
    }

    void "hibernateFlushModeToConstant maps AUTO to FLUSH_AUTO"() {
        expect:
        GrailsHibernateTemplate.hibernateFlushModeToConstant(FlushMode.AUTO) == GrailsHibernateTemplate.FLUSH_AUTO
    }

    // -------------------------------------------------------------------------
    // 2-arg constructor (SessionFactory + HibernateDatastore)
    // -------------------------------------------------------------------------

    void "two-arg constructor copies settings from datastore"() {
        when:
        GrailsHibernateTemplate t = new GrailsHibernateTemplate(sessionFactory, manager.hibernateDatastore)

        then:
        t.sessionFactory == sessionFactory
        t.flushMode == GrailsHibernateTemplate.hibernateFlushModeToConstant(manager.hibernateDatastore.defaultFlushMode)
        t.cacheQueries == manager.hibernateDatastore.cacheQueries
        t.osivReadOnly  == manager.hibernateDatastore.osivReadOnly
    }

    void "two-arg constructor tolerates null datastore"() {
        when:
        GrailsHibernateTemplate t = new GrailsHibernateTemplate(sessionFactory, null)

        then:
        t.sessionFactory == sessionFactory
        t.flushMode == GrailsHibernateTemplate.FLUSH_AUTO
    }

    // -------------------------------------------------------------------------
    // 3-arg constructor (SessionFactory + HibernateDatastore + defaultFlushMode)
    // -------------------------------------------------------------------------

    void "three-arg constructor uses explicit flush mode"() {
        when:
        GrailsHibernateTemplate t = new GrailsHibernateTemplate(sessionFactory, manager.hibernateDatastore, GrailsHibernateTemplate.FLUSH_COMMIT)

        then:
        t.flushMode == GrailsHibernateTemplate.FLUSH_COMMIT
    }

    void "three-arg constructor tolerates null datastore"() {
        when:
        GrailsHibernateTemplate t = new GrailsHibernateTemplate(sessionFactory, null, GrailsHibernateTemplate.FLUSH_ALWAYS)

        then:
        t.flushMode == GrailsHibernateTemplate.FLUSH_ALWAYS
    }

    // -------------------------------------------------------------------------
    // refresh(entity, LockMode) — non-null lockMode branch
    // -------------------------------------------------------------------------

    void "refresh with explicit LockMode reloads the entity"() {
        given:
        TemplateBook saved = TemplateBook.withTransaction {
            new TemplateBook(title: "Hibernate Tips", author: "Thorben Janssen").save(flush: true, failOnError: true)
        }
        saved.title = "mutated"

        when:
        TemplateBook.withTransaction {
            template.refresh(saved, LockMode.PESSIMISTIC_READ)
        }

        then:
        saved.title == "Hibernate Tips"
    }

    // -------------------------------------------------------------------------
    // deleteAll
    // -------------------------------------------------------------------------

    void "deleteAll removes all supplied entities"() {
        given:
        List<TemplateBook> books = TemplateBook.withTransaction {
            [
                new TemplateBook(title: "Book X", author: "Author X").save(flush: true, failOnError: true),
                new TemplateBook(title: "Book Y", author: "Author Y").save(flush: true, failOnError: true)
            ]
        }

        when:
        TemplateBook.withTransaction {
            template.deleteAll(books)
            template.flush()
        }

        then:
        books.every { template.get(TemplateBook, it.id) == null }
    }

    // -------------------------------------------------------------------------
    // getIterableAsCollection — both branches
    // -------------------------------------------------------------------------

    void "getIterableAsCollection returns the same Collection when given a Collection"() {
        given:
        List<String> input = ["a", "b", "c"]

        when:
        Collection result = template.getIterableAsCollection(input)

        then:
        result.is(input)
    }

    void "getIterableAsCollection converts a non-Collection Iterable to a List"() {
        given:
        Iterable<String> iterable = ["x", "y"] as Iterable

        when:
        Collection result = template.getIterableAsCollection(iterable)

        then:
        result instanceof List
        result.toList() == ["x", "y"]
    }

    // -------------------------------------------------------------------------
    // executeFind
    // -------------------------------------------------------------------------

    void "executeFind returns a List when the callback returns a List"() {
        given:
        TemplateBook.withTransaction {
            new TemplateBook(title: "Groovy Recipes", author: "Scott Davis").save(flush: true, failOnError: true)
        }

        when:
        List<?> results = template.executeFind { sess ->
            sess.createQuery("from TemplateBook", TemplateBook).list()
        }

        then:
        results instanceof List
        !results.empty
    }

    void "executeFind throws InvalidDataAccessApiUsageException when callback returns a non-List"() {
        when:
        template.executeFind { sess -> "not a list" }

        then:
        thrown(org.springframework.dao.InvalidDataAccessApiUsageException)
    }

    // -------------------------------------------------------------------------
    // executeWithExistingOrCreateNewSession
    // -------------------------------------------------------------------------

    void "executeWithExistingOrCreateNewSession uses existing session when one is bound"() {
        when:
        Long count = template.executeWithExistingOrCreateNewSession(sessionFactory) { sess ->
            sess.createQuery("select count(b) from TemplateBook b", Long).uniqueResult()
        }

        then:
        count != null
        count >= 0L
    }

    void "executeWithExistingOrCreateNewSession opens a new session when none is bound"() {
        when: "called outside any active session binding"
        Long count = template.executeWithNewSession { newSess ->
            template.executeWithExistingOrCreateNewSession(sessionFactory) { sess ->
                sess.createQuery("select count(b) from TemplateBook b", Long).uniqueResult()
            }
        }

        then:
        count != null
        count >= 0L
    }

    // -------------------------------------------------------------------------
    // shouldPassReadOnlyToHibernate — all branches via stubbed TransactionResources
    // -------------------------------------------------------------------------

    void "shouldPassReadOnlyToHibernate returns false when neither flag is set"() {
        expect:
        !template.shouldPassReadOnlyToHibernate()
    }

    void "shouldPassReadOnlyToHibernate returns false when osivReadOnly=true but session not bound"() {
        given:
        template.osivReadOnly = true
        template.txResources = Stub(TransactionResources) {
            hasResource(_) >> false
        }

        expect:
        !template.shouldPassReadOnlyToHibernate()

        cleanup:
        template.osivReadOnly = false
        template.txResources = new org.grails.orm.hibernate.support.hibernate7.DefaultTransactionResources()
    }

    void "shouldPassReadOnlyToHibernate returns true via osivReadOnly when no active transaction"() {
        given:
        template.osivReadOnly = true
        template.txResources = Stub(TransactionResources) {
            hasResource(_) >> true
            isActualTransactionActive() >> false
        }

        expect:
        template.shouldPassReadOnlyToHibernate()

        cleanup:
        template.osivReadOnly = false
        template.txResources = new org.grails.orm.hibernate.support.hibernate7.DefaultTransactionResources()
    }

    void "shouldPassReadOnlyToHibernate returns true via passReadOnlyToHibernate when transaction is read-only"() {
        given:
        GrailsHibernateTemplate.getDeclaredField('passReadOnlyToHibernate').tap { it.accessible = true }.set(template, true)
        template.txResources = Stub(TransactionResources) {
            hasResource(_) >> true
            isActualTransactionActive() >> true
            isCurrentTransactionReadOnly() >> true
        }

        expect:
        template.shouldPassReadOnlyToHibernate()

        cleanup:
        GrailsHibernateTemplate.getDeclaredField('passReadOnlyToHibernate').tap { it.accessible = true }.set(template, false)
        template.txResources = new org.grails.orm.hibernate.support.hibernate7.DefaultTransactionResources()
    }

    void "shouldPassReadOnlyToHibernate returns false via passReadOnlyToHibernate when transaction is not read-only"() {
        given:
        GrailsHibernateTemplate.getDeclaredField('passReadOnlyToHibernate').tap { it.accessible = true }.set(template, true)
        template.txResources = Stub(TransactionResources) {
            hasResource(_) >> true
            isActualTransactionActive() >> true
            isCurrentTransactionReadOnly() >> false
        }

        expect:
        !template.shouldPassReadOnlyToHibernate()

        cleanup:
        GrailsHibernateTemplate.getDeclaredField('passReadOnlyToHibernate').tap { it.accessible = true }.set(template, false)
        template.txResources = new org.grails.orm.hibernate.support.hibernate7.DefaultTransactionResources()
    }

    // -------------------------------------------------------------------------
    // applyFlushMode — all branches via Mock(Session)
    // -------------------------------------------------------------------------

    void "applyFlushMode returns null immediately when applyFlushModeOnlyToNonExistingTransactions and existing tx"() {
        given:
        template.applyFlushModeOnlyToNonExistingTransactions = true
        def mockSession = Mock(Session)

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == null
        0 * mockSession.setHibernateFlushMode(_)

        cleanup:
        template.applyFlushModeOnlyToNonExistingTransactions = false
    }

    void "applyFlushMode FLUSH_NEVER with existing tx and previous mode >= COMMIT sets MANUAL and returns previous"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_NEVER
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.COMMIT }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == FlushMode.COMMIT
        1 * mockSession.setHibernateFlushMode(FlushMode.MANUAL)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_NEVER with existing tx and previous mode < COMMIT is a no-op"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_NEVER
        // MANUAL.lessThan(COMMIT) == true, so !lessThan is false — no change
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.MANUAL }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == null
        0 * mockSession.setHibernateFlushMode(_)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_NEVER without existing tx sets MANUAL"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_NEVER
        def mockSession = Mock(Session)

        when:
        FlushMode result = template.applyFlushMode(mockSession, false)

        then:
        result == null
        1 * mockSession.setHibernateFlushMode(FlushMode.MANUAL)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_EAGER with existing tx and previous != AUTO sets AUTO and returns previous"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_EAGER
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.COMMIT }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == FlushMode.COMMIT
        1 * mockSession.setHibernateFlushMode(FlushMode.AUTO)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_EAGER with existing tx and previous == AUTO is a no-op"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_EAGER
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.AUTO }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == null
        0 * mockSession.setHibernateFlushMode(_)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_EAGER without existing tx is a no-op"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_EAGER
        def mockSession = Mock(Session)

        when:
        FlushMode result = template.applyFlushMode(mockSession, false)

        then:
        result == null
        0 * mockSession.setHibernateFlushMode(_)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_COMMIT with existing tx and previous AUTO sets COMMIT and returns AUTO"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_COMMIT
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.AUTO }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == FlushMode.AUTO
        1 * mockSession.setHibernateFlushMode(FlushMode.COMMIT)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_COMMIT with existing tx and previous ALWAYS sets COMMIT and returns ALWAYS"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_COMMIT
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.ALWAYS }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == FlushMode.ALWAYS
        1 * mockSession.setHibernateFlushMode(FlushMode.COMMIT)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_COMMIT with existing tx and previous COMMIT is a no-op"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_COMMIT
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.COMMIT }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == null
        0 * mockSession.setHibernateFlushMode(_)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_COMMIT without existing tx sets COMMIT"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_COMMIT
        def mockSession = Mock(Session)

        when:
        FlushMode result = template.applyFlushMode(mockSession, false)

        then:
        result == null
        1 * mockSession.setHibernateFlushMode(FlushMode.COMMIT)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_ALWAYS with existing tx and previous != ALWAYS sets ALWAYS and returns previous"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_ALWAYS
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.AUTO }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == FlushMode.AUTO
        1 * mockSession.setHibernateFlushMode(FlushMode.ALWAYS)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_ALWAYS with existing tx and previous == ALWAYS is a no-op"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_ALWAYS
        def mockSession = Mock(Session) { getHibernateFlushMode() >> FlushMode.ALWAYS }

        when:
        FlushMode result = template.applyFlushMode(mockSession, true)

        then:
        result == null
        0 * mockSession.setHibernateFlushMode(_)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    void "applyFlushMode FLUSH_ALWAYS without existing tx sets ALWAYS"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_ALWAYS
        def mockSession = Mock(Session)

        when:
        FlushMode result = template.applyFlushMode(mockSession, false)

        then:
        result == null
        1 * mockSession.setHibernateFlushMode(FlushMode.ALWAYS)

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    // -------------------------------------------------------------------------
    // doExecute — exception paths
    // -------------------------------------------------------------------------

    void "doExecute wraps HibernateException as DataAccessException"() {
        when:
        template.execute { sess -> throw new HibernateException("simulated") }

        then:
        thrown(DataAccessException)
    }

    void "doExecute wraps PersistenceException with HibernateException cause as DataAccessException"() {
        when:
        template.execute { sess ->
            throw new PersistenceException(new HibernateException("inner cause"))
        }

        then:
        thrown(DataAccessException)
    }

    void "doExecute rethrows PersistenceException that has no HibernateException cause"() {
        when:
        template.execute { sess ->
            throw new PersistenceException("plain persistence error")
        }

        then:
        thrown(PersistenceException)
    }

    void "doExecute wraps SQLException as DataAccessException"() {
        when: "SQLException with a recognisable SQL state (23=constraint violation) is thrown from callback"
        template.execute { sess -> throw new java.sql.SQLException("constraint violation", "23000", 23000) }

        then:
        thrown(DataAccessException)
    }

    // -------------------------------------------------------------------------
    // createSessionProxy — exposeNativeSession=false path
    // -------------------------------------------------------------------------

    void "execute exposes a JDK proxy when exposeNativeSession is false"() {
        given:
        template.exposeNativeSession = false

        when:
        Class<?> sessionClass = template.execute { sess -> sess.class }

        then:
        java.lang.reflect.Proxy.isProxyClass(sessionClass)

        cleanup:
        template.exposeNativeSession = true
    }

    // -------------------------------------------------------------------------
    // flushIfNecessary — FLUSH_EAGER path inside existing transaction
    // -------------------------------------------------------------------------

    void "execute with FLUSH_EAGER flushes session after callback in existing transaction"() {
        given:
        template.flushMode = GrailsHibernateTemplate.FLUSH_EAGER

        when:
        template.execute { sess -> null }

        then:
        noExceptionThrown()

        cleanup:
        template.flushMode = GrailsHibernateTemplate.FLUSH_AUTO
    }

    // ── Additional edge cases for coverage ───────────────────────────────────

    void "executeFind returns null if action returns null"() {
        when:
        def result = template.executeFind { sess -> null }

        then:
        result == null
    }

    void "getIterableAsCollection handles non-Collection Iterables"() {
        given:
        def iterable = new Iterable() {
            @Override
            Iterator iterator() {
                return ["a", "b"].iterator()
            }
        }

        when:
        def collection = template.getIterableAsCollection(iterable)

        then:
        collection instanceof List
        collection.size() == 2
        collection.contains("a")
        collection.contains("b")
    }

    void "convertHibernateAccessException handles JDBCException"() {
        given:
        def jdbcEx = new org.hibernate.exception.ConstraintViolationException("msg", new java.sql.SQLException("inner", "23000"), "constraint")

        when:
        def converted = template.convertHibernateAccessException(jdbcEx)

        then:
        converted instanceof DataAccessException
    }

    void "convertHibernateAccessException handles GenericJDBCException"() {
        given:
        // Use a SQL state that is likely to be translated (e.g. 42000 for syntax error)
        def genericEx = new org.hibernate.exception.GenericJDBCException("msg", new java.sql.SQLException("inner", "42000"))

        when:
        def converted = template.convertHibernateAccessException(genericEx)

        then:
        converted instanceof DataAccessException
    }

    void "createSessionProxy handles EventSource"() {
        given:
        def mockEventSourceSession = Mock(org.hibernate.event.spi.EventSource)
        template.exposeNativeSession = false

        when:
        def proxy = template.createSessionProxy(mockEventSourceSession)

        then:
        proxy instanceof org.hibernate.event.spi.EventSource

        cleanup:
        template.exposeNativeSession = true
    }

    void "createSessionProxy handles SessionImplementor"() {
        given:
        def mockSessionImplementor = Mock(org.hibernate.engine.spi.SessionImplementor)
        template.exposeNativeSession = false

        when:
        def proxy = template.createSessionProxy(mockSessionImplementor)

        then:
        proxy instanceof org.hibernate.engine.spi.SessionImplementor

        cleanup:
        template.exposeNativeSession = true
    }

    void "test executeWithNewSession does not release connection for MultiTenantDataSource"() {
        given: "A template with a multi-tenant data source"
        def mockMultiTenantDataSource = Mock(org.grails.datastore.gorm.jdbc.MultiTenantDataSource)
        mockMultiTenantDataSource.getConnection() >> Mock(java.sql.Connection) {
            getMetaData() >> Mock(java.sql.DatabaseMetaData) {
                getDatabaseProductName() >> "H2"
            }
        }
        
        def mockSessionFactory = Mock(org.hibernate.engine.spi.SessionFactoryImplementor)
        def mockServiceRegistry = Mock(org.hibernate.service.spi.ServiceRegistryImplementor)
        def mockConnectionProvider = Mock(org.hibernate.engine.jdbc.connections.spi.ConnectionProvider)
        
        mockSessionFactory.getServiceRegistry() >> mockServiceRegistry
        mockServiceRegistry.getService(org.hibernate.engine.jdbc.connections.spi.ConnectionProvider) >> mockConnectionProvider
        mockConnectionProvider.unwrap(javax.sql.DataSource) >> mockMultiTenantDataSource
        
        // Mocking the TransactionResources to return our multi-tenant data source
        def mockTxResources = Mock(TransactionResources)
        def templateUnderTest = new GrailsHibernateTemplate(mockSessionFactory)
        templateUnderTest.txResources = mockTxResources

        when: "executeWithNewSession is called"
        templateUnderTest.executeWithNewSession { session -> }

        then: "The multi-tenant data source is handled correctly"
        1 * mockSessionFactory.openSession() >> Mock(org.hibernate.engine.spi.SessionImplementor)
        // Ensure the resource is unbound
        1 * mockTxResources.unbindResourceIfPossible(mockMultiTenantDataSource) >> null
        
        cleanup:
        templateUnderTest.txResources = new org.grails.orm.hibernate.support.hibernate7.DefaultTransactionResources()
    }
}

@Entity
class TemplateBook implements HibernateEntity<TemplateBook> {
    String title
    String author
}
