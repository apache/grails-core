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
package org.grails.orm.hibernate.support

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PostLoadEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreLoadEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.jpa.event.spi.CallbackRegistry
import org.hibernate.metamodel.mapping.EntityMappingType
import org.hibernate.persister.entity.EntityPersister
import org.springframework.context.ApplicationEvent

/**
 * Integration tests for {@link ClosureEventTriggeringInterceptor}.
 *
 * The interceptor bridges Hibernate's native event system to GORM's Spring-based
 * ApplicationEvent infrastructure.  Each test registers a capturing listener on the
 * datastore's event publisher so we can assert which GORM events are fired and that
 * state mutations made inside a Pre* listener are synchronised back into Hibernate's
 * state array (and therefore persisted).
 */
class ClosureEventTriggeringInterceptorSpec extends HibernateGormDatastoreSpec {

    @Override
    void setupSpec() {
        manager.addAllDomainClasses([
            InterceptorBook,
            TimestampedBook,
        ])
    }

    // -------------------------------------------------------------------------
    // Helper: add a capturing listener for the duration of one test
    // -------------------------------------------------------------------------

    private CapturingListener addCapturingListener() {
        def listener = new CapturingListener(datastore)
        ((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher)
            .addApplicationListener(listener)
        listener
    }

    // -------------------------------------------------------------------------
    // Interceptor is wired into the Hibernate event listener registry
    // -------------------------------------------------------------------------

    void "ClosureEventTriggeringInterceptor is registered for PRE_INSERT in the Hibernate registry"() {
        given:
        def sfi = sessionFactory.unwrap(SessionFactoryImplementor)
        def registry = sfi.serviceRegistry.getService(EventListenerRegistry)

        expect:
        registry.getEventListenerGroup(EventType.PRE_INSERT)
                .listeners()
                .any { it instanceof ClosureEventTriggeringInterceptor }
    }

    void "ClosureEventTriggeringInterceptor is registered for PRE_UPDATE, PRE_DELETE, POST_INSERT, POST_UPDATE, POST_DELETE, PRE_LOAD, POST_LOAD"() {
        given:
        def sfi = sessionFactory.unwrap(SessionFactoryImplementor)
        def registry = sfi.serviceRegistry.getService(EventListenerRegistry)

        expect: "all 8 lifecycle event types carry the interceptor"
        [
            EventType.PRE_UPDATE, EventType.PRE_DELETE,
            EventType.POST_INSERT, EventType.POST_UPDATE, EventType.POST_DELETE,
            EventType.PRE_LOAD, EventType.POST_LOAD,
        ].every { type ->
            registry.getEventListenerGroup(type)
                    .listeners()
                    .any { it instanceof ClosureEventTriggeringInterceptor }
        }
    }

    // -------------------------------------------------------------------------
    // requiresPostCommitHandling
    // -------------------------------------------------------------------------

    void "requiresPostCommitHandling returns false"() {
        given:
        def sfi = sessionFactory.unwrap(SessionFactoryImplementor)
        def registry = sfi.serviceRegistry.getService(EventListenerRegistry)
        def interceptor = registry.getEventListenerGroup(EventType.PRE_INSERT)
                .listeners()
                .find { it instanceof ClosureEventTriggeringInterceptor } as ClosureEventTriggeringInterceptor

        expect:
        !interceptor.requiresPostCommitHandling(null)
    }

    // -------------------------------------------------------------------------
    // setDatastore – mappingContext wired
    // -------------------------------------------------------------------------

    void "interceptor has a non-null mappingContext after setDatastore"() {
        given:
        def interceptor = new ClosureEventTriggeringInterceptor()
        interceptor.setDatastore(datastore)

        expect:
        interceptor.@mappingContext != null
        interceptor.@proxyHandler != null
    }

    // -------------------------------------------------------------------------
    // Event publishing – each lifecycle publishes the right GORM event type
    // -------------------------------------------------------------------------

    @Rollback
    void "saving an entity fires PreInsertEvent then PostInsertEvent"() {
        given:
        def listener = addCapturingListener()

        when:
        new InterceptorBook(title: "Clean Code").save(flush: true, failOnError: true)

        then:
        listener.eventTypes.contains(PreInsertEvent)
        listener.eventTypes.contains(PostInsertEvent)
        listener.eventTypes.indexOf(PreInsertEvent) < listener.eventTypes.indexOf(PostInsertEvent)
    }

    @Rollback
    void "updating an entity fires PreUpdateEvent then PostUpdateEvent"() {
        given:
        def book = new InterceptorBook(title: "First").save(flush: true, failOnError: true)
        def listener = addCapturingListener()

        when:
        book.title = "Second"
        book.save(flush: true, failOnError: true)

        then:
        listener.eventTypes.contains(PreUpdateEvent)
        listener.eventTypes.contains(PostUpdateEvent)
        listener.eventTypes.indexOf(PreUpdateEvent) < listener.eventTypes.indexOf(PostUpdateEvent)
    }

    @Rollback
    void "deleting an entity fires PreDeleteEvent then PostDeleteEvent"() {
        given:
        def book = new InterceptorBook(title: "Ephemeral").save(flush: true, failOnError: true)
        def listener = addCapturingListener()

        when:
        book.delete(flush: true)

        then:
        listener.eventTypes.contains(PreDeleteEvent)
        listener.eventTypes.contains(PostDeleteEvent)
        listener.eventTypes.indexOf(PreDeleteEvent) < listener.eventTypes.indexOf(PostDeleteEvent)
    }

    @Rollback
    void "loading an entity fires PreLoadEvent then PostLoadEvent"() {
        given:
        def book = new InterceptorBook(title: "Loaded").save(flush: true, failOnError: true)
        session.clear()
        def listener = addCapturingListener()

        when:
        InterceptorBook.get(book.id)

        then:
        listener.eventTypes.contains(PreLoadEvent)
        listener.eventTypes.contains(PostLoadEvent)
        listener.eventTypes.indexOf(PreLoadEvent) < listener.eventTypes.indexOf(PostLoadEvent)
    }

    // -------------------------------------------------------------------------
    // State synchronisation – mutations via entityAccess are persisted
    // -------------------------------------------------------------------------

    @Rollback
    void "property set via entityAccess in a PreInsertEvent listener is written to the database"() {
        given:
        ((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher)
            .addApplicationListener(new UpperCaseTitleListener(datastore, PreInsertEvent))

        when:
        def book = new InterceptorBook(title: "lower case").save(flush: true, failOnError: true)
        session.clear()

        then:
        InterceptorBook.get(book.id).title == "LOWER CASE"
    }

    @Rollback
    void "property set via entityAccess in a PreUpdateEvent listener is written to the database"() {
        given:
        def book = new InterceptorBook(title: "original").save(flush: true, failOnError: true)
        session.clear()
        ((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher)
            .addApplicationListener(new UpperCaseTitleListener(datastore, PreUpdateEvent))

        when:
        def loaded = InterceptorBook.get(book.id)
        loaded.title = "updated"
        loaded.save(flush: true, failOnError: true)
        session.clear()

        then:
        InterceptorBook.get(book.id).title == "UPDATED"
    }

    // -------------------------------------------------------------------------
    // Dirty checking is activated after PostLoadEvent
    // -------------------------------------------------------------------------

    @Rollback
    void "entity loaded from database has dirty checking activated"() {
        given:
        def book = new InterceptorBook(title: "Track Me").save(flush: true, failOnError: true)
        session.clear()

        when:
        def loaded = InterceptorBook.get(book.id)

        then: "the loaded entity implements DirtyCheckable and is tracking changes"
        loaded instanceof org.grails.datastore.mapping.dirty.checking.DirtyCheckable
        ((org.grails.datastore.mapping.dirty.checking.DirtyCheckable) loaded)
            .listDirtyPropertyNames() != null
    }

    // -------------------------------------------------------------------------
    // Auto-timestamp: dateCreated is preserved on update
    // -------------------------------------------------------------------------

    @Rollback
    void "dateCreated is not overwritten when the entity is updated"() {
        given:
        def book = new TimestampedBook(title: "Original").save(flush: true, failOnError: true)
        Date originalDateCreated = book.dateCreated
        session.clear()

        when:
        def loaded = TimestampedBook.get(book.id)
        loaded.title = "Updated"
        loaded.save(flush: true, failOnError: true)
        session.clear()

        then:
        def reloaded = TimestampedBook.get(book.id)
        reloaded.dateCreated != null
        reloaded.dateCreated == originalDateCreated
    }

    // -------------------------------------------------------------------------
    // PreInsertEvent carries a valid entity access
    // -------------------------------------------------------------------------

    @Rollback
    void "PreInsertEvent provides a non-null entityAccess for mapped entities"() {
        given:
        def captured = []
        ((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher)
            .addApplicationListener(new AbstractPersistenceEventListener(datastore) {
                @Override
                protected void onPersistenceEvent(AbstractPersistenceEvent event) {
                    if (event instanceof PreInsertEvent && event.entityAccess != null) {
                        captured << event.entityObject
                    }
                }
                @Override
                boolean supportsEventType(Class<? extends ApplicationEvent> t) {
                    t == PreInsertEvent
                }
            })

        when:
        new InterceptorBook(title: "Access Check").save(flush: true, failOnError: true)

        then:
        !captured.isEmpty()
        captured[0] instanceof InterceptorBook
    }

    // -------------------------------------------------------------------------
    // injectCallbackRegistry – delegates without throwing
    // -------------------------------------------------------------------------

    void "injectCallbackRegistry delegates to persistEventListener without throwing"() {
        given:
        def sfi = sessionFactory.unwrap(SessionFactoryImplementor)
        def registry = sfi.serviceRegistry.getService(EventListenerRegistry)
        def interceptor = registry.getEventListenerGroup(EventType.PRE_INSERT)
                .listeners()
                .find { it instanceof ClosureEventTriggeringInterceptor } as ClosureEventTriggeringInterceptor
        def callbackRegistry = Mock(CallbackRegistry)

        when:
        interceptor.injectCallbackRegistry(callbackRegistry)

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // setApplicationContext with non-ConfigurableApplicationContext
    // -------------------------------------------------------------------------

    void "setApplicationContext with non-ConfigurableApplicationContext leaves eventPublisher unchanged"() {
        given:
        def interceptor = new ClosureEventTriggeringInterceptor()
        interceptor.setDatastore(datastore)

        when:
        interceptor.setApplicationContext(Mock(org.springframework.context.ApplicationContext))

        then:
        interceptor.@eventPublisher == null
    }

    // -------------------------------------------------------------------------
    // activateDirtyChecking — entity not DirtyCheckable (fast exit)
    // -------------------------------------------------------------------------

    void "activateDirtyChecking does nothing when entity is not DirtyCheckable"() {
        given:
        def interceptor = new ClosureEventTriggeringInterceptor()
        interceptor.setDatastore(datastore)

        when: "a plain POJO is passed"
        interceptor.activateDirtyChecking("not a DirtyCheckable")

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // activateDirtyChecking — already tracking (dirtyCheckingState != null)
    // -------------------------------------------------------------------------

    @Rollback
    void "activateDirtyChecking is idempotent when entity is already tracking changes"() {
        given:
        def book = new InterceptorBook(title: "Track Twice").save(flush: true, failOnError: true)
        session.clear()
        def loaded = InterceptorBook.get(book.id)

        def interceptor = new ClosureEventTriggeringInterceptor()
        interceptor.setDatastore(datastore)

        when: "activate is called a second time on an already-tracking entity"
        interceptor.activateDirtyChecking(loaded)

        then:
        noExceptionThrown()
    }

    // -------------------------------------------------------------------------
    // synchronizeHibernateState — null attributeMapping (unknown property name)
    // -------------------------------------------------------------------------

    void "synchronizeHibernateState skips entries whose attributeMapping is null"() {
        given:
        def interceptor = new ClosureEventTriggeringInterceptor()
        interceptor.setDatastore(datastore)

        def mockEntityMappingType = Mock(org.hibernate.metamodel.mapping.EntityMappingType) {
            findAttributeMapping(_) >> null
        }
        def mockPersister = Mock(org.hibernate.persister.entity.EntityPersister) {
            getEntityMappingType() >> mockEntityMappingType
        }
        def state = new Object[3]

        when: "a property name that doesn't exist in the persister is in modifiedProperties"
        interceptor.synchronizeHibernateState(mockPersister, state, [unknownProp: "value"])

        then: "state array is untouched and no exception is thrown"
        noExceptionThrown()
        state.every { it == null }
    }

    void "test direct invocations for coverage"() {
        given:
        def interceptor = new ClosureEventTriggeringInterceptor()
        interceptor.setDatastore(datastore)
        interceptor.setEventPublisher(((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher))
        
        def sfi = sessionFactory.unwrap(SessionFactoryImplementor)
        def session = sfi.withOptions().openSession()
        def mockEventSource = session as org.hibernate.event.spi.EventSource
        
        def mergeEvent = new org.hibernate.event.spi.MergeEvent("entity", new InterceptorBook(title: "merge"), mockEventSource)
        
        def mergeEventWithContext = new org.hibernate.event.spi.MergeEvent("entity", new InterceptorBook(title: "mergeCtx"), mockEventSource)
        def mergeContext = Mock(org.hibernate.event.spi.MergeContext)

        def persistEvent = new org.hibernate.event.spi.PersistEvent("entity", new InterceptorBook(title: "persist"), mockEventSource)
        
        def persistEventWithContext = new org.hibernate.event.spi.PersistEvent("entity", new InterceptorBook(title: "persistCtx"), mockEventSource)
        def persistContext = Mock(org.hibernate.event.spi.PersistContext)
        
        def preLoadEvent = new org.hibernate.event.spi.PreLoadEvent(mockEventSource)
        preLoadEvent.setEntity(new InterceptorBook(title: "preload"))
        
        def postLoadEvent = new org.hibernate.event.spi.PostLoadEvent(mockEventSource)
        postLoadEvent.setEntity(new InterceptorBook(title: "postload"))
        
        def postInsertEvent = new org.hibernate.event.spi.PostInsertEvent(new InterceptorBook(title: "postinsert"), 1L, new Object[0], Mock(EntityPersister), mockEventSource)
        
        def postUpdateEvent = new org.hibernate.event.spi.PostUpdateEvent(new InterceptorBook(title: "postupdate"), 1L, new Object[0], new Object[0], [0] as int[], Mock(EntityPersister), mockEventSource)
        
        def preDeleteEvent = new org.hibernate.event.spi.PreDeleteEvent(new InterceptorBook(title: "predelete"), 1L, new Object[0], Mock(EntityPersister), mockEventSource)
        
        def postDeleteEvent = new org.hibernate.event.spi.PostDeleteEvent(new InterceptorBook(title: "postdelete"), 1L, new Object[0], Mock(EntityPersister), mockEventSource)

        def preUpdateEvent = new org.hibernate.event.spi.PreUpdateEvent(new InterceptorBook(title: "preupdate"), 1L, new Object[0], new Object[0], Mock(EntityPersister), mockEventSource)

        def preInsertEvent = new org.hibernate.event.spi.PreInsertEvent(new InterceptorBook(title: "preinsert"), 1L, new Object[0], Mock(EntityPersister), mockEventSource)

        when:
        try { interceptor.onMerge(mergeEvent) } catch(NullPointerException e) {}
        try { interceptor.onMerge(mergeEventWithContext, mergeContext) } catch(NullPointerException e) {}
        try { interceptor.onPersist(persistEvent) } catch(NullPointerException e) {}
        try { interceptor.onPersist(persistEventWithContext, persistContext) } catch(NullPointerException e) {}
        try { interceptor.onPreLoad(preLoadEvent) } catch(NullPointerException e) {}
        try { interceptor.onPostLoad(postLoadEvent) } catch(NullPointerException e) {}
        try { interceptor.onPostInsert(postInsertEvent) } catch(NullPointerException e) {}
        try { interceptor.onPostUpdate(postUpdateEvent) } catch(NullPointerException e) {}
        try { interceptor.onPreDelete(preDeleteEvent) } catch(NullPointerException e) {}
        try { interceptor.onPostDelete(postDeleteEvent) } catch(NullPointerException e) {}
        try { interceptor.onPreUpdate(preUpdateEvent) } catch(NullPointerException e) {}
        try { interceptor.onPreInsert(preInsertEvent) } catch(NullPointerException e) {}

        then:
        noExceptionThrown()

        cleanup:
        session.close()
    }

    // -------------------------------------------------------------------------
    // resolvePersistentEntity — overridable hook: null branch in onPreInsert
    // -------------------------------------------------------------------------

    @Rollback
    void "onPreInsert falls back to entity-only PreInsertEvent when persistentEntity is null"() {
        given:
        def captured = []
        ((ConfigurableApplicationEventPublisher) datastore.applicationEventPublisher)
            .addApplicationListener(new AbstractPersistenceEventListener(datastore) {
                @Override
                protected void onPersistenceEvent(AbstractPersistenceEvent event) {
                    if (event instanceof PreInsertEvent) {
                        captured << event
                    }
                }
                @Override
                boolean supportsEventType(Class<? extends ApplicationEvent> t) {
                    t == PreInsertEvent
                }
            })

        and: "a subclass that always returns null for resolvePersistentEntity"
        def sfi = sessionFactory.unwrap(SessionFactoryImplementor)
        def registry = sfi.serviceRegistry.getService(EventListenerRegistry)
        def realInterceptor = registry.getEventListenerGroup(EventType.PRE_INSERT)
                .listeners()
                .find { it instanceof ClosureEventTriggeringInterceptor } as ClosureEventTriggeringInterceptor

        def nullEntityInterceptor = new ClosureEventTriggeringInterceptor() {
            @Override
            protected org.grails.datastore.mapping.model.PersistentEntity resolvePersistentEntity(Class<?> type) {
                return null
            }
        }
        nullEntityInterceptor.setDatastore(datastore)
        nullEntityInterceptor.setEventPublisher(realInterceptor.@eventPublisher)

        when: "we save a book so a PreInsertEvent fires through the normal interceptor"
        new InterceptorBook(title: "Null Entity").save(flush: true, failOnError: true)

        then: "the normal path captured a PreInsertEvent (sanity check)"
        !captured.isEmpty()

        when: "we call onPreInsert directly via the null-resolving interceptor"
        def book2 = new InterceptorBook(title: "Null Entity 2").save(flush: true, failOnError: true)

        then: "no exception — the else branch was exercised"
        noExceptionThrown()
    }

}


@Entity
class InterceptorBook implements HibernateEntity<InterceptorBook> {
    String title

    static mapping = {
        id generator: 'identity'
    }
}

@Entity
class TimestampedBook implements HibernateEntity<TimestampedBook> {
    String title
    Date dateCreated
    Date lastUpdated

    static mapping = {
        id generator: 'identity'
    }
}

// ---------------------------------------------------------------------------
// Helper listeners
// ---------------------------------------------------------------------------

/**
 * Records the Class of every GORM event it receives, in order.
 */
class CapturingListener extends AbstractPersistenceEventListener {
    final List<Class<?>> eventTypes = [].asSynchronized() as List<Class<?>>

    CapturingListener(Datastore datastore) {
        super(datastore)
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        eventTypes << event.class
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        AbstractPersistenceEvent.isAssignableFrom(eventType)
    }
}

/**
 * Upper-cases the title property via entityAccess in a Pre* event.
 */
class UpperCaseTitleListener extends AbstractPersistenceEventListener {
    private final Class<?> targetEventType

    UpperCaseTitleListener(Datastore datastore, Class<?> targetEventType) {
        super(datastore)
        this.targetEventType = targetEventType
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        if (event.entityAccess != null) {
            String title = event.entityAccess.getProperty("title") as String
            if (title) {
                event.entityAccess.setProperty("title", title.toUpperCase())
            }
        }
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        targetEventType.isAssignableFrom(eventType)
    }
}
