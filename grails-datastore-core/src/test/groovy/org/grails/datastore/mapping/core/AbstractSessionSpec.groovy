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
package org.grails.datastore.mapping.core

import jakarta.persistence.FlushModeType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

import org.grails.datastore.mapping.core.impl.PendingDeleteAdapter
import org.grails.datastore.mapping.core.impl.PendingInsertAdapter
import org.grails.datastore.mapping.core.impl.PendingOperationAdapter
import org.grails.datastore.mapping.core.impl.PendingUpdateAdapter
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.engine.NonPersistentTypeException
import org.grails.datastore.mapping.engine.Persister
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.transactions.SessionHolder
import org.grails.datastore.mapping.transactions.Transaction

class AbstractSessionSpec extends Specification {

    MappingContext mappingContext = new KeyValueMappingContext('test')
    PersistentEntity bookEntity = mappingContext.addPersistentEntity(ASBook)
    PersistentEntity tagEntity = mappingContext.addPersistentEntity(ASTag)
    PersistentEntity statelessEntity = mappingContext.addPersistentEntity(ASStatelessBook)
    Datastore datastore = Stub(Datastore) {
        getMappingContext() >> mappingContext
        isSchemaless() >> true
    }
    ApplicationEventPublisher publisher = Stub(ApplicationEventPublisher)
    TestSession session = new TestSession(datastore, mappingContext, publisher)

    void setup() {
        statelessEntity.mapping.mappedForm.stateless = true
        session.persisters[ASBook] = new StoringPersister(mappingContext, bookEntity, session, publisher)
        session.persisters[ASTag] = new StoringPersister(mappingContext, tagEntity, session, publisher)
        session.persisters[ASStatelessBook] = new StoringPersister(mappingContext, statelessEntity, session, publisher)
    }

    void cleanup() {
        statelessEntity.mapping.mappedForm.stateless = false
    }

    private StoringPersister persisterFor(Class type) {
        (StoringPersister) session.getPersister(type)
    }

    void "the session exposes its collaborators and flush mode"() {
        expect:
        session.datastore.is(datastore)
        session.mappingContext.is(mappingContext)
        session.schemaless
        !session.stateless
        session.connected
        session.flushMode == FlushModeType.AUTO
        session.createEntityAccess(bookEntity, new ASBook()) instanceof EntityAccess
        AbstractSession.ENTITY_ACCESS == 'org.grails.gorm.ENTITY_ACCESS'

        when:
        session.flushMode = FlushModeType.COMMIT
        session.synchronizedWithTransaction = true

        then:
        session.flushMode == FlushModeType.COMMIT
        session.isSynchronizedWithTransaction
    }

    void "persisters are resolved from classes, entities and instances and cached"() {
        expect:
        session.getPersister(null) == null
        session.getPersister(String) == null
        session.getPersister(ASBook).is(session.getPersister(new ASBook()))
        session.getPersister(bookEntity).is(session.getPersister(ASBook))
        session.created == [String, ASBook]
    }

    void "persist, insert and refresh delegate to the persister and cache the instance"() {
        given:
        ASBook book = new ASBook(name: 'b')

        when:
        Serializable key = session.persist(book)

        then:
        key == 1L
        book.id == 1L
        session.contains(book)
        session.getCachedInstance(ASBook, 1L).is(book)
        session.getObjectIdentifier(book) == 1L

        when:
        Serializable inserted = session.insert(new ASBook(name: 'c'))
        session.refresh(book)

        then:
        inserted == 2L
        persisterFor(ASBook).refreshed == [book]
    }

    void "persisting, inserting or refreshing unsupported objects fails"() {
        when:
        session.persist((Object) null)

        then:
        thrown(IllegalArgumentException)

        when:
        session.persist('nope')

        then:
        NonPersistentTypeException e = thrown()
        e.message == 'Object [nope] cannot be persisted. It is not a known persistent type.'

        when:
        session.insert('nope')

        then:
        thrown(NonPersistentTypeException)

        when:
        session.refresh('nope')

        then:
        e = thrown()
        e.message == 'Object [nope] cannot be refreshed. It is not a known persistent type.'

        when:
        session.persist(['nope'])

        then:
        e = thrown()
        e.message == 'Cannot persist objects. The class [java.lang.String] is not a known persistent type.'
    }

