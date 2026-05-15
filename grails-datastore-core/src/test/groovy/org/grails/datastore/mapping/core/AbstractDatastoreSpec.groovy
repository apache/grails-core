/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.mapping.core

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.PropertyResolver
import spock.lang.Specification

class AbstractDatastoreSpec extends Specification {

    void 'test that getApplicationEventPublisher returns the application context if set'() {
        given:
        def mappingContext = Mock(MappingContext)
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        def datastore = new TestDatastore(mappingContext, (PropertyResolver)null, ctx)

        expect:
        datastore.applicationEventPublisher == ctx
        datastore.applicationContext == ctx
    }

    void 'test that SessionCreationEvent is published when connect is called'() {
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

    void 'test that getApplicationEventPublisher returns the standalone publisher if set'() {
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
