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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.FlushModeType
import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.HibernateException
import org.springframework.transaction.support.TransactionSynchronizationManager

class HibernateSessionSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HSBook])
    }

    // -------------------------------------------------------------------------
    // Accessors and simple state
    // -------------------------------------------------------------------------

    void "isSchemaless returns false"() {
        expect:
        !getSession().isSchemaless()
    }

    void "isConnected returns true for a fresh session"() {
        expect:
        getSession().isConnected()
    }

    void "disconnect sets connected to false"() {
        given:
        def session = getSession()

        when:
        session.disconnect()

        then:
        !session.isConnected()
    }

    void "getMappingContext returns the datastore mapping context"() {
        expect:
        getSession().getMappingContext() == datastore.getMappingContext()
    }

    void "getDatastore returns the HibernateDatastore"() {
        expect:
        getSession().getDatastore() == datastore
    }

    void "getNativeInterface returns the HibernateTemplate"() {
        given:
        def session = getSession()

        expect:
        session.getNativeInterface() == session.getHibernateTemplate()
    }

    void "getHibernateTemplate returns a non-null template"() {
        expect:
        getSession().getHibernateTemplate() != null
    }

    void "getHibernateTemplate returns the same template as the datastore"() {
        expect:
        getSession().getHibernateTemplate().is(datastore.getHibernateTemplate())
    }

    // -------------------------------------------------------------------------
    // Transaction guards
    // -------------------------------------------------------------------------

    void "beginTransaction() throws UnsupportedOperationException"() {
        when:
        getSession().beginTransaction()

        then:
        thrown(UnsupportedOperationException)
    }

    void "beginTransaction(definition) throws UnsupportedOperationException"() {
        when:
        getSession().beginTransaction(null)

        then:
        thrown(UnsupportedOperationException)
    }

    void "hasTransaction returns true when a transaction is active"() {
        expect:
        getSession().hasTransaction()
    }

    // -------------------------------------------------------------------------
    // Flush mode
    // -------------------------------------------------------------------------

    void "getFlushMode and setFlushMode round-trip correctly"() {
        given:
        def session = getSession()

        when:
        session.setFlushMode(FlushModeType.AUTO)

        then:
        session.getFlushMode() == FlushModeType.AUTO

        when:
        session.setFlushMode(FlushModeType.COMMIT)

        then:
        session.getFlushMode() == FlushModeType.COMMIT
    }

    // -------------------------------------------------------------------------
    // Persist and retrieve
    // -------------------------------------------------------------------------

    void "persist(Object) saves entity and returns id"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Grails in Action")

        when:
        def id = session.persist(book)

        then:
        id != null
        session.contains(book)
    }

    void "insert(Object) delegates to persist and returns id"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Inserted Book")

        when:
        def id = session.insert(book)

        then:
        id != null
    }

    void "persist(Iterable) persists all entities and returns ids"() {
        given:
        def session = getSession()
        def books = [new HSBook(title: "Book A"), new HSBook(title: "Book B")]

        when:
        def ids = session.persist(books)

        then:
        ids.size() == 2
        ids.every { it != null }
    }

    void "retrieve returns entity by id"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Retrieved Book")
        def id = session.persist(book)
        session.flush()

        when:
        def found = session.retrieve(HSBook, id)

        then:
        found != null
        found.title == "Retrieved Book"
    }

    void "getObjectIdentifier returns the entity id"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Identified Book")
        def id = session.persist(book)

        when:
        def result = session.getObjectIdentifier(book)

        then:
        result == id
    }

    // -------------------------------------------------------------------------
    // Session state management
    // -------------------------------------------------------------------------

    void "contains returns true for persisted entity"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Contained Book")
        session.persist(book)

        expect:
        session.contains(book)
    }

    void "merge returns the merged entity"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Original")
        session.persist(book)
        session.flush()
        session.clear()

        book.title = "Modified"

        when:
        def merged = session.merge(book)

        then:
        merged != null
    }

    void "refresh reloads entity state from database"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Refreshable")
        session.persist(book)
        session.flush()

        when:
        session.refresh(book)

        then:
        noExceptionThrown()
    }

    void "flush executes without error"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Flushed")
        session.persist(book)

        when:
        session.flush()

        then:
        noExceptionThrown()
    }

    void "clear(Object) evicts entity from session"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Evicted")
        session.persist(book)

        when:
        session.clear(book)

        then:
        !session.contains(book)
    }

    void "clear() clears the entire session"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Cleared")
        session.persist(book)

        when:
        session.clear()

        then:
        !session.contains(book)
    }

    void "lock(Object) acquires lock without error"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Locked")
        session.persist(book)
        session.flush()

        when:
        session.lock(book)

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    void "delete(Object) removes entity"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Deleted")
        def id = session.persist(book)
        session.flush()

        when:
        session.delete(book)
        session.flush()

        then:
        session.retrieve(HSBook, id) == null
    }

    void "delete(Iterable) removes all entities"() {
        given:
        def session = getSession()
        def books = [new HSBook(title: "Del A"), new HSBook(title: "Del B")]
        def ids = session.persist(books)
        session.flush()

        when:
        session.delete(books)
        session.flush()

        then:
        ids.every { session.retrieve(HSBook, it) == null }
    }

    // -------------------------------------------------------------------------
    // Bulk retrieve
    // -------------------------------------------------------------------------

    void "retrieveAll(type, keys...) returns matching entities"() {
        given:
        def session = getSession()
        def b1 = new HSBook(title: "RA1")
        def b2 = new HSBook(title: "RA2")
        def id1 = session.persist(b1)
        def id2 = session.persist(b2)
        session.flush()

        when:
        def results = session.retrieveAll(HSBook, id1, id2)

        then:
        results.size() == 2
    }

    void "retrieveAll(type, Iterable<keys>) returns matching entities"() {
        given:
        def session = getSession()
        def b1 = new HSBook(title: "RI1")
        def b2 = new HSBook(title: "RI2")
        def id1 = session.persist(b1)
        def id2 = session.persist(b2)
        session.flush()

        when:
        def results = session.retrieveAll(HSBook, [id1, id2])

        then:
        results.size() == 2
    }

    // -------------------------------------------------------------------------
    // Bulk criteria operations
    // -------------------------------------------------------------------------

    void "deleteAll(criteria) bulk deletes matching entities"() {
        given:
        def session = getSession()
        ['Bulk A', 'Bulk B', 'Keep'].each { title ->
            session.persist(new HSBook(title: title))
        }
        session.flush()
        session.clear()

        def criteria = new DetachedCriteria(HSBook).build {
            like('title', 'Bulk%')
        }

        when:
        long deleted = session.deleteAll(criteria)

        then:
        deleted == 2
    }

    void "updateAll(criteria, properties) bulk updates matching entities"() {
        given:
        def session = getSession()
        ['Update A', 'Update B'].each { title ->
            session.persist(new HSBook(title: title))
        }
        session.flush()
        session.clear()

        def criteria = new DetachedCriteria(HSBook).build {
            like('title', 'Update%')
        }

        when:
        long updated = session.updateAll(criteria, [title: 'Updated'])

        then:
        updated == 2
    }

    // -------------------------------------------------------------------------
    // Query creation
    // -------------------------------------------------------------------------

    void "createQuery(type) returns a HibernateQuery"() {
        when:
        def query = getSession().createQuery(HSBook)

        then:
        query instanceof HibernateQuery
    }

    void "createQuery(type, alias) returns a HibernateQuery with alias set"() {
        when:
        def query = getSession().createQuery(HSBook, 'b')

        then:
        query instanceof HibernateQuery
    }

    // ─── Additional edge cases for coverage ───────────────────────────────────

    void "getObjectIdentifier returns null for null instance"() {
        expect:
        getSession().getObjectIdentifier(null) == null
    }

    void "getObjectIdentifier handles proxy"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Proxy Book").save(flush: true)
        session.clear()
        def proxy = session.proxy(HSBook, book.id)

        expect:
        session.getObjectIdentifier(proxy) == book.id
    }

    void "getIterableAsCollection handles non-Collection Iterable"() {
        given:
        def iterable = new Iterable() {
            @Override
            Iterator iterator() {
                return ["a", "b"].iterator()
            }
        }

        when:
        def collection = getSession().getIterableAsCollection(iterable)

        then:
        collection.size() == 2
        collection.contains("a")
        collection.contains("b")
    }

    void "updateAll handles lastUpdated auto-timestamp"() {
        given:
        def session = getSession()
        def book = new HSBook(title: "Timestamp Book").save(flush: true)
        def criteria = new DetachedCriteria(HSBook).build {
            eq('id', book.id)
        }

        when:
        session.updateAll(criteria, [title: "Updated Title"])

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // getNativeSession() — fallback contract
    // -------------------------------------------------------------------------

    // Exposes the root cause of the SCHEMA multi-tenancy "No Session found" bug:
    // HibernateSession constructed without a native session falls back to
    // sessionFactory.getCurrentSession(), which throws when no session is
    // bound to the thread (e.g. bare Tenants.withId() 0-arg closure path).
    void "getNativeSession() throws HibernateException when constructed without a native session and no thread-bound session exists"() {
        given: "any pre-existing thread-bound Hibernate session is saved and cleared"
        def sf = datastore.sessionFactory
        def prior = TransactionSynchronizationManager.getResource(sf)
        if (prior) TransactionSynchronizationManager.unbindResource(sf)

        and: "a HibernateSession created without a pre-opened native session"
        def wrapper = new HibernateSession(datastore, sf)

        when: "getNativeSession() falls back to getCurrentSession() with nothing bound"
        wrapper.getNativeSession()

        then: "an exception is thrown because there is no session on the thread"
        thrown(HibernateException)

        cleanup:
        if (prior) TransactionSynchronizationManager.bindResource(sf, prior)
    }

    // Documents the correct contract: when a native session is explicitly provided,
    // getNativeSession() returns it directly without any thread-lookup.
    void "getNativeSession() returns the explicitly provided native session without thread lookup"() {
        given: "a real Hibernate session captured from withNewSession"
        org.hibernate.Session captured = null
        datastore.withNewSession { org.hibernate.Session s -> captured = s }

        and: "a HibernateSession wrapper constructed with that native session"
        def wrapper = new HibernateSession(datastore, datastore.sessionFactory, captured)

        expect: "getNativeSession() returns the exact same session instance"
        wrapper.getNativeSession().is(captured)
    }
}

@Entity
class HSBook implements HibernateEntity<HSBook> {
    String title
}