    void "persisting an iterable uses the first object's persister"() {
        given:
        List books = [new ASBook(name: 'a'), new ASBook(name: 'b')]

        expect:
        session.persist((Iterable) null) == []
        session.persist([]) == []
        session.persist(books) == [1L, 2L]
    }

    void "retrieve converts keys, honours the cache and ignores null keys"() {
        given:
        ASBook book = new ASBook(name: 'r')
        session.persist(book)

        expect:
        session.retrieve(ASBook, null) == null
        session.retrieve(null, 1L) == null
        session.retrieve(ASBook, 'null') == null
        session.retrieve(ASBook, '1').is(book)
        session.retrieve(ASBook, 1L).is(book)
        persisterFor(ASBook).retrieved.empty

        when:
        session.clear()
        Object loaded = session.retrieve(ASBook, 1L)

        then:
        loaded.name == 'r'
        !loaded.is(book)
        persisterFor(ASBook).retrieved == [1L]
        session.retrieve(ASBook, 1L).is(loaded)
        session.retrieve(ASBook, 99L) == null
    }

    void "retrieve and proxy fail for unknown types"() {
        when:
        session.retrieve(String, 1L)

        then:
        NonPersistentTypeException e = thrown()
        e.message == 'Cannot retrieve object with key [1]. The class [java.lang.String] is not a known persistent type.'

        when:
        session.proxy(String, 1L)

        then:
        thrown(NonPersistentTypeException)

        when:
        session.createQuery(String)

        then:
        e = thrown()
        e.message == 'Cannot create query. The class [class java.lang.String] is not a known persistent type.'

        when:
        session.retrieveAll(String, [1L])

        then:
        thrown(NonPersistentTypeException)

        when:
        session.retrieveAll(String, 1L, 2L)

        then:
        thrown(NonPersistentTypeException)
    }

    void "proxies are only created when the instance is not cached"() {
        given:
        ASBook book = new ASBook(name: 'p')
        session.persist(book)

        expect:
        session.proxy(ASBook, null) == null
        session.proxy(null, 1L) == null
        session.proxy(ASBook, 1L).is(book)
        session.proxy(ASBook, 5L) == 'proxy-5'
    }

    void "retrieveAll fills in uncached instances from the persister"() {
        given:
        ASBook cached = new ASBook(name: 'cached')
        session.persist(cached)
        persisterFor(ASBook).store[2L] = new ASBook(id: 2L, name: 'stored')

        when:
        List result = session.retrieveAll(ASBook, [1L, 2L, 3L])

        then:
        result[0].is(cached)
        result[1].name == 'stored'
        result[2] == null
        session.getCachedInstance(ASBook, 2L).is(result[1])
        session.retrieveAll(ASBook, 1L, 2L)*.name == ['cached', 'stored']
    }

    void "queries are created by the persister"() {
        given:
        Query query = Stub(Query)
        persisterFor(ASBook).query = query

        expect:
        session.createQuery(ASBook).is(query)
    }

    void "delete removes the instance through its persister and clears it from the cache"() {
        given:
        ASBook book = new ASBook(name: 'd')
        ASTag tag = new ASTag(name: 't')
        session.persist(book)
        session.persist(tag)

        when:
        session.delete((Object) null)
        session.delete('unknown')
        session.delete(book)

        then:
        persisterFor(ASBook).deleted == [book]
        !session.contains(book)

        when:
        session.delete((Iterable) null)
        session.delete([null, 'unknown', tag, new ASBook(id: 9L)])

        then:
        persisterFor(ASTag).deletedAll == [[tag]]
        persisterFor(ASBook).deletedAll == [[new ASBook(id: 9L)]]
    }

