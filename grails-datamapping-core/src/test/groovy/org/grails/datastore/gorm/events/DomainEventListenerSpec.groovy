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

package org.grails.datastore.gorm.events

import java.sql.Timestamp

import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.PayloadApplicationEvent

import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.MergeEvent
import org.grails.datastore.mapping.engine.event.PersistEvent
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PostLoadEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreLoadEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.engine.event.SaveOrUpdateEvent
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties

/**
 * Note on coverage gaps left deliberately untested:
 * - {@code invokeEvent}'s {@code ea != null} branch is always true through every public before-
 *   and after-hook method, which never passes a null {@code EntityAccess}; the {@code ea == null}
 *   path is unreachable via the public API.
 * - {@code invokeEvent}'s {@code eventMethod.getParameterTypes().length == 1} branch can never be
 *   taken: {@code findAndCacheEvent} caches hooks via Spring's {@code ReflectionUtils.findMethod(Class, String)},
 *   which (confirmed via decompiling spring-core) only ever matches zero-argument methods, so a
 *   cached {@code eventMethod} can never have one parameter. Event-argument-accepting hooks appear
 *   to be an unreachable, effectively dead capability.
 * - The protected {@code DomainEventListener(ConnectionSourcesProvider, MappingContext)}
 *   constructor exists solely for subclassing (e.g. {@code grails.gorm.rx.events.DomainEventListener}),
 *   which is covered by its own module's spec; exercising it here would duplicate that coverage.
 */
class DomainEventListenerSpec extends Specification {

    void "registers itself as a mapping context listener and creates event caches for entities present at construction time"() {
        given:
        RecordingDomain domain = new RecordingDomain()
        PersistentEntity entity = entityFor(RecordingDomain)
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> [entity]
        }
        Datastore datastore = plainDatastore(mappingContext)

        when:
        DomainEventListener listener = new DomainEventListener(datastore)

        then:
        1 * mappingContext.addMappingContextListener(_)

