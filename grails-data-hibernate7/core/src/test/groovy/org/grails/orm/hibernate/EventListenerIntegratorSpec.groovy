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

import org.hibernate.boot.Metadata
import org.hibernate.boot.spi.BootstrapContext
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.internal.DefaultMergeEventListener
import org.hibernate.event.internal.DefaultPersistEventListener
import org.hibernate.event.internal.DefaultPersistOnFlushEventListener
import org.hibernate.event.service.spi.EventListenerGroup
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.event.spi.LoadEventListener
import org.hibernate.service.spi.SessionFactoryServiceRegistry
import spock.lang.Specification

import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor

class EventListenerIntegratorSpec extends Specification {

    Metadata metadata = Mock(Metadata)
    BootstrapContext bootstrapContext = Mock(BootstrapContext)
    SessionFactoryImplementor sfi = Mock(SessionFactoryImplementor)
    SessionFactoryServiceRegistry serviceRegistry = Mock(SessionFactoryServiceRegistry)
    EventListenerRegistry listenerRegistry = Mock(EventListenerRegistry)

    def setup() {
        sfi.getServiceRegistry() >> serviceRegistry
        serviceRegistry.getService(EventListenerRegistry) >> listenerRegistry
    }

    def "integrate throws IllegalStateException if EventListenerRegistry is not available"() {
        given:
        def localSfi = Mock(SessionFactoryImplementor)
        def localServiceRegistry = Mock(SessionFactoryServiceRegistry)
        localSfi.getServiceRegistry() >> localServiceRegistry
        localServiceRegistry.getService(EventListenerRegistry) >> null

        EventListenerIntegrator integrator = new EventListenerIntegrator(Mock(HibernateEventListeners), [:])

        when:
        integrator.integrate(Mock(Metadata), Mock(BootstrapContext), localSfi)

        then:
        def e = thrown(IllegalStateException)
        e.message == "EventListenerRegistry not available from ServiceRegistry"
    }

    def "integrate with null hibernateEventListeners and null eventListeners map is a no-op"() {
        given:
        EventListenerIntegrator integrator = new EventListenerIntegrator(null, null)

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        noExceptionThrown()
        0 * listenerRegistry.appendListeners(*_)
        0 * listenerRegistry.setListeners(*_)
    }

    def "integrate appends a custom listener from eventListeners map using a Collection"() {
        given:
        LoadEventListener customListener = Mock(LoadEventListener)
        EventListenerGroup<LoadEventListener> group = Mock(EventListenerGroup)
        listenerRegistry.getEventListenerGroup(EventType.LOAD) >> group

        EventListenerIntegrator integrator = new EventListenerIntegrator(null, ['load': [customListener]])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        1 * group.appendListener(customListener)
    }

    def "integrate appends a singleton listener from eventListeners map when value is not a collection"() {
        given:
        LoadEventListener customListener = Mock(LoadEventListener)
        EventListenerGroup<LoadEventListener> group = Mock(EventListenerGroup)
        listenerRegistry.getEventListenerGroup(EventType.LOAD) >> group

        EventListenerIntegrator integrator = new EventListenerIntegrator(null, ['load': customListener])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        1 * group.appendListener(customListener)
    }

    def "integrate skips null values in eventListeners map"() {
        given:
        EventListenerIntegrator integrator = new EventListenerIntegrator(null, ['load': null])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        noExceptionThrown()
        0 * listenerRegistry.appendListeners(*_)
    }

    def "integrate uses setListeners (override) for DefaultMergeEventListener on MERGE event"() {
        given:
        DefaultMergeEventListener mergeListener = new DefaultMergeEventListener()
        HibernateEventListeners hibernateEventListeners = Mock(HibernateEventListeners)
        hibernateEventListeners.getListenerMap() >> ['merge': mergeListener]

        EventListenerIntegrator integrator = new EventListenerIntegrator(hibernateEventListeners, [:])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        1 * listenerRegistry.setListeners(EventType.MERGE, mergeListener)
    }

    def "integrate uses setListeners (override) for the interceptor's listener on #eventType"() {
        given: "the interceptor composes rather than extends Hibernate's default merge/persist listeners"
        ClosureEventTriggeringInterceptor interceptor = new ClosureEventTriggeringInterceptor()
        Object listener = eventType == EventType.PERSIST_ONFLUSH ? interceptor.persistOnFlushEventListener : interceptor
        HibernateEventListeners hibernateEventListeners = Mock(HibernateEventListeners)
        hibernateEventListeners.getListenerMap() >> [(eventName): listener]

        EventListenerIntegrator integrator = new EventListenerIntegrator(hibernateEventListeners, [:])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then: "appending it would double-fire its delegated merge/persist logic for every entity"
        1 * listenerRegistry.setListeners(eventType, listener)
        0 * listenerRegistry.appendListeners(eventType, listener)

        where:
        eventType                  | eventName
        EventType.MERGE            | 'merge'
        EventType.PERSIST          | 'create'
        EventType.PERSIST_ONFLUSH  | 'create-onflush'
    }

