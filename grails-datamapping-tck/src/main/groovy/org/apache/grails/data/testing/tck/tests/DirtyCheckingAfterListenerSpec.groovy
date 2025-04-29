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

import org.apache.grails.data.testing.tck.domains.TestPlayer
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.PendingFeatureIf
import spock.util.concurrent.PollingConditions

class DirtyCheckingAfterListenerSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([TestPlayer])
    }

    TestSaveOrUpdateEventListener listener
    def datastore

    def setup() {
        datastore = manager.session.datastore
        listener = new TestSaveOrUpdateEventListener(datastore)
        ApplicationEventPublisher publisher = datastore.applicationEventPublisher
        if (publisher instanceof ConfigurableApplicationEventPublisher) {
            ((ConfigurableApplicationEventPublisher) publisher).addApplicationListener(listener)
        } else if (publisher instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext) publisher).addApplicationListener(listener)
        }
    }

    @PendingFeatureIf({ !Boolean.getBoolean('hibernate5.gorm.suite') && !Boolean.getBoolean('hibernate6.gorm.suite') && !Boolean.getBoolean('mongodb.gorm.suite') })
    void "test state change from listener update the object"() {

        when:
        TestPlayer john = new TestPlayer(name: "John").save(flush: true)

        then:
        new PollingConditions().eventually { listener.isExecuted && TestPlayer.count() }

        when:
        manager.session.flush()
        manager.session.clear()
        john = TestPlayer.get(john.id)

        then:
        john.attributes
        john.attributes.size() == 3

    }
}

class TestSaveOrUpdateEventListener extends AbstractPersistenceEventListener {

    boolean isExecuted = false

    TestSaveOrUpdateEventListener(Datastore datastore) {
        super(datastore)
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        TestPlayer player = (TestPlayer) event.entityObject
        player.attributes = ["test0", "test1", "test2"]
        isExecuted = true
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return eventType == PreUpdateEvent || eventType == PreInsertEvent
    }
}