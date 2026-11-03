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
package org.grails.datastore.mapping.engine.event

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

class PersistenceEventSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity entity = mappingContext.addPersistentEntity(EvtBook)

    Datastore datastore = Stub(Datastore) {
        getMappingContext() >> mappingContext
    }

    private EntityAccess accessFor(EvtBook book) {
        mappingContext.createEntityAccess(entity, book)
    }

    @Unroll
    void "#type.simpleName reports #eventType and carries the entity access"() {
        given:
        EvtBook book = new EvtBook(title: 't')
        EntityAccess access = accessFor(book)

        when:
        AbstractPersistenceEvent event = type.newInstance(datastore, entity, access)

        then:
        event.eventType == eventType
        event.source.is(datastore)
        event.entity.is(entity)
        event.entityAccess.is(access)
        event.entityObject.is(book)
        !event.cancelled
        event.nativeEvent == null

        where:
        type              | eventType
        PreInsertEvent    | EventType.PreInsert
        PostInsertEvent   | EventType.PostInsert
        PreUpdateEvent    | EventType.PreUpdate
        PostUpdateEvent   | EventType.PostUpdate
        PreDeleteEvent    | EventType.PreDelete
        PostDeleteEvent   | EventType.PostDelete
        PreLoadEvent      | EventType.PreLoad
        PostLoadEvent     | EventType.PostLoad
        SaveOrUpdateEvent | EventType.SaveOrUpdate
        MergeEvent        | EventType.Merge
        PersistEvent      | EventType.Persist
        ValidationEvent   | EventType.Validation
    }

    @Unroll
    void "#type.simpleName resolves the entity and access from a raw object"() {
        given:
        EvtBook book = new EvtBook(title: 'raw')

        when:
        AbstractPersistenceEvent event = type.newInstance(datastore, book)

        then:
        event.entityObject.is(book)
        event.entity.is(entity)
        event.entityAccess.entity.is(book)
        event.entityAccess.getProperty('title') == 'raw'

        when: 'the object is not a mapped entity'
        AbstractPersistenceEvent unknown = type.newInstance(datastore, 'not an entity')

        then:
        unknown.entityObject == 'not an entity'
        unknown.entity == null
        unknown.entityAccess == null

        where:
        type << [PreInsertEvent, PostInsertEvent, PreUpdateEvent, PostUpdateEvent, PreDeleteEvent, PostDeleteEvent,
                 PreLoadEvent, PostLoadEvent, SaveOrUpdateEvent, MergeEvent, PersistEvent, ValidationEvent]
    }

    @Unroll
    void "#type.simpleName accepts a plain object source"() {
        given:
        EvtBook book = new EvtBook()
        EntityAccess access = accessFor(book)

        when:
        AbstractPersistenceEvent event = type.newInstance('source', entity, access)

        then:
        event.source == 'source'
        event.entity.is(entity)
        event.entityObject.is(book)

        where:
        type << [PreInsertEvent, PostInsertEvent, PreUpdateEvent, PostUpdateEvent, PreDeleteEvent, PostDeleteEvent,
                 PreLoadEvent, PostLoadEvent, ValidationEvent]
    }

    @Unroll
    void "#type.simpleName can be created without an entity access"() {
        when:
        AbstractPersistenceEvent event = type.newInstance('source', entity)

        then:
        event.source == 'source'
        event.entity.is(entity)
        event.entityAccess == null
        event.entityObject == null

        where:
        type << [PreInsertEvent, PostInsertEvent, PreLoadEvent, PostLoadEvent]
    }

    void "events can be cancelled, exclude listeners and carry a native event"() {
        given:
        AbstractPersistenceEvent event = new PreInsertEvent(datastore, entity, accessFor(new EvtBook()))

        expect:
        !event.cancelled
        !event.isListenerExcluded('a.Listener')

        when:
        event.cancel()
        event.addExcludedListenerName('a.Listener')
        event.nativeEvent = 'native'

        then:
        event.cancelled
        event.isListenerExcluded('a.Listener')
        !event.isListenerExcluded('b.Listener')
        event.nativeEvent == 'native'
    }

    void "validation events track the validated fields"() {
        given:
        ValidationEvent event = new ValidationEvent(datastore, entity, accessFor(new EvtBook()))

        expect:
        event.validatedFields == null

        when:
        event.validatedFields = ['title']

        then:
        event.validatedFields == ['title']
    }

    void "the event constants name the GORM callbacks"() {
        expect:
        AbstractPersistenceEvent.ONLOAD_EVENT == 'onLoad'
        AbstractPersistenceEvent.ONLOAD_SAVE == 'onSave'
        AbstractPersistenceEvent.BEFORE_LOAD_EVENT == 'beforeLoad'
        AbstractPersistenceEvent.BEFORE_INSERT_EVENT == 'beforeInsert'
        AbstractPersistenceEvent.AFTER_INSERT_EVENT == 'afterInsert'
        AbstractPersistenceEvent.BEFORE_UPDATE_EVENT == 'beforeUpdate'
        AbstractPersistenceEvent.AFTER_UPDATE_EVENT == 'afterUpdate'
        AbstractPersistenceEvent.BEFORE_DELETE_EVENT == 'beforeDelete'
        AbstractPersistenceEvent.AFTER_DELETE_EVENT == 'afterDelete'
        AbstractPersistenceEvent.AFTER_LOAD_EVENT == 'afterLoad'
    }

    void "event types and the default listener order are stable"() {
        expect:
        EventType.values()*.name() == ['PreDelete', 'PreInsert', 'PreLoad', 'PreUpdate', 'PostDelete', 'PostInsert',
                                       'PostLoad', 'PostUpdate', 'SaveOrUpdate', 'Validation', 'Merge', 'Persist']
        PersistenceEventListener.DEFAULT_ORDER == Integer.MAX_VALUE.intdiv(2)
    }
}

class EvtBook {
    Long id
    String title
}
