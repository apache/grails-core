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

import grails.gorm.DetachedCriteria
import org.apache.grails.data.testing.tck.domains.Simples
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.query.event.AbstractQueryEvent
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.SmartApplicationListener
import spock.lang.IgnoreIf
import spock.lang.PendingFeature
import spock.lang.PendingFeatureIf

/**
 * Tests for query events.
 */
// TODO: the application context is null on hibernate tck tests, so this test errors on the add of the application listener
@IgnoreIf({ System.getProperty('hibernate5.gorm.suite') || System.getProperty('hibernate6.gorm.suite') || System.getProperty('mongodb.gorm.suite')})
class QueryEventsSpec extends GrailsDataTckSpec {
    SpecQueryEventListener listener

    void setupSpec() {
        manager.domainClasses.addAll([Simples])
    }

    def setup() {
        listener = new SpecQueryEventListener()
        manager.session.datastore.applicationContext.addApplicationListener(listener)
    }

    void "pre-events are fired before queries are run"() {
        when:
        TestEntity.findByName 'bob'
        then:
        listener.events.size() >= 1
        listener.events[0] instanceof PreQueryEvent
        listener.events[0].query != null
        listener.PreExecution == 1

        when:
        TestEntity.where {name == 'bob'}.list()
        then:
        listener.PreExecution == 2

        when:
        new DetachedCriteria(TestEntity).build({name == 'bob'}).list()
        then:
        listener.PreExecution == 3
    }

    void "post-events are fired after queries are run"() {
        given:
        def entity = new TestEntity(name: 'bob').save(flush: true)
        new TestEntity(name: 'mark').save(flush: true)

        when:
        TestEntity.findByName 'bob'
        then:
        listener.events.size() >= 1
        listener.events[1] instanceof PostQueryEvent
        listener.events[1].query != null
        listener.events[1].query == listener.events[0].query
        listener.events[1].results instanceof List
        listener.events[1].results.size() == 1
        listener.events[1].results[0] == entity
        listener.PostExecution == 1

        when:
        TestEntity.where {name == 'bob'}.list()
        then:
        listener.PostExecution == 2

        when:
        new DetachedCriteria(TestEntity).build({name == 'bob'}).list()
        then:
        listener.PostExecution == 3
    }

    static class SpecQueryEventListener implements SmartApplicationListener {

        List<AbstractQueryEvent> events = []

        int PreExecution,
            PostExecution

        @Override
        void onApplicationEvent(ApplicationEvent event) {
            AbstractQueryEvent e = event as AbstractQueryEvent
            def typeName = e.eventType.name()
            this."$typeName"++
            events << event
        }

        @Override
        boolean supportsSourceType(Class<?> sourceType) {
            return true
        }

        @Override
        int getOrder() {
            Integer.MAX_VALUE / 2
        }

        @Override
        boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
            return eventType in [PreQueryEvent, PostQueryEvent]
        }
    }
}

