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
package org.apache.grails.data.testing.tck.tests

import org.springframework.context.ApplicationEvent
import org.springframework.context.ConfigurableApplicationContext

import grails.gorm.DetachedCriteria
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.Simples
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.EventType
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.ValidationEvent

/**
 * @author Tom Widmer
 */
class PersistenceEventListenerSpec extends GrailsDataTckSpec {
    SpecPersistenceListener listener

    void setupSpec() {
        manager.domainClasses.addAll([Simples])
    }

    def setup() {
        listener = new SpecPersistenceListener(manager.session.datastore)

        def publisher = manager.session.datastore.applicationEventPublisher
        if (publisher instanceof ConfigurableApplicationEventPublisher) {
            ((ConfigurableApplicationEventPublisher) publisher).addApplicationListener(listener)
        } else if (publisher instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext) publisher).addApplicationListener(listener)
        }
    }

    void "Test delete events"() {
        given:
        def p = new Simples()
        p.name = "Fred"
        p.save(flush: true)
        manager.session.clear()

        when:
        p = Simples.get(p.id)

        then:
        0 == listener.PreDeleteCount
        0 == listener.PostDeleteCount

        when:
        p.delete(flush: true)

        then:
        1 == listener.PreDeleteCount
        1 == listener.PostDeleteCount
        0 < listener.events.size()
        p == listener.events[-1].entityObject
        listener.events[-1].eventType == EventType.PostDelete
        listener.events[-1] instanceof PostDeleteEvent
        listener.events[-2].eventType == EventType.PreDelete
        listener.events[-2] instanceof PreDeleteEvent
    }

    void "Test multi-delete events"() {
        given:
        def freds = (1..3).collect {
            new Simples(name: "Fred$it").save(flush: true)
        }
        manager.session.clear()

        when:
        freds = Simples.findAllByIdInList(freds*.id)

        then:
        3 == freds.size()
        0 == listener.PreDeleteCount
        0 == listener.PostDeleteCount

        when:
        new DetachedCriteria(Simples).build {
            'in'('id', freds*.id)
        }.deleteAll()
        manager.session.flush()

        then:
        0 == Simples.count()
        0 == Simples.list().size()

        // conditional assertions because in the case of batch DML statements neither Hibernate nor JPA triggers delete events for individual entities
        if (!manager.session.getClass().simpleName in ['JpaSession', 'HibernateSession']) {
            3 == listener.PreDeleteCount
            3 == listener.PostDeleteCount
        }
    }

    void "Test update events"() {
        given:
        def p = new Simples()

        p.name = "Fred"
        p.save(flush: true)
        manager.session.clear()

        when:
        p = Simples.get(p.id)

        then:
        "Fred" == p.name
        0 == listener.PreUpdateCount
        0 == listener.PostUpdateCount

        when:
        p.name = "Bob"
        p.save(flush: true)
        manager.session.clear()
        p = Simples.get(p.id)

        then:
        "Bob" == p.name
        1 == listener.PreUpdateCount
        1 == listener.PostUpdateCount
    }

    void "Test insert events"() {
        given:
        def p = new Simples()

        p.name = "Fred"
        p.save(flush: true)
        manager.session.clear()

        when:
        p = Simples.get(p.id)

        then:
        "Fred" == p.name
        0 == listener.PreUpdateCount
        1 == listener.PreInsertCount
        0 == listener.PostUpdateCount
        1 == listener.PostInsertCount

        when:
        p.name = "Bob"
        p.save(flush: true)
        manager.session.clear()
        p = Simples.get(p.id)

        then:
        "Bob" == p.name
        1 == listener.PreUpdateCount
        1 == listener.PreInsertCount
        1 == listener.PostUpdateCount
        1 == listener.PostInsertCount
    }

    void "Test load events"() {
        given:
        def p = new Simples()

        p.name = "Fred"
        p.save(flush: true)
        manager.session.clear()

        when:
        p = Simples.get(p.id)

        then:
        "Fred" == p.name
        if (!'JpaSession'.equals(manager.session.getClass().simpleName)) {
            // JPA doesn't seem to support a pre-load event
            1 == listener.PreLoadCount
        }
        1 == listener.PostLoadCount
    }

    void "Test multi-load events"() {
        given:
        def freds = (1..3).collect {
            new Simples(name: "Fred$it").save(flush: true)
        }
        manager.session.clear()

        when:
        freds = Simples.findAllByIdInList(freds*.id)
        for (f in freds) {
        } // just to trigger load

        then:
        3 == freds.size()
        if (!'JpaSession'.equals(manager.session.getClass().simpleName)) {
            // JPA doesn't seem to support a pre-load event
            3 == listener.PreLoadCount
        }
        3 == listener.PostLoadCount
    }

    void "Test validation events"() {
        given:
        def p = new Simples()

        p.name = "Fred"

        when:
        p.validate()

        then:
        1 == listener.ValidationCount
        listener.events.size() == 1
        p == listener.events[0].entityObject
        listener.events[0] instanceof ValidationEvent
        null == listener.events[0].validatedFields

        when:
        p.name = null
        p.validate(['name'])

        then:
        2 == listener.ValidationCount
        listener.events.size() == 2
        p == listener.events[1].entityObject
        listener.events[1] instanceof ValidationEvent
        ['name'] == listener.events[1].validatedFields
    }
}

class SpecPersistenceListener extends AbstractPersistenceEventListener {

    SpecPersistenceListener(Datastore datastore) {
        super(datastore)
    }

    List<AbstractPersistenceEvent> events = []

    int PreDeleteCount,
        PreInsertCount,
        PreUpdateCount,
        PostUpdateCount,
        PostDeleteCount,
        PostInsertCount,
        PreLoadCount,
        PostLoadCount,
        SaveOrUpdateCount,
        ValidationCount

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        String typeName = event.eventType.name()
        this."${typeName}Count"++
        events << event
    }

    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        true
    }
}