        when: 'the pre-existing entity\'s hook is invoked'
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }
        listener.beforeInsert(entity, ea)

        then: 'it fires immediately, proving the cache was created eagerly at construction time'
        domain.invoked == ['beforeInsert']
    }

    void "persistentEntityAdded creates event caches for a newly discovered entity"() {
        given:
        RecordingDomain domain = new RecordingDomain()
        PersistentEntity entity = entityFor(RecordingDomain)
        Datastore datastore = plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] })
        DomainEventListener listener = new DomainEventListener(datastore)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        expect: 'the hook is not yet wired up before the entity is added'
        listener.beforeInsert(entity, ea)
        domain.invoked.isEmpty()

        when:
        listener.persistentEntityAdded(entity)
        listener.beforeInsert(entity, ea)

        then:
        domain.invoked == ['beforeInsert']
    }

    void "supportsEventType accepts AbstractPersistenceEvent subtypes and rejects unrelated ApplicationEvents"() {
        given:
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))

        expect:
        listener.supportsEventType(PreInsertEvent)
        !listener.supportsEventType(PayloadApplicationEvent)
    }

    void "beforeInsert sets an initial numeric version to 0 when the entity is versioned"() {
        given:
        PersistentEntity entity = entityFor(NoHooksDomain, true, Long)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Mock(EntityAccess) {
            getEntity() >> new NoHooksDomain()
            getPersistentEntity() >> entity
        }

        when:
        listener.beforeInsert(entity, ea)

        then:
        1 * ea.setProperty(GormProperties.VERSION, 0)
    }

    void "beforeInsert sets an initial java.sql.Timestamp version when the version type is a Timestamp"() {
        given:
        PersistentEntity entity = entityFor(NoHooksDomain, true, Timestamp)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Mock(EntityAccess) {
            getEntity() >> new NoHooksDomain()
            getPersistentEntity() >> entity
        }

        when:
        listener.beforeInsert(entity, ea)

        then:
        1 * ea.setProperty(GormProperties.VERSION, { it instanceof Timestamp })
    }

    void "beforeInsert sets an initial java.util.Date version when the version type is a plain Date"() {
        given:
        PersistentEntity entity = entityFor(NoHooksDomain, true, Date)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Mock(EntityAccess) {
            getEntity() >> new NoHooksDomain()
            getPersistentEntity() >> entity
        }

        when:
        listener.beforeInsert(entity, ea)

        then:
        1 * ea.setProperty(GormProperties.VERSION, { it.class == Date })
    }

    void "beforeInsert does not set a version when the version type is neither Number, Timestamp, nor Date"() {
        given:
        PersistentEntity entity = entityFor(NoHooksDomain, true, String)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Mock(EntityAccess) {
            getEntity() >> new NoHooksDomain()
            getPersistentEntity() >> entity
        }

        when:
        listener.beforeInsert(entity, ea)

        then:
        0 * ea.setProperty(*_)
    }

    void "beforeInsert does not set a version when the entity is not versioned"() {
        given:
        PersistentEntity entity = entityFor(NoHooksDomain, false)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Mock(EntityAccess) { getEntity() >> new NoHooksDomain() }

        when:
        listener.beforeInsert(entity, ea)

        then:
        0 * ea.setProperty(*_)
    }

    void "beforeInsert returns true without error when the entity was never registered with the listener"() {
        given:
        PersistentEntity entity = entityFor(NoHooksDomain)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> new NoHooksDomain() }

        expect:
        listener.beforeInsert(entity, ea)
    }

    void "beforeInsert returns true without error when the domain class defines no beforeInsert hook"() {
        given:
        PersistentEntity entity = entityFor(NoHooksDomain)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        listener.persistentEntityAdded(entity)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> new NoHooksDomain() }

        expect:
        listener.beforeInsert(entity, ea)
    }

    @Unroll
    void "the 2-arg #methodName(entity, ea) convenience overload dispatches to the corresponding hook"() {
        given:
        RecordingDomain domain = new RecordingDomain()
        PersistentEntity entity = entityFor(RecordingDomain)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        listener.persistentEntityAdded(entity)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener."$methodName"(entity, ea)

        then:
        domain.invoked == [hookName]

        where:
        methodName     | hookName
        'beforeInsert'  | 'beforeInsert'
        'beforeUpdate'  | 'beforeUpdate'
        'beforeDelete'  | 'beforeDelete'
        'beforeLoad'    | 'beforeLoad'
        'afterInsert'   | 'afterInsert'
        'afterUpdate'   | 'afterUpdate'
        'afterDelete'   | 'afterDelete'
        'afterLoad'     | 'afterLoad'
    }

    void "afterInsert activates dirty checking on entities that implement DirtyCheckable"() {
        given:
        DirtyCheckableDomain domain = Spy(DirtyCheckableDomain)
        PersistentEntity entity = entityFor(DirtyCheckableDomain)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.afterInsert(entity, ea)

        then:
        1 * domain.trackChanges()
    }

    void "afterUpdate re-activates dirty checking on entities that implement DirtyCheckable"() {
        given:
        DirtyCheckableDomain domain = Spy(DirtyCheckableDomain)
        PersistentEntity entity = entityFor(DirtyCheckableDomain)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.afterUpdate(entity, ea)

        then:
        1 * domain.trackChanges()
    }

    void "afterLoad activates dirty checking on entities that implement DirtyCheckable"() {
        given:
        DirtyCheckableDomain domain = Spy(DirtyCheckableDomain)
        PersistentEntity entity = entityFor(DirtyCheckableDomain)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.afterLoad(entity, ea)

        then:
        1 * domain.trackChanges()
    }

    void "afterDelete does not activate dirty checking since the entity is no longer trackable"() {
        given:
        DirtyCheckableDomain domain = Spy(DirtyCheckableDomain)
        PersistentEntity entity = entityFor(DirtyCheckableDomain)
        DomainEventListener listener = new DomainEventListener(plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }))
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.afterDelete(entity, ea)

        then:
        0 * domain.trackChanges()
    }

    void "afterLoad autowires the entity when the datastore's default connection source is configured to autowire"() {
        given:
        NoHooksDomain domain = new NoHooksDomain()
        PersistentEntity entity = entityFor(NoHooksDomain, false, null, false)
        AutowireCapableBeanFactory beanFactory = Mock(AutowireCapableBeanFactory)
        ConfigurableApplicationContext appContext = Stub(ConfigurableApplicationContext) {
            getAutowireCapableBeanFactory() >> beanFactory
        }
        Datastore datastore = connectionAwareDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }, true, appContext)
        DomainEventListener listener = new DomainEventListener(datastore)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.afterLoad(entity, ea)

        then:
        1 * beanFactory.autowireBeanProperties(domain, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
    }

    void "afterLoad autowires the entity when the entity's own mapping requests autowire even though the datastore default does not"() {
        given:
        NoHooksDomain domain = new NoHooksDomain()
        PersistentEntity entity = entityFor(NoHooksDomain, false, null, true)
        AutowireCapableBeanFactory beanFactory = Mock(AutowireCapableBeanFactory)
        ConfigurableApplicationContext appContext = Stub(ConfigurableApplicationContext) {
            getAutowireCapableBeanFactory() >> beanFactory
        }
        Datastore datastore = plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }, appContext)
        DomainEventListener listener = new DomainEventListener(datastore)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.afterLoad(entity, ea)

        then:
        1 * beanFactory.autowireBeanProperties(domain, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
    }

    void "afterLoad does not autowire the entity when neither the datastore default nor the entity's mapping request it"() {
        given:
        NoHooksDomain domain = new NoHooksDomain()
        PersistentEntity entity = entityFor(NoHooksDomain, false, null, false)
        AutowireCapableBeanFactory beanFactory = Mock(AutowireCapableBeanFactory)
        ConfigurableApplicationContext appContext = Stub(ConfigurableApplicationContext) {
            getAutowireCapableBeanFactory() >> beanFactory
        }
        Datastore datastore = plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }, appContext)
        DomainEventListener listener = new DomainEventListener(datastore)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.afterLoad(entity, ea)

        then:
        0 * beanFactory.autowireBeanProperties(*_)
    }

    void "afterLoad requests autowiring without error when the datastore has no ApplicationContext to autowire through"() {
        given:
        NoHooksDomain domain = new NoHooksDomain()
        PersistentEntity entity = entityFor(NoHooksDomain, false, null, false)
        Datastore datastore = connectionAwareDatastore(Stub(MappingContext) { getPersistentEntities() >> [] }, true, null)
        DomainEventListener listener = new DomainEventListener(datastore)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.afterLoad(entity, ea)

        then:
        notThrown(NullPointerException)
    }

    @Unroll
    void "onApplicationEvent dispatches a #eventType.simpleName to the #hookName hook"() {
        given:
        RecordingDomain domain = new RecordingDomain()
        PersistentEntity entity = entityFor(RecordingDomain)
        Datastore datastore = plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] })
        DomainEventListener listener = new DomainEventListener(datastore)
        listener.persistentEntityAdded(entity)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }
        ApplicationEvent event = eventType.newInstance(datastore, entity, ea)

        when:
        listener.onApplicationEvent(event)

        then:
        domain.invoked == [hookName]

        where:
        eventType        | hookName
        PreInsertEvent    | 'beforeInsert'
        PostInsertEvent   | 'afterInsert'
        PreUpdateEvent    | 'beforeUpdate'
        PostUpdateEvent   | 'afterUpdate'
        PreDeleteEvent    | 'beforeDelete'
        PostDeleteEvent   | 'afterDelete'
        PreLoadEvent      | 'beforeLoad'
        PostLoadEvent     | 'afterLoad'
    }

    @Unroll
    void "onApplicationEvent silently ignores a #eventType.simpleName since domain events define no hook for it"() {
        given:
        RecordingDomain domain = new RecordingDomain()
        PersistentEntity entity = entityFor(RecordingDomain)
        Datastore datastore = plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] })
        DomainEventListener listener = new DomainEventListener(datastore)
        listener.persistentEntityAdded(entity)
        EntityAccess ea = Stub(EntityAccess) { getEntity() >> domain }

        when:
        listener.onApplicationEvent(eventType.newInstance(datastore, entity, ea))

        then:
        noExceptionThrown()
        domain.invoked.isEmpty()

        where:
        eventType << [SaveOrUpdateEvent, ValidationEvent, MergeEvent, PersistEvent]
    }

    @Unroll
    void "onApplicationEvent cancels a #eventType.simpleName when its before-hook returns false"() {
        given:
        CancellingDomain domain = new CancellingDomain()
        PersistentEntity entity = entityFor(CancellingDomain)
        Datastore datastore = plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] })
        DomainEventListener listener = new DomainEventListener(datastore)
        listener.persistentEntityAdded(entity)
        EntityAccess ea = Mock(EntityAccess) { getEntity() >> domain }
        ApplicationEvent event = eventType.newInstance(datastore, entity, ea)

        when:
        listener.onApplicationEvent(event)

        then:
        event.cancelled
        0 * ea.refresh()

        where:
        eventType << [PreInsertEvent, PreUpdateEvent, PreDeleteEvent]
    }

    void "onApplicationEvent refreshes the entity access after a successful beforeInsert hook since beforeInsert is a refresh event"() {
        given:
        RecordingDomain domain = new RecordingDomain()
        PersistentEntity entity = entityFor(RecordingDomain)
        Datastore datastore = plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] })
        DomainEventListener listener = new DomainEventListener(datastore)
        listener.persistentEntityAdded(entity)
        EntityAccess ea = Mock(EntityAccess) { getEntity() >> domain }

        when:
        listener.onApplicationEvent(new PreInsertEvent(datastore, entity, ea))

        then:
        1 * ea.refresh()
    }

    void "onApplicationEvent does not refresh the entity access after a successful beforeLoad hook since beforeLoad is not a refresh event"() {
        given:
        RecordingDomain domain = new RecordingDomain()
        PersistentEntity entity = entityFor(RecordingDomain)
        Datastore datastore = plainDatastore(Stub(MappingContext) { getPersistentEntities() >> [] })
        DomainEventListener listener = new DomainEventListener(datastore)
        listener.persistentEntityAdded(entity)
        EntityAccess ea = Mock(EntityAccess) { getEntity() >> domain }

        when:
        listener.onApplicationEvent(new PreLoadEvent(datastore, entity, ea))

        then:
        0 * ea.refresh()
    }

    private PersistentEntity entityFor(Class<?> javaClass, boolean versioned = false, Class<?> versionType = null,
                                        boolean mappedAutowire = false) {
        PersistentProperty version = versioned ? Stub(PersistentProperty) { getType() >> versionType } : null
        ClassMapping mapping = Stub(ClassMapping) {
            getMappedForm() >> new Entity(autowire: mappedAutowire)
        }
        Stub(PersistentEntity) {
            getJavaClass() >> javaClass
            isVersioned() >> versioned
            getVersion() >> version
            getMapping() >> mapping
        }
    }

    private Datastore plainDatastore(MappingContext mappingContext, ConfigurableApplicationContext appContext = null) {
        Stub(Datastore) {
            getMappingContext() >> mappingContext
            getApplicationContext() >> appContext
        }
    }

    private Datastore connectionAwareDatastore(MappingContext mappingContext, boolean autowire,
                                                ConfigurableApplicationContext appContext = null) {
        ConnectionSource connectionSource = Stub(ConnectionSource) {
            getSettings() >> new ConnectionSourceSettings(autowire: autowire)
        }
        ConnectionSources connectionSources = Stub(ConnectionSources) {
            getDefaultConnectionSource() >> connectionSource
        }
        Stub(Datastore, additionalInterfaces: [ConnectionSourcesProvider]) {
            getMappingContext() >> mappingContext
            getApplicationContext() >> appContext
            getConnectionSources() >> connectionSources
        }
    }
}

class RecordingDomain {

    List<String> invoked = []

    void beforeInsert() { invoked << 'beforeInsert' }

    void beforeUpdate() { invoked << 'beforeUpdate' }

    void beforeDelete() { invoked << 'beforeDelete' }

    void beforeLoad() { invoked << 'beforeLoad' }

    void afterInsert() { invoked << 'afterInsert' }

    void afterUpdate() { invoked << 'afterUpdate' }

    void afterDelete() { invoked << 'afterDelete' }

    void afterLoad() { invoked << 'afterLoad' }
}

class CancellingDomain {

    boolean beforeInsert() { false }

    boolean beforeUpdate() { false }

    boolean beforeDelete() { false }
}

class NoHooksDomain {

}

class DirtyCheckableDomain implements DirtyCheckable {

}
