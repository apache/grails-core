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
package org.grails.datastore.mapping.engine

import java.sql.Timestamp

import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import org.grails.datastore.mapping.cache.TPCacheAdapter
import org.grails.datastore.mapping.cache.TPCacheAdapterRepository
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionImplementor
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PostLoadEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreLoadEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.proxy.ProxyFactory

class EntityPersisterSpec extends Specification {

    MappingContext mappingContext = new KeyValueMappingContext('test')
    PersistentEntity entity = mappingContext.addPersistentEntity(EPBook)
    Datastore datastore = Stub(Datastore) { getMappingContext() >> mappingContext }
    Session session = Mock(Session, additionalInterfaces: [SessionImplementor]) {
        getDatastore() >> datastore
        createEntityAccess(_, _) >> { PersistentEntity pe, Object obj -> mappingContext.createEntityAccess(pe, obj) }
    }
    ApplicationEventPublisher publisher = Mock(ApplicationEventPublisher)
    TestPersister persister = new TestPersister(mappingContext, entity, session, publisher)

    private EntityAccess accessFor(Object instance, PersistentEntity pe = entity) {
        mappingContext.createEntityAccess(pe, instance)
    }

    void "the persister exposes its collaborators"() {
        expect:
        persister.session.is(session)
        persister.mappingContext.is(mappingContext)
        persister.persistentEntity.is(entity)
        persister.type == EPBook
        persister.proxyFactory.is(mappingContext.proxyFactory)
        LockableEntityPersister.DEFAULT_TIMEOUT == 30
    }

    void "proxies are created for the converted identifier"() {
        given:
        ProxyFactory proxyFactory = Mock(ProxyFactory)
        mappingContext.proxyFactory = proxyFactory
        EPBook proxy = new EPBook()

        when:
        Object created = persister.proxy('5')

        then:
        1 * proxyFactory.createProxy(session, EPBook, 5L) >> proxy
        created.is(proxy)
    }

    void "object identifiers are read from proxies, the reflector or another persister"() {
        given:
        ProxyFactory proxyFactory = Mock(ProxyFactory)
        mappingContext.proxyFactory = proxyFactory
        EPBook book = new EPBook(id: 3L)
        EPOther other = new EPOther(id: 8L)
        PersistentEntity otherEntity = mappingContext.addPersistentEntity(EPOther)
        TestPersister otherPersister = new TestPersister(mappingContext, otherEntity, session, publisher)

        expect:
        persister.getObjectIdentifier(null) == null

        when:
        Serializable fromProxy = persister.getObjectIdentifier('proxy')

        then:
        1 * proxyFactory.isProxy('proxy') >> true
        1 * proxyFactory.getIdentifier('proxy') >> 99L
        fromProxy == 99L

        when:
        Serializable fromReflector = persister.getObjectIdentifier(book)

        then:
        _ * proxyFactory.isProxy(_) >> false
        fromReflector == 3L

        when:
        Serializable delegated = persister.getObjectIdentifier(other)

        then:
        1 * session.getPersister(other) >> otherPersister
        delegated == 8L

        when:
        Serializable unknown = persister.getObjectIdentifier(other)

        then:
        1 * session.getPersister(other) >> null
        unknown == null
    }

    void "persist and insert delegate to persistEntity for instances of the entity"() {
        given:
        EPBook book = new EPBook()

        when:
        Serializable persisted = persister.persist(book)
        Serializable inserted = persister.insert(book)

        then:
        persisted == 1L
        inserted == 2L
        persister.persisted == [[book, false], [book, true]]
    }

    void "persist and insert delegate other objects to their own persister"() {
        given:
        EPOther other = new EPOther()
        Persister otherPersister = Mock(Persister)

        when:
        Serializable persisted = persister.persist(other)
        Serializable inserted = persister.insert(other)

        then:
        2 * session.getPersister(other) >> otherPersister
        1 * otherPersister.persist(other) >> 10L
        1 * otherPersister.persist(other) >> 11L
        persisted == 10L
        inserted == 11L
    }

    void "persisting an unsupported object fails"() {
        given:
        session.getPersister(_) >> null

        when:
        persister.persist('nope')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Object [nope] is not an instance supported by the persister for class [' + EPBook.name + ']'

        when:
        persister.insert('nope')

        then:
        e = thrown()
        e.message == 'Object [nope] is not an instance supported by the persister for class [' + EPBook.name + ']'
    }