    def "integrate overrides Hibernate's persist-on-flush listener only for the persist-on-flush event"() {
        given: "a listener that keeps the PERSIST_ON_FLUSH cascade action, as the interceptor's does"
        DefaultPersistOnFlushEventListener onFlushListener = new DefaultPersistOnFlushEventListener()
        HibernateEventListeners hibernateEventListeners = Mock(HibernateEventListeners)
        hibernateEventListeners.getListenerMap() >> ['create-onflush': onFlushListener, 'load': onFlushListener]

        EventListenerIntegrator integrator = new EventListenerIntegrator(hibernateEventListeners, [:])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        1 * listenerRegistry.setListeners(EventType.PERSIST_ONFLUSH, onFlushListener)
        0 * listenerRegistry.appendListeners(EventType.PERSIST_ONFLUSH, onFlushListener)
        1 * listenerRegistry.appendListeners(EventType.LOAD, onFlushListener)
    }

    def "integrate keeps the Grails interceptor as an observer when an application listener replaces Hibernate's default on #eventType"() {
        given: "an application listener of its own for an event the interceptor also handles"
        ClosureEventTriggeringInterceptor interceptor = new ClosureEventTriggeringInterceptor()
        def applicationListener = eventType == EventType.MERGE ?
                new DefaultMergeEventListener() : new DefaultPersistEventListener()
        def group = Mock(EventListenerGroup)
        group.listeners() >> [interceptor]
        listenerRegistry.getEventListenerGroup(eventType) >> group

        EventListenerIntegrator integrator = new EventListenerIntegrator(
                Mock(HibernateEventListeners), [(eventName): applicationListener])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then: "the application listener replaces Hibernate's default, and GORM's event publishing is kept behind it"
        1 * group.clearListeners()
        1 * group.appendListener(applicationListener)
        1 * group.appendListener(interceptor.observingEventListener)

        and: "not the interceptor itself, which would perform the same merge or persist a second time"
        0 * group.appendListener(interceptor)

        where:
        eventType         | eventName
        EventType.MERGE   | 'merge'
        EventType.PERSIST | 'create'
    }

    def "integrate keeps the persist-on-flush listener as an observer when an application listener replaces it"() {
        given:
        ClosureEventTriggeringInterceptor interceptor = new ClosureEventTriggeringInterceptor()
        def applicationListener = new DefaultPersistOnFlushEventListener()
        def group = Mock(EventListenerGroup)
        group.listeners() >> [interceptor.persistOnFlushEventListener]
        listenerRegistry.getEventListenerGroup(EventType.PERSIST_ONFLUSH) >> group

        EventListenerIntegrator integrator = new EventListenerIntegrator(
                Mock(HibernateEventListeners), ['create-onflush': applicationListener])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        1 * group.clearListeners()
        1 * group.appendListener(applicationListener)
        1 * group.appendListener(interceptor.observingEventListener)
        0 * group.appendListener(interceptor.persistOnFlushEventListener)
    }

    def "integrate does not keep a second copy of the listener it is installing"() {
        given: "a group that already holds the very listener being registered"
        ClosureEventTriggeringInterceptor interceptor = new ClosureEventTriggeringInterceptor()
        def group = Mock(EventListenerGroup)
        group.listeners() >> [interceptor]
        listenerRegistry.getEventListenerGroup(EventType.MERGE) >> group

        EventListenerIntegrator integrator = new EventListenerIntegrator(null, ['merge': interceptor])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then: "it is installed once, with no observer alongside it"
        1 * group.clearListeners()
        1 * group.appendListener(interceptor)
        0 * group.appendListener(interceptor.observingEventListener)
    }

    def "integrate appends (not overrides) non-merge non-persist listeners from hibernateEventListeners"() {
        given:
        LoadEventListener loadListener = Mock(LoadEventListener)
        HibernateEventListeners hibernateEventListeners = Mock(HibernateEventListeners)
        hibernateEventListeners.getListenerMap() >> ['load': loadListener]

        EventListenerIntegrator integrator = new EventListenerIntegrator(hibernateEventListeners, [:])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        1 * listenerRegistry.appendListeners(EventType.LOAD, loadListener)
    }

    def "disintegrate is a no-op"() {
        given:
        EventListenerIntegrator integrator = new EventListenerIntegrator(null, [:])

        when:
        integrator.disintegrate(sfi, serviceRegistry)

        then:
        noExceptionThrown()
    }

    def "appendListeners(registry, eventType, Collection) skips null listeners in collection"() {
        given:
        EventListenerGroup<LoadEventListener> group = Mock(EventListenerGroup)
        listenerRegistry.getEventListenerGroup(EventType.LOAD) >> group

        EventListenerIntegrator integrator = new EventListenerIntegrator(null, ['load': [null]])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        0 * group.appendListener(_)
    }

    def "appendListeners with clearListeners is triggered for MergeEventListener in collection"() {
        given:
        DefaultMergeEventListener mergeListener = new DefaultMergeEventListener()
        EventListenerGroup group = Mock(EventListenerGroup)
        listenerRegistry.getEventListenerGroup(EventType.MERGE) >> group

        EventListenerIntegrator integrator = new EventListenerIntegrator(null, ['merge': [mergeListener]])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        1 * group.clearListeners()
        1 * group.appendListener(mergeListener)
    }
}
