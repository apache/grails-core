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

package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.TestEntity
import org.springframework.context.event.SmartApplicationListener
import org.springframework.context.ApplicationEvent
import org.grails.datastore.mapping.core.SessionCreationEvent
import org.grails.datastore.mapping.core.Session
import spock.lang.Ignore

/**
 * Test case that session creation events are fired.
 */
@Ignore('''
Cannot invoke method addApplicationListener() on null object
java.lang.NullPointerException: Cannot invoke method addApplicationListener() on null object
    at grails.gorm.tck.SessionCreationEventSpec.setup(SessionCreationEventSpec.groovy)
''')
class SessionCreationEventSpec extends GormDatastoreSpec {

    Listener listener

    def setup() {
        listener = new Listener()
        session.datastore.applicationContext.addApplicationListener(listener)
    }

    void "test event for new session"() {
        when:"Using existing session"
        TestEntity.withSession { s ->
            s.flush()
        }
        then:
        listener.events.empty

        when:"Creating new session"
        def newSession = null
        def isDatastoreSession = false
        TestEntity.withNewSession { s ->
            newSession = s
            isDatastoreSession = s instanceof Session
        }
        then:
        !isDatastoreSession || listener.events.size() == 1
        !isDatastoreSession || listener.events[0].session == newSession
    }


    static class Listener implements SmartApplicationListener {
        List<SessionCreationEvent> events = []

        @Override
        int getOrder() {
            Integer.MAX_VALUE / 2
        }

        @Override
        void onApplicationEvent(ApplicationEvent event) {
            events << event
        }

        @Override
        boolean supportsSourceType(Class<?> sourceType) {
            return true
        }

        @Override
        boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
            return eventType == SessionCreationEvent
        }
    }
}