    void "bulk operations delegate to the abstract hooks"() {
        given:
        List books = [new EPBook(), new EPBook()]

        expect:
        persister.persist(books) == [1L, 2L]
        persister.retrieveAll([1L, 2L]) == ['entity-1', 'entity-2']
        persister.retrieveAll([3L] as Serializable[]) == ['entity-3']
        persister.retrieve(4L) == 'entity-4'
        persister.retrieve(null) == null
        persister.retrieve(-1L) == null

        when:
        persister.delete((Object) null)
        persister.delete((Iterable) null)
        persister.delete(books[0])
        persister.delete(books)

        then:
        persister.deleted == [books[0]]
        persister.deletedAll == [books]
    }

    void "the object identifier can be assigned through the entity access"() {
        given:
        EPBook book = new EPBook()

        when:
        persister.setObjectIdentifier(book, 21L)

        then:
        book.id == 21L
    }

    void "new instances publish a pre-load event"() {
        given:
        List<AbstractPersistenceEvent> events = []

        when:
        Object instance = persister.newEntityInstance(entity)

        then:
        instance instanceof EPBook
        1 * publisher.publishEvent(_ as PreLoadEvent) >> { AbstractPersistenceEvent e -> events << e }
        events[0].source.is(datastore)
        events[0].entity.is(entity)
        events[0].entityObject.is(instance)
    }

    void "cancellation hooks publish the pre events and report cancellation"() {
        given:
        EntityAccess access = accessFor(new EPBook())

        when:
        boolean insert = persister.cancelInsert(entity, access)
        boolean update = persister.cancelUpdate(entity, access)
        boolean delete = persister.cancelDelete(entity, access)
        boolean load = persister.cancelLoad(entity, access)

        then:
        1 * publisher.publishEvent(_ as PreInsertEvent)
        1 * publisher.publishEvent(_ as PreUpdateEvent)
        1 * publisher.publishEvent(_ as PreDeleteEvent)
        1 * publisher.publishEvent(_ as PreLoadEvent)
        !insert
        !update
        !delete
        !load

        when:
        boolean cancelled = persister.cancelInsert(entity, access)

        then:
        1 * publisher.publishEvent(_ as PreInsertEvent) >> { AbstractPersistenceEvent e -> e.cancel() }
        cancelled
    }

    void "post events are published with the datastore, entity and access"() {
        given:
        EntityAccess access = accessFor(new EPBook())

        when:
        persister.firePostInsertEvent(entity, access)
        persister.firePostUpdateEvent(entity, access)
        persister.firePostDeleteEvent(entity, access)
        persister.firePreLoadEvent(entity, access)
        persister.firePostLoadEvent(entity, access)

        then:
        1 * publisher.publishEvent({ it instanceof PostInsertEvent && it.source.is(datastore) && it.entity.is(entity) && it.entityAccess.is(access) })
        1 * publisher.publishEvent({ it instanceof PostUpdateEvent && it.entityAccess.is(access) })
        1 * publisher.publishEvent({ it instanceof PostDeleteEvent && it.entityAccess.is(access) })
        1 * publisher.publishEvent({ it instanceof PreLoadEvent && it.entityAccess.is(access) })
        1 * publisher.publishEvent({ it instanceof PostLoadEvent && it.entityAccess.is(access) })
    }

    void "versioning is detected from the entity and the version property type"() {
        given:
        PersistentEntity dateEntity = mappingContext.addPersistentEntity(EPDateVersioned)
        PersistentEntity stringEntity = mappingContext.addPersistentEntity(EPStringVersion)

        expect:
        persister.isVersioned(accessFor(new EPBook()))
        persister.isVersioned(accessFor(new EPDateVersioned(), dateEntity))
        !persister.isVersioned(accessFor(new EPStringVersion(), stringEntity))
    }

    void "the current version is normalised to a long"() {
        expect:
        persister.getCurrentVersion(accessFor(new EPBook(version: 5L))) == 5L
        persister.getCurrentVersion(accessFor(new EPBook())) == null
    }

    void "numeric versions are incremented and initialised"() {
        given:
        EPBook fresh = new EPBook()
        EPBook existing = new EPBook(version: 5L)

        when:
        persister.incrementVersion(accessFor(fresh))
        EntityPersister.incrementEntityVersion(accessFor(existing))

        then:
        fresh.version == 1L
        existing.version == 6L

        when:
        persister.setVersion(accessFor(existing))

        then:
        existing.version == 0L
    }