    void "deleteAll and updateAll operate on the criteria results"() {
        given:
        ASBook one = new ASBook(name: 'one')
        ASBook two = new ASBook(name: 'two')
        QueryableCriteria criteria = Stub(QueryableCriteria) { list() >> [one, two] }

        when:
        long updated = session.updateAll(criteria, [name: 'renamed'])

        then:
        updated == 2
        one.name == 'renamed'
        two.name == 'renamed'
        one.id != null

        when:
        long deleted = session.deleteAll(criteria)

        then:
        deleted == 2
        persisterFor(ASBook).deletedAll == [[one, two]]
    }

    void "locking is unsupported by default but unlock is tolerant"() {
        when:
        session.lock(new ASBook())

        then:
        UnsupportedOperationException e = thrown()
        e.message == 'Datastore [' + TestSession.name + '] does not support locking.'

        when:
        session.lock(ASBook, 1L)

        then:
        thrown(UnsupportedOperationException)

        when:
        session.lockedObjects.add('locked')
        session.unlock(null)
        session.unlock('locked')

        then:
        session.lockedObjects.empty
    }

    void "attach caches an instance that already has an identifier"() {
        given:
        ASBook saved = new ASBook(id: 4L, name: 'attached')
        ASBook unsaved = new ASBook(name: 'unsaved')

        when:
        session.attach(null)
        session.attach('unknown')
        session.attach(unsaved)
        session.attach(saved)

        then:
        session.contains(saved)
        !session.contains(unsaved)
        session.contains(null) == false
        session.getCachedInstance(ASBook, 4L).is(saved)
    }

    void "instance caching ignores null arguments and stateless entities"() {
        given:
        ASBook book = new ASBook(id: 1L)
        ASStatelessBook stateless = new ASStatelessBook(id: 1L)

        when:
        session.cacheInstance(null, 1L, book)
        session.cacheInstance(ASBook, null, book)
        session.cacheInstance(ASBook, 1L, null)
        session.cacheInstance(ASStatelessBook, 1L, stateless)

        then:
        !session.isCached(ASBook, 1L)
        !session.isCached(ASBook, null)
        session.getCachedInstance(ASBook, null) == null
        session.getCachedInstance(null, 1L) == null
        !session.isCached(ASStatelessBook, 1L)
        session.getCachedInstance(ASStatelessBook, 1L) == null
        session.isStateless(statelessEntity)
        !session.isStateless(bookEntity)
        !session.isStateless(null)

        when:
        session.cacheInstance(ASBook, 1L, book)

        then:
        session.isCached(ASBook, 1L)
        session.getCachedInstance(ASBook, 1L).is(book)

        when:
        session.clear(book)

        then:
        !session.isCached(ASBook, 1L)
    }

    void "entry caching keeps a dirty checking copy and can be cleared"() {
        when:
        session.cacheEntry(bookEntity, null, [a: 1])
        session.cacheEntry(bookEntity, 1L, null)
        session.cacheEntry(statelessEntity, 1L, [a: 1])

        then:
        session.getCachedEntry(bookEntity, 1L) == null
        session.getCachedEntry(bookEntity, null) == null
        session.getCachedEntry(statelessEntity, 1L) == null

        when:
        session.cacheEntry(bookEntity, 1L, [a: 1])

        then:
        session.getCachedEntry(bookEntity, 1L) == [a: 1]
        session.getCachedEntry(bookEntity, 1L, true) == [a: 1]
        session.getCachedEntry(bookEntity, 1L, false) == [a: 1]

        when:
        session.clear()

        then:
        session.getCachedEntry(bookEntity, 1L) == null
    }

