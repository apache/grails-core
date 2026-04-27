package org.grails.datastore.mapping.core

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
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
}
