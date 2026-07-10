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

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.PropertyResolver
import spock.lang.Specification

class AbstractDatastoreSpec extends Specification {

    void "test that getApplicationEventPublisher returns the application context if set"() {
        given:
        def mappingContext = Mock(MappingContext)
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        def datastore = new TestDatastore(mappingContext, (PropertyResolver)null, ctx)

        expect:
        datastore.applicationEventPublisher == ctx
        datastore.applicationContext == ctx
    }

    void "test that SessionCreationEvent is published when connect is called"() {
        given:
        def mappingContext = Mock(MappingContext)
        def events = []
        def publisher = [
            publishEvent: { event -> events << event }
        ] as ApplicationEventPublisher
        
        // Note: GenericApplicationContext implements ApplicationEventPublisher
        def ctx = new GenericApplicationContext()
        ctx.addApplicationListener({ event -> events << event })
        ctx.refresh()
        
        def datastore = new TestDatastore(mappingContext, (PropertyResolver)null, ctx)
        def mockSession = Mock(Session)
        mockSession.getDatastore() >> datastore
        datastore.sessionCreator = { mockSession }

        when:
        def session = datastore.connect()

        then:
        session == mockSession
        events.any { it instanceof SessionCreationEvent }
        ((SessionCreationEvent)events.find { it instanceof SessionCreationEvent }).session == session
    }

    void "test that getApplicationEventPublisher returns the standalone publisher if set"() {
        given:
        def mappingContext = Mock(MappingContext)
        def events = []
        def publisher = [
            publishEvent: { event -> events << event }
        ] as ApplicationEventPublisher

        def datastore = new TestDatastore(mappingContext, (PropertyResolver)null, null)
        datastore.setApplicationEventPublisher(publisher)

        expect:
        datastore.getApplicationEventPublisher() == publisher
        datastore.applicationContext == null
    }

    void "the default event publisher only dispatches an event to listeners that support its type"() {
        given:
        def mappingContext = Mock(MappingContext)
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def matchingListener = new RecordingSessionCreationListener()
        def nonMatchingListener = new RecordingOtherEventListener()
        datastore.addApplicationListener(matchingListener)
        datastore.addApplicationListener(nonMatchingListener)
        def mockSession = Mock(Session)
        mockSession.getDatastore() >> datastore
        datastore.sessionCreator = { mockSession }

        when:
        datastore.connect()

        then:
        noExceptionThrown()
        matchingListener.received.size() == 1
        nonMatchingListener.received.isEmpty()
    }

    void "addApplicationListener registers against a subclass's overridden getApplicationEventPublisher, not the superclass field"() {
        given: "a subclass that publishes through its own field, like MongoDatastore/HibernateDatastore/Neo4jDatastore do"
        def mappingContext = Mock(MappingContext)
        def datastore = new OwnPublisherTestDatastore(mappingContext, (PropertyResolver) null, null)
        def listener = new RecordingSessionCreationListener()
        def mockSession = Mock(Session)
        mockSession.getDatastore() >> datastore

        when: "a listener is registered via the base class API"
        datastore.addApplicationListener(listener)
        and: "an event is published through the subclass's own publisher"
        datastore.ownPublisher.publishEvent(new SessionCreationEvent(mockSession))

        then: "the listener actually receives it, rather than being registered on an unused superclass publisher"
        listener.received.size() == 1
    }

    void "setApplicationContext(null) does not discard an explicitly-installed custom publisher"() {
        given:
        def mappingContext = Mock(MappingContext)
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, ctx)
        def customPublisher = Mock(ApplicationEventPublisher)
        datastore.setApplicationEventPublisher(customPublisher)

        when:
        datastore.setApplicationContext(null)