    void "collection caching is keyed by entity, key and collection name"() {
        when:
        session.cacheCollection(bookEntity, null, ['x'], 'tags')
        session.cacheCollection(bookEntity, 1L, null, 'tags')
        session.cacheCollection(bookEntity, 1L, ['x'], null)
        session.cacheCollection(statelessEntity, 1L, ['x'], 'tags')

        then:
        session.getCachedCollection(bookEntity, 1L, 'tags') == null
        session.getCachedCollection(bookEntity, null, 'tags') == null
        session.getCachedCollection(bookEntity, 1L, null) == null
        session.getCachedCollection(statelessEntity, 1L, 'tags') == null

        when:
        session.cacheCollection(bookEntity, 1L, ['x'], 'tags')

        then:
        session.getCachedCollection(bookEntity, 1L, 'tags') == ['x']
        session.getCachedCollection(bookEntity, 2L, 'tags') == null
        session.getCachedCollection(bookEntity, 1L, 'other') == null
        session.getCachedCollection(tagEntity, 1L, 'tags') == null
        session.firstLevelCollectionCache.keySet()*.toString() == [ASBook.name + ':1:tags']
    }

    void "a stateless session never caches"() {
        given:
        TestSession stateless = new TestSession(datastore, mappingContext, publisher, true)
        stateless.persisters[ASBook] = new StoringPersister(mappingContext, bookEntity, stateless, publisher)
        ASBook book = new ASBook(id: 1L)

        when:
        stateless.cacheInstance(ASBook, 1L, book)
        stateless.cacheEntry(bookEntity, 1L, [a: 1])

        then:
        stateless.stateless
        stateless.isStateless(bookEntity)
        !stateless.contains(book)
        stateless.getCachedInstance(ASBook, 1L) == null
        stateless.getCachedEntry(bookEntity, 1L) == null

        when:
        stateless.clear(book)

        then:
        noExceptionThrown()
    }

    void "pending operations are registered and flushed in order with cascades"() {
        given:
        List<String> log = []
        ASBook book = new ASBook(name: 'flushed')
        ASBook other = new ASBook(id: 8L, name: 'other')
        EntityAccess access = session.createEntityAccess(bookEntity, book)
        PendingInsertAdapter insert = new PendingInsertAdapter<Object, Long>(bookEntity, null, [:], access) {
            void run() { log << 'insert' }
        }
        insert.addPreOperation(new PendingOperationAdapter<Object, Long>(bookEntity, null, [:]) {
            void run() { log << 'pre' }
        })
        insert.addCascadeOperation(new PendingOperationAdapter<Object, Long>(bookEntity, null, [:]) {
            void run() { log << 'cascade' }
        })
        PendingUpdateAdapter update = new PendingUpdateAdapter<Object, Long>(bookEntity, 8L, [:], session.createEntityAccess(bookEntity, other)) {
            void run() { log << 'update' }
        }
        PendingDeleteAdapter delete = new PendingDeleteAdapter<Object, Long>(bookEntity, 8L, other) {
            void run() { log << 'delete' }
        }

        when:
        session.addPendingInsert(insert)
        session.addPendingUpdate(update)
        session.addPendingDelete(delete)
        session.addPostFlushOperation({ log << 'post' } as Runnable)
        session.addPostFlushOperation(null)
        session.cacheCollection(bookEntity, 1L, ['x'], 'tags')

        then:
        session.isPendingAlready(book)
        session.isPendingAlready(other)
        !session.isPendingAlready(new ASBook())
        session.pendingInserts[bookEntity].toList() == [insert]
        session.pendingUpdates[bookEntity].toList() == [update]
        session.pendingDeletes[bookEntity].toList() == [delete]

        when:
        session.flush()

        then:
        log == ['pre', 'insert', 'cascade', 'update', 'delete', 'post']
        session.pendingInserts.isEmpty()
        session.pendingUpdates.isEmpty()
        session.pendingDeletes.isEmpty()
        session.postFlushOperations.isEmpty()
        session.postFlushed == [true]
        !session.isPendingAlready(book)
        session.getCachedCollection(bookEntity, 1L, 'tags') == null

        when: 'nothing is pending'
        session.flush()

        then:
        session.postFlushed == [true, false]
    }

