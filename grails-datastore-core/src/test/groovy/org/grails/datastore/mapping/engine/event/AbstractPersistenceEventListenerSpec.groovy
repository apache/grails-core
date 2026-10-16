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

import org.springframework.context.ApplicationEvent
import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

class AbstractPersistenceEventListenerSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity entity = mappingContext.addPersistentEntity(ListenedBook)

    Datastore datastore = Stub(Datastore) { getMappingContext() >> mappingContext }
    Datastore otherDatastore = Stub(Datastore) { getMappingContext() >> mappingContext }
    RecordingListener listener = new RecordingListener(datastore)

    private AbstractPersistenceEvent eventFor(Object source) {
        new PreInsertEvent(source, entity, mappingContext.createEntityAccess(entity, new ListenedBook()))
    }

    void "persistence events from the listener's datastore are dispatched"() {
        given:
        AbstractPersistenceEvent event = eventFor(datastore)

        when:
        listener.onApplicationEvent(event)

        then:
        listener.received == [event]
        listener.datastore.is(datastore)
    }

    void "events that are not persistence events are ignored"() {
        when:
        listener.onApplicationEvent(new ApplicationEvent(datastore) { })

        then:
        listener.received.empty
    }

    void "events from another source are ignored"() {
        when:
        listener.onApplicationEvent(eventFor(otherDatastore))
        listener.onApplicationEvent(eventFor('not a datastore'))

        then:
        listener.received.empty
    }

    void "cancelled events are ignored"() {
        given:
        AbstractPersistenceEvent event = eventFor(datastore)
        event.cancel()

        when:
        listener.onApplicationEvent(event)

        then:
        listener.received.empty
    }

    void "events that exclude this listener are ignored"() {
        given:
        AbstractPersistenceEvent event = eventFor(datastore)
        event.addExcludedListenerName(RecordingListener.name)

        when:
        listener.onApplicationEvent(event)

        then:
        listener.received.empty

        when:
        AbstractPersistenceEvent other = eventFor(datastore)
        other.addExcludedListenerName('some.other.Listener')
        listener.onApplicationEvent(other)

        then:
        listener.received == [other]
    }

    void "the listener orders itself last and only supports its own datastore type"() {
        expect:
        listener.order == PersistenceEventListener.DEFAULT_ORDER
        listener.supportsSourceType(datastore.getClass())
        !listener.supportsSourceType(String)
    }

    static class RecordingListener extends AbstractPersistenceEventListener {

        List<AbstractPersistenceEvent> received = []

        RecordingListener(Datastore datastore) {
            super(datastore)
        }

        @Override
        protected void onPersistenceEvent(AbstractPersistenceEvent event) {
            received << event
        }

        @Override
        boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
            AbstractPersistenceEvent.isAssignableFrom(eventType)
        }
    }
}

class ListenedBook {
    Long id
    String title
}
