/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
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
import org.hibernate.event.service.spi.EventListenerGroup
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.event.spi.LoadEventListener
import org.hibernate.service.spi.SessionFactoryServiceRegistry
import spock.lang.Specification

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

    def 'integrate throws IllegalStateException if EventListenerRegistry is not available'() {
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
        e.message == 'EventListenerRegistry not available from ServiceRegistry'
    }

    def 'integrate with null hibernateEventListeners and null eventListeners map is a no-op'() {
        given:
        EventListenerIntegrator integrator = new EventListenerIntegrator(null, null)

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        noExceptionThrown()
        0 * listenerRegistry.appendListeners(*_)
        0 * listenerRegistry.setListeners(*_)
    }

    def 'integrate appends a custom listener from eventListeners map using a Collection'() {
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

    def 'integrate appends a singleton listener from eventListeners map when value is not a collection'() {
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

    def 'integrate skips null values in eventListeners map'() {
        given:
        EventListenerIntegrator integrator = new EventListenerIntegrator(null, ['load': null])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        noExceptionThrown()
        0 * listenerRegistry.appendListeners(*_)
    }

    def 'integrate uses setListeners (override) for DefaultMergeEventListener on MERGE event'() {
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

    def 'integrate appends (not overrides) non-merge non-persist listeners from hibernateEventListeners'() {
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

    def 'disintegrate is a no-op'() {
        given:
        EventListenerIntegrator integrator = new EventListenerIntegrator(null, [:])

        when:
        integrator.disintegrate(sfi, serviceRegistry)

        then:
        noExceptionThrown()
    }

    def 'appendListeners(registry, eventType, Collection) skips null listeners in collection'() {
        given:
        EventListenerGroup<LoadEventListener> group = Mock(EventListenerGroup)
        listenerRegistry.getEventListenerGroup(EventType.LOAD) >> group

        EventListenerIntegrator integrator = new EventListenerIntegrator(null, ['load': [null]])

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        0 * group.appendListener(_)
    }

    def 'appendListeners with clearListeners is triggered for MergeEventListener in collection'() {
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