    void "a failing pending operation poisons the session until it is cleared"() {
        given:
        PendingInsertAdapter failing = new PendingInsertAdapter<Object, Long>(bookEntity, null, [:], null) {
            void run() { throw new IllegalStateException('boom') }
        }
        session.addPendingInsert(failing)

        when:
        session.flush()

        then:
        IllegalStateException e = thrown()
        e.message == 'boom'
        session.flushMode == FlushModeType.COMMIT
        session.pendingInserts.isEmpty()

        when:
        session.flush()

        then:
        InvalidDataAccessResourceUsageException poisoned = thrown()
        poisoned.message == 'Do not flush() the Session after an exception occurs'

        when:
        session.clear()
        session.flush()

        then:
        noExceptionThrown()
    }

    void "a failing post flush operation poisons the session as well"() {
        given:
        session.addPendingInsert(new PendingInsertAdapter<Object, Long>(bookEntity, null, [:], null) {
            void run() { }
        })
        session.addPostFlushOperation({ throw new IllegalStateException('post boom') } as Runnable)

        when:
        session.flush()

        then:
        thrown(IllegalStateException)
        session.flushMode == FlushModeType.COMMIT

        when:
        session.flush()

        then:
        thrown(InvalidDataAccessResourceUsageException)
    }

    void "dirty checking uses the DirtyCheckable state or the persister"() {
        given:
        ASDirtyBook dirty = new ASDirtyBook(name: 'x')
        PersistentEntity dirtyEntity = mappingContext.addPersistentEntity(ASDirtyBook)
        session.persisters[ASDirtyBook] = new StoringPersister(mappingContext, dirtyEntity, session, publisher)

        expect:
        !session.isDirty(null)
        !session.isDirty('unknown')
        session.isDirty(dirty)
        !session.isDirty(new ASBook(id: 1L))

        when:
        dirty.trackChanges()

        then:
        !session.isDirty(dirty)

        when:
        dirty.name = 'changed'

        then:
        session.isDirty(dirty)
    }

    void "object identifiers come from the persister"() {
        expect:
        session.getObjectIdentifier('unknown') == null
        session.getObjectIdentifier(new ASBook(id: 3L)) == 3L
        session.getObjectIdentifier(new ASBook()) == null
    }

    void "transactions are started through the subclass and remembered"() {
        given:
        Transaction stubbed = Stub(Transaction)
        session.transactionFactory = { stubbed }

        when:
        session.getTransaction()

        then:
        NoTransactionException e = thrown()
        e.message == 'Transaction not started. Call beginTransaction() first'
        !session.hasTransaction()

        when:
        Transaction transaction = session.beginTransaction()

        then:
        transaction.is(stubbed)
        session.getTransaction().is(transaction)
        session.hasTransaction()
        session.beginTransaction(null).is(session.transaction)
    }