        then:
        datastore.getApplicationEventPublisher().is(customPublisher)
    }

    void "destroy() closes every session held on the current thread and clears resolver-bound state"() {
        given:
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def session = Mock(Session)
        session.getDatastore() >> datastore
        DatastoreUtils.bindSession(session)

        when:
        datastore.destroy()

        then:
        1 * session.disconnect()
        !datastore.hasCurrentSession()
        datastore.sessionResolver.resolve() == null
    }

    void "destroy() logs and continues closing remaining sessions when a session's disconnect() throws"() {
        given:
        def mappingContext = Mock(MappingContext) {
            getPersistentEntities() >> []
        }
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def session = Mock(Session)
        session.getDatastore() >> datastore
        session.disconnect() >> { throw new IllegalStateException("boom") }
        DatastoreUtils.bindSession(session)

        when:
        datastore.destroy()

        then:
        noExceptionThrown()
        !datastore.hasCurrentSession()
    }

    void "the default event publisher wraps a non-ApplicationEvent payload in a PayloadApplicationEvent before dispatching"() {
        given:
        def mappingContext = Mock(MappingContext)
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def listener = new RecordingAnyEventListener()
        datastore.addApplicationListener(listener)

        when:
        datastore.getApplicationEventPublisher().publishEvent("a plain payload")

        then:
        listener.received.size() == 1
        listener.received[0] instanceof PayloadApplicationEvent
        ((PayloadApplicationEvent) listener.received[0]).payload == "a plain payload"
    }

    void "addApplicationListener falls back to reflection when the publisher exposes its own addApplicationListener method"() {
        given:
        def mappingContext = Mock(MappingContext)
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def customPublisher = new ReflectiveApplicationEventPublisher()
        datastore.setApplicationEventPublisher(customPublisher)
        def listener = new RecordingSessionCreationListener()

        when:
        datastore.addApplicationListener(listener)

        then:
        customPublisher.registered == [listener]
    }

    void "addApplicationListener fails loudly rather than silently dropping the listener when the publisher exposes no addApplicationListener method"() {
        given:
        def mappingContext = Mock(MappingContext)
        def datastore = new TestDatastore(mappingContext, (PropertyResolver) null, null)
        def bareCustomPublisher = Mock(ApplicationEventPublisher)
        datastore.setApplicationEventPublisher(bareCustomPublisher)
        def listener = new RecordingSessionCreationListener()

        when:
        datastore.addApplicationListener(listener)

        then: "the caller finds out immediately that the listener will never receive events, instead of it being silently dropped"
        thrown(IllegalStateException)
    }

    static class RecordingSessionCreationListener implements ApplicationListener<SessionCreationEvent> {
        List<SessionCreationEvent> received = []

        @Override
        void onApplicationEvent(SessionCreationEvent event) {
            received << event
        }
    }

    static class OtherEvent extends ApplicationEvent {
        OtherEvent(Object source) {
            super(source)
        }
    }

    static class RecordingOtherEventListener implements ApplicationListener<OtherEvent> {
        List<OtherEvent> received = []

        @Override
        void onApplicationEvent(OtherEvent event) {
            received << event
        }
    }

    static class RecordingAnyEventListener implements ApplicationListener<ApplicationEvent> {
        List<ApplicationEvent> received = []

        @Override
        void onApplicationEvent(ApplicationEvent event) {
            received << event
        }
    }

    /**
     * Mirrors a custom {@link ApplicationEventPublisher} that is neither a {@link ConfigurableApplicationContext}
     * nor the datastore's own multicaster, but still exposes an addApplicationListener(ApplicationListener) method
     * that {@link AbstractDatastore#addApplicationListener(ApplicationListener)} discovers via reflection.
     */
    static class ReflectiveApplicationEventPublisher implements ApplicationEventPublisher {
        List<ApplicationListener> registered = []

        @Override
        void publishEvent(ApplicationEvent event) {
        }

        @Override
        void publishEvent(Object event) {
        }

        void addApplicationListener(ApplicationListener listener) {
            registered << listener
        }
    }

    static class TestDatastore extends AbstractDatastore {
        Closure<Session> sessionCreator = { null }

        TestDatastore(MappingContext mappingContext, PropertyResolver connectionDetails, ConfigurableApplicationContext ctx) {
            super(mappingContext, (PropertyResolver)connectionDetails, ctx)
        }

        @Override
        protected Session createSession(PropertyResolver connectionDetails) {
            return sessionCreator.call(connectionDetails)
        }
    }

    /**
     * Mirrors how MongoDatastore/HibernateDatastore/Neo4jDatastore keep their own
     * {@code eventPublisher} field and override {@link #getApplicationEventPublisher()}
     * to return it, bypassing the superclass field.
     */
    static class OwnPublisherTestDatastore extends AbstractDatastore {
        GenericApplicationContext ownPublisher

        OwnPublisherTestDatastore(MappingContext mappingContext, PropertyResolver connectionDetails, ConfigurableApplicationContext ctx) {
            super(mappingContext, connectionDetails, ctx)
            ownPublisher = new GenericApplicationContext()
            ownPublisher.refresh()
        }

        @Override
        ApplicationEventPublisher getApplicationEventPublisher() {
            return ownPublisher
        }

        @Override
        protected Session createSession(PropertyResolver connectionDetails) {
            return null
        }
    }
}
