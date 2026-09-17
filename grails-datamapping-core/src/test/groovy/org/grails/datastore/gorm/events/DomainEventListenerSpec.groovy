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

import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PostLoadEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreLoadEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.springframework.context.ApplicationEvent

class DomainEventListenerSpec extends Specification {

    Datastore datastore = Stub(Datastore) {
        getMappingContext() >> Stub(MappingContext) {
            getPersistentEntities() >> []
        }
    }

    DomainEventListener listener = new DomainEventListener(datastore)

    void "supportsEventType returns true for persistence events and false otherwise"() {
        expect:
        listener.supportsEventType(PreInsertEvent)
        listener.supportsEventType(PostLoadEvent)
        !listener.supportsEventType(ApplicationEvent)
    }

    void "beforeInsert with no cached hook does nothing and returns true"() {
        given:
        def entity = entityFor(PlainThing)
        def ea = accessFor(new PlainThing(), entity)

        expect:
        listener.beforeInsert(entity, ea)
        !ea.isRefreshed()
    }

    void "beforeInsert invokes the domain hook and refreshes the entity access when it returns true"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)

        when:
        def result = listener.beforeInsert(entity, ea)

        then:
        result
        thing.beforeInsertCalled
        ea.isRefreshed()
    }

    void "beforeInsert returns false and does not refresh when the domain hook returns false"() {
        given:
        def entity = entityFor(CancellingThing)
        listener.persistentEntityAdded(entity)
        def thing = new CancellingThing()
        def ea = accessFor(thing, entity)

        when:
        def result = listener.beforeInsert(entity, ea)

        then:
        !result
        !ea.isRefreshed()
    }

    void "beforeInsert sets the initial version to 0 for a numeric version type"() {
        given:
        def entity = entityFor(PlainThing, true, Long)
        def ea = accessFor(new PlainThing(), entity)

        when:
        listener.beforeInsert(entity, ea)

        then:
        ea.getProperties()['version'] == 0
    }

    @Unroll
    void "beforeInsert sets an initial version of type #versionType.simpleName on a versioned entity"() {
        given:
        def entity = entityFor(PlainThing, true, versionType)
        def ea = accessFor(new PlainThing(), entity)

        when:
        listener.beforeInsert(entity, ea)

        then:
        versionType.isInstance(ea.getProperties()['version'])

        where:
        versionType << [Timestamp, Date]
    }

    void "beforeInsert swallows an exception raised while setting the initial version"() {
        given:
        def entity = entityFor(PlainThing, true, Long)
        def ea = accessFor(new PlainThing(), entity)
        ea.setFailOnSetProperty(true)

        when:
        listener.beforeInsert(entity, ea)

        then:
        noExceptionThrown()
    }

    void "beforeUpdate invokes the domain hook and refreshes the entity access when it returns true"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)

        when:
        def result = listener.beforeUpdate(entity, ea)

        then:
        result
        thing.beforeUpdateCalled
        ea.isRefreshed()
    }

    void "beforeDelete invokes the domain hook and refreshes the entity access when it returns true"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)

        when:
        def result = listener.beforeDelete(entity, ea)

        then:
        result
        thing.beforeDeleteCalled
        ea.isRefreshed()
    }

    void "beforeLoad invokes the domain hook and does not refresh the entity access"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)

        when:
        listener.beforeLoad(entity, ea)

        then:
        thing.beforeLoadCalled
        !ea.isRefreshed()
    }

    void "afterInsert activates dirty checking and invokes the domain hook"() {
        given:
        def entity = entityFor(HookedTrackable)
        listener.persistentEntityAdded(entity)
        def thing = new HookedTrackable()
        def ea = accessFor(thing, entity)

        when:
        listener.afterInsert(entity, ea)

        then:
        thing.afterInsertCalled
        thing.trackChangesCalled
        !ea.isRefreshed()
    }

    void "afterUpdate activates dirty checking and invokes the domain hook"() {
        given:
        def entity = entityFor(HookedTrackable)
        listener.persistentEntityAdded(entity)
        def thing = new HookedTrackable()
        def ea = accessFor(thing, entity)

        when:
        listener.afterUpdate(entity, ea)

        then:
        thing.afterUpdateCalled
        thing.trackChangesCalled
    }

    void "afterDelete invokes the domain hook"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)

        when:
        listener.afterDelete(entity, ea)

        then:
        thing.afterDeleteCalled
    }

    void "afterLoad activates dirty checking and invokes the domain hook"() {
        given:
        def entity = entityFor(HookedTrackable)
        listener.persistentEntityAdded(entity)
        def thing = new HookedTrackable()
        def ea = accessFor(thing, entity)

        when:
        listener.afterLoad(entity, ea)

        then:
        thing.afterLoadCalled
        thing.trackChangesCalled
    }

    void "onPersistenceEvent cancels a PreInsertEvent when the domain hook returns false"() {
        given:
        def entity = entityFor(CancellingThing)
        listener.persistentEntityAdded(entity)
        def ea = accessFor(new CancellingThing(), entity)
        def event = new PreInsertEvent(datastore, entity, ea)

        when:
        listener.onPersistenceEvent(event)

        then:
        event.isCancelled()
    }

    void "onPersistenceEvent does not cancel a PreInsertEvent when the domain hook returns true"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def ea = accessFor(new HookedThing(), entity)
        def event = new PreInsertEvent(datastore, entity, ea)

        when:
        listener.onPersistenceEvent(event)

        then:
        !event.isCancelled()
    }

    void "onPersistenceEvent dispatches PostInsertEvent to afterInsert"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)
        def event = new PostInsertEvent(datastore, entity, ea)

        when:
        listener.onPersistenceEvent(event)

        then:
        thing.afterInsertCalled
    }

    void "onPersistenceEvent dispatches PreUpdateEvent and PostUpdateEvent to the update hooks"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)

        when:
        listener.onPersistenceEvent(new PreUpdateEvent(datastore, entity, ea))
        listener.onPersistenceEvent(new PostUpdateEvent(datastore, entity, ea))

        then:
        thing.beforeUpdateCalled
        thing.afterUpdateCalled
    }

    void "onPersistenceEvent dispatches PreDeleteEvent and PostDeleteEvent to the delete hooks"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)

        when:
        listener.onPersistenceEvent(new PreDeleteEvent(datastore, entity, ea))
        listener.onPersistenceEvent(new PostDeleteEvent(datastore, entity, ea))

        then:
        thing.beforeDeleteCalled
        thing.afterDeleteCalled
    }

    void "onPersistenceEvent dispatches PreLoadEvent and PostLoadEvent to the load hooks"() {
        given:
        def entity = entityFor(HookedThing)
        listener.persistentEntityAdded(entity)
        def thing = new HookedThing()
        def ea = accessFor(thing, entity)

        when:
        listener.onPersistenceEvent(new PreLoadEvent(datastore, entity, ea))
        listener.onPersistenceEvent(new PostLoadEvent(datastore, entity, ea))

        then:
        thing.beforeLoadCalled
        thing.afterLoadCalled
    }

    private static PersistentEntity entityFor(Class clazz, boolean versioned = false, Class versionType = null) {
        PersistentProperty versionProperty = versionType == null ? null : [getType: { -> versionType }] as PersistentProperty
        ClassMapping mapping = [getMappedForm: { -> new Entity() }] as ClassMapping
        [
                getJavaClass: { -> clazz },
                isVersioned : { -> versioned },
                getVersion  : { -> versionProperty },
                getMapping  : { -> mapping }
        ] as PersistentEntity
    }

    private static RecordingEntityAccess accessFor(Object entity, PersistentEntity persistentEntity = null) {
        new RecordingEntityAccess(entity, persistentEntity)
    }

    static class RecordingEntityAccess implements EntityAccess {

        final Object entity
        final PersistentEntity persistentEntity
        final Map<String, Object> properties = [:]
        boolean refreshed = false
        boolean failOnSetProperty = false

        RecordingEntityAccess(Object entity, PersistentEntity persistentEntity) {
            this.entity = entity
            this.persistentEntity = persistentEntity
        }

        @Override
        Object getEntity() { entity }

        @Override
        Object getProperty(String name) { properties[name] }

        @Override
        Object getPropertyValue(String name) { properties[name] }

        @Override
        Class getPropertyType(String name) { properties[name]?.class }

        @Override
        void setProperty(String name, Object value) {
            if (failOnSetProperty) {
                throw new IllegalStateException('boom')
            }
            properties[name] = value
        }

        @Override
        Object getIdentifier() { null }

        @Override
        void setIdentifier(Object id) { }

        @Override
        void setIdentifierNoConversion(Object id) { }

        @Override
        String getIdentifierName() { 'id' }

        @Override
        PersistentEntity getPersistentEntity() { persistentEntity }

        @Override
        void refresh() { refreshed = true }

        @Override
        void setPropertyNoConversion(String name, Object value) { properties[name] = value }
    }

    static class PlainThing {
    }

    static class HookedThing {
        boolean beforeInsertCalled
        boolean afterInsertCalled
        boolean beforeUpdateCalled
        boolean afterUpdateCalled
        boolean beforeDeleteCalled
        boolean afterDeleteCalled
        boolean beforeLoadCalled
        boolean afterLoadCalled

        boolean beforeInsert() { beforeInsertCalled = true; true }

        void afterInsert() { afterInsertCalled = true }

        boolean beforeUpdate() { beforeUpdateCalled = true; true }

        void afterUpdate() { afterUpdateCalled = true }

        boolean beforeDelete() { beforeDeleteCalled = true; true }

        void afterDelete() { afterDeleteCalled = true }

        void beforeLoad() { beforeLoadCalled = true }

        void afterLoad() { afterLoadCalled = true }
    }

    static class CancellingThing {
        boolean beforeInsert() { false }
    }

    static class HookedTrackable implements DirtyCheckable {
        boolean afterInsertCalled
        boolean afterUpdateCalled
        boolean afterLoadCalled
        boolean trackChangesCalled

        void afterInsert() { afterInsertCalled = true }

        void afterUpdate() { afterUpdateCalled = true }

        void afterLoad() { afterLoadCalled = true }

        @Override
        void trackChanges() { trackChangesCalled = true }
    }
}