    void "entity attributes are stored per instance and cleared on disconnect"() {
        given:
        ASBook book = new ASBook()

        when:
        session.setAttribute(null, 'a', 1)
        session.setAttribute(book, 'a', 1)
        session.setAttribute(book, 'b', 2)
        session.setAttribute(book, 'b', null)
        session.setAttribute(book, null, 3)

        then:
        session.getAttribute(book, 'a') == 1
        session.getAttribute(book, 'b') == null
        session.getAttribute(book, null) == null
        session.getAttribute(null, 'a') == null
        session.getAttribute(new ASBook(), 'a') == null

        when:
        session.setSessionProperty('p', 'v')

        then:
        session.getSessionProperty('p') == 'v'
        session.clearSessionProperty('p') == 'v'
        session.getSessionProperty('p') == null

        when:
        session.removeAttributesForEntity(book)

        then:
        session.getAttribute(book, 'a') == null

        when:
        session.setAttribute(book, 'a', 1)
        DatastoreUtils.bindSession(session)
        session.disconnect()

        then:
        !session.connected
        session.getAttribute(book, 'a') == null
        TransactionSynchronizationManager.getResource(datastore) == null

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    void "disconnect leaves a holder that still contains other sessions bound"() {
        given:
        TestSession other = new TestSession(datastore, mappingContext, publisher)
        DatastoreUtils.bindSession(other)
        DatastoreUtils.bindNewSession(session)

        when:
        session.disconnect()

        then:
        SessionHolder holder = (SessionHolder) TransactionSynchronizationManager.getResource(datastore)
        holder != null
        holder.containsSession(other)
        !holder.containsSession(session)

        cleanup:
        TransactionSynchronizationManager.unbindResourceIfPossible(datastore)
    }

    static class TestSession extends AbstractSession<Object> {

        Map<Class, Persister> persisters = [:]
        List<Class> created = []
        List<Boolean> postFlushed = []

        TestSession(Datastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher) {
            super(datastore, mappingContext, publisher)
        }

        TestSession(Datastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher, boolean stateless) {
            super(datastore, mappingContext, publisher, stateless)
        }

        @Override
        protected Persister createPersister(Class cls, MappingContext mappingContext) {
            created << cls
            persisters[cls]
        }

        Closure<Transaction> transactionFactory = { null }

        @Override
        protected Transaction beginTransactionInternal() {
            transactionFactory.call()
        }

        @Override
        protected void postFlush(boolean hasUpdates) {
            postFlushed << hasUpdates
        }

        @Override
        Object getNativeInterface() {
            null
        }
    }

    static class StoringPersister extends EntityPersister {

        Map<Serializable, Object> store = [:]
        List<Serializable> retrieved = []
        List deleted = []
        List deletedAll = []
        List refreshed = []
        Query query
        long nextId = 0

        StoringPersister(MappingContext mappingContext, PersistentEntity entity, Session session, ApplicationEventPublisher publisher) {
            super(mappingContext, entity, session, publisher)
        }

        @Override
        protected List<Object> retrieveAllEntities(PersistentEntity pe, Serializable[] keys) {
            keys.collect { retrieveEntity(pe, it) }
        }

        @Override
        protected List<Object> retrieveAllEntities(PersistentEntity pe, Iterable<Serializable> keys) {
            keys.collect { retrieveEntity(pe, it) }
        }

        @Override
        protected List<Serializable> persistEntities(PersistentEntity pe, Iterable objs) {
            objs.collect { persistEntity(pe, it) }
        }

        @Override
        protected Object retrieveEntity(PersistentEntity pe, Serializable key) {
            retrieved << key
            Object stored = store[key]
            stored == null ? null : pe.javaClass.newInstance(id: stored.id, name: stored.name)
        }

        @Override
        protected Serializable persistEntity(PersistentEntity pe, Object obj) {
            EntityAccess access = createEntityAccess(pe, obj)
            if (access.identifier == null) {
                access.setIdentifier(++nextId)
            }
            store[(Serializable) access.identifier] = obj
            (Serializable) access.identifier
        }

        @Override
        protected void deleteEntity(PersistentEntity pe, Object obj) {
            deleted << obj
        }

        @Override
        protected void deleteEntities(PersistentEntity pe, Iterable objects) {
            deletedAll << objects.toList()
        }

        @Override
        Object proxy(Serializable key) {
            'proxy-' + key
        }

        @Override
        Serializable refresh(Object o) {
            refreshed << o
            getObjectIdentifier(o)
        }

        @Override
        Query createQuery() {
            query
        }
    }
}

class ASBook {
    Long id
    String name

    @Override
    boolean equals(Object o) {
        o instanceof ASBook && o.id == id && o.name == name
    }

    @Override
    int hashCode() {
        Objects.hash(id, name)
    }
}

class ASTag {
    Long id
    String name
}

class ASStatelessBook {
    Long id
    String name
}

class ASDirtyBook implements DirtyCheckable {
    Long id
    String name

    void setName(String name) {
        markDirty('name')
        this.name = name
    }
}