    void "date and timestamp versions are set to the current time"() {
        given:
        PersistentEntity dateEntity = mappingContext.addPersistentEntity(EPDateVersioned)
        PersistentEntity timestampEntity = mappingContext.addPersistentEntity(EPTimestampVersioned)
        EPDateVersioned dated = new EPDateVersioned()
        EPTimestampVersioned stamped = new EPTimestampVersioned()
        long before = System.currentTimeMillis()

        when:
        persister.incrementVersion(accessFor(dated, dateEntity))
        persister.setVersion(accessFor(stamped, timestampEntity))

        then:
        dated.version.getClass() == Date
        dated.version.time >= before
        stamped.version.getClass() == Timestamp
        stamped.version.time >= before

        when:
        persister.setDateVersion(accessFor(dated, dateEntity))

        then:
        dated.version.time >= before
    }

    void "assigned identifiers are detected from the identity mapping"() {
        expect:
        !persister.isAssignedId(entity)
        !persister.isAssignedId(mappingContext.createEmbeddedEntity(EPComponent))

        when:
        entity.identity.mapping.mappedForm.generator = 'assigned'

        then:
        persister.isAssignedId(entity)

        cleanup:
        entity.identity.mapping.mappedForm.generator = null
    }

    void "third party caching is skipped without a repository"() {
        expect:
        persister.getFromTPCache(entity, 1L) == null

        when:
        persister.updateTPCache(entity, [a: 1], 1L)

        then:
        noExceptionThrown()
    }

    void "third party caching delegates to the adapter for the entity"() {
        given:
        TPCacheAdapter adapter = Mock(TPCacheAdapter)
        TPCacheAdapterRepository repository = Mock(TPCacheAdapterRepository)
        TestPersister caching = new TestPersister(mappingContext, entity, session, publisher, repository)

        when:
        caching.updateTPCache(entity, [a: 1], 1L)
        Object cached = caching.getFromTPCache(entity, 1L)

        then:
        2 * repository.getTPCacheAdapter(entity) >> adapter
        1 * adapter.cacheEntry(1L, [a: 1])
        1 * adapter.getCachedEntry(1L) >> [a: 1]
        cached == [a: 1]

        when:
        Object missing = caching.getFromTPCache(entity, 2L)
        caching.updateTPCache(entity, [b: 2], 2L)

        then:
        2 * repository.getTPCacheAdapter(entity) >> null
        0 * adapter._
        missing == null
    }

    static class TestPersister extends ThirdPartyCacheEntityPersister<Map> {

        List persisted = []
        List deleted = []
        List deletedAll = []
        long nextId = 0

        TestPersister(MappingContext mappingContext, PersistentEntity entity, Session session, ApplicationEventPublisher publisher) {
            super(mappingContext, entity, session, publisher)
        }

        TestPersister(MappingContext mappingContext, PersistentEntity entity, SessionImplementor session, ApplicationEventPublisher publisher, TPCacheAdapterRepository<Map> repository) {
            super(mappingContext, entity, session, publisher, repository)
        }

        @Override
        protected List<Object> retrieveAllEntities(PersistentEntity pe, Serializable[] keys) {
            keys.collect { 'entity-' + it }
        }

        @Override
        protected List<Object> retrieveAllEntities(PersistentEntity pe, Iterable<Serializable> keys) {
            keys.collect { 'entity-' + it }
        }

        @Override
        protected List<Serializable> persistEntities(PersistentEntity pe, Iterable objs) {
            objs.collect { persistEntity(pe, it) }
        }

        @Override
        protected Object retrieveEntity(PersistentEntity pe, Serializable key) {
            key == -1L ? null : 'entity-' + key
        }

        @Override
        protected Serializable persistEntity(PersistentEntity pe, Object obj) {
            persistEntity(pe, obj, false)
        }

        @Override
        protected Serializable persistEntity(PersistentEntity pe, Object obj, boolean isInsert) {
            persisted << [obj, isInsert]
            ++nextId
        }

        @Override
        protected void deleteEntity(PersistentEntity pe, Object obj) {
            deleted << obj
        }

        @Override
        protected void deleteEntities(PersistentEntity pe, Iterable objects) {
            deletedAll << objects
        }

        @Override
        Object lock(Serializable id) {
            null
        }

        @Override
        Object lock(Serializable id, int timeout) {
            null
        }

        @Override
        boolean isLocked(Object o) {
            false
        }

        @Override
        void unlock(Object o) {
        }

        @Override
        Serializable refresh(Object o) {
            null
        }

        @Override
        org.grails.datastore.mapping.query.Query createQuery() {
            null
        }
    }
}

class EPBook {
    Long id
    Long version
    String title
}

class EPOther {
    Long id
    String name
}

class EPDateVersioned {
    Long id
    Date version
    String name
}

class EPTimestampVersioned {
    Long id
    Timestamp version
}

class EPStringVersion {
    Long id
    String version
}

class EPComponent {
    String value
}
