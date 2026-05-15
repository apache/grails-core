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

package org.grails.orm.hibernate.event.listener

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider
import org.grails.datastore.mapping.engine.event.MergeEvent as GormMergeEvent
import org.grails.datastore.mapping.engine.event.PersistEvent as GormPersistEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent as GormPreInsertEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent as GormPostInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent as GormPreUpdateEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent as GormPostUpdateEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent as GormPreDeleteEvent
import org.grails.datastore.mapping.engine.event.PostDeleteEvent as GormPostDeleteEvent
import org.grails.datastore.mapping.engine.event.PreLoadEvent as GormPreLoadEvent
import org.grails.datastore.mapping.engine.event.PostLoadEvent as GormPostLoadEvent
import org.grails.datastore.mapping.engine.event.ValidationEvent as GormValidationEvent
import org.hibernate.event.spi.MergeEvent as HibernateMergeEvent
import org.hibernate.event.spi.PersistEvent as HibernatePersistEvent
import org.hibernate.event.spi.PreInsertEvent as HibernatePreInsertEvent
import org.hibernate.event.spi.PostInsertEvent as HibernatePostInsertEvent
import org.hibernate.event.spi.PreUpdateEvent as HibernatePreUpdateEvent
import org.hibernate.event.spi.PostUpdateEvent as HibernatePostUpdateEvent
import org.hibernate.event.spi.PreDeleteEvent as HibernatePreDeleteEvent
import org.hibernate.event.spi.PostDeleteEvent as HibernatePostDeleteEvent
import org.hibernate.event.spi.PreLoadEvent as HibernatePreLoadEvent
import org.hibernate.event.spi.PostLoadEvent as HibernatePostLoadEvent
import org.hibernate.event.spi.EventSource

class HibernateEventListenerSpec extends HibernateGormDatastoreSpec {

    class RecordingHibernateEventListener extends HibernateEventListener {

        boolean onMergeEventCalled = false
        boolean onPersistEventCalled = false
        boolean onPreInsertCalled = false
        boolean onPostInsertCalled = false
        boolean onPreUpdateCalled = false
        boolean onPostUpdateCalled = false
        boolean onPreDeleteCalled = false
        boolean onPostDeleteCalled = false
        boolean onPreLoadCalled = false
        boolean onPostLoadCalled = false
        HibernateMergeEvent lastMergeEvent
        HibernatePersistEvent lastPersistEvent

        RecordingHibernateEventListener(org.grails.orm.hibernate.HibernateDatastore datastore) {
            super(datastore)
        }

        @Override
        protected void onMergeEvent(HibernateMergeEvent event) {
            onMergeEventCalled = true
            lastMergeEvent = event
        }

        @Override
        protected void onPersistEvent(HibernatePersistEvent event) {
            onPersistEventCalled = true
            lastPersistEvent = event
        }

        @Override
        boolean onPreInsert(HibernatePreInsertEvent event) { onPreInsertCalled = true; return false }

        @Override
        void onPostInsert(HibernatePostInsertEvent event) { onPostInsertCalled = true }

        @Override
        boolean onPreUpdate(HibernatePreUpdateEvent event) { onPreUpdateCalled = true; return false }

        @Override
        void onPostUpdate(HibernatePostUpdateEvent event) { onPostUpdateCalled = true }

        @Override
        boolean onPreDelete(HibernatePreDeleteEvent event) { onPreDeleteCalled = true; return false }

        @Override
        void onPostDelete(HibernatePostDeleteEvent event) { onPostDeleteCalled = true }

        @Override
        void onPreLoad(HibernatePreLoadEvent event) { onPreLoadCalled = true }

        @Override
        void onPostLoad(HibernatePostLoadEvent event) { onPostLoadCalled = true }

        @Override
        protected boolean isValidSource(org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent event) {
            return true
        }
    }

    void 'test onPersistenceEvent handles Merge event without fall-through'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def hibernateSession = Mock(EventSource)

        and: 'a GORM Merge event wrapping a Hibernate Merge event'
        def hibernateMergeEvent = new HibernateMergeEvent('Foo', entity, hibernateSession)
        def gormMergeEvent = new GormMergeEvent(datastore, entity)
        gormMergeEvent.setNativeEvent(hibernateMergeEvent)

        when: 'Merge event is published'
        listener.onApplicationEvent(gormMergeEvent)

        then: 'Only onMergeEvent is called'
        listener.onMergeEventCalled
        listener.lastMergeEvent == hibernateMergeEvent
        !listener.onPersistEventCalled
    }

    void 'test onPersistenceEvent handles Persist event without fall-through'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def hibernateSession = Mock(EventSource)

        and: 'a GORM Persist event wrapping a Hibernate Persist event'
        def hibernatePersistEvent = new HibernatePersistEvent('Foo', entity, hibernateSession)
        def gormPersistEvent = new GormPersistEvent(datastore, entity)
        gormPersistEvent.setNativeEvent(hibernatePersistEvent)

        when: 'Persist event is published'
        listener.onApplicationEvent(gormPersistEvent)

        then: 'Only onPersistEvent is called'
        listener.onPersistEventCalled
        listener.lastPersistEvent == hibernatePersistEvent
        !listener.onMergeEventCalled
    }

    void 'test onPersistenceEvent handles PreInsert event'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePreInsertEvent)

        def gormEvent = new GormPreInsertEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        listener.onPreInsertCalled
    }

    void 'test onPersistenceEvent handles PostInsert event'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePostInsertEvent)

        def gormEvent = new GormPostInsertEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        listener.onPostInsertCalled
    }

    void 'test onPersistenceEvent handles PreUpdate event'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePreUpdateEvent)

        def gormEvent = new GormPreUpdateEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        listener.onPreUpdateCalled
    }

    void 'test onPersistenceEvent handles PostUpdate event'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePostUpdateEvent)

        def gormEvent = new GormPostUpdateEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        listener.onPostUpdateCalled
    }

    void 'test onPersistenceEvent handles PreDelete event'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePreDeleteEvent)

        def gormEvent = new GormPreDeleteEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        listener.onPreDeleteCalled
    }

    void 'test onPersistenceEvent handles PostDelete event'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePostDeleteEvent)

        def gormEvent = new GormPostDeleteEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        listener.onPostDeleteCalled
    }

    void 'test onPersistenceEvent handles PreLoad event'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePreLoadEvent)

        def gormEvent = new GormPreLoadEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        listener.onPreLoadCalled
    }

    void 'test onPersistenceEvent handles PostLoad event'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePostLoadEvent)

        def gormEvent = new GormPostLoadEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        listener.onPostLoadCalled
    }

    void 'test onPersistenceEvent calls event.cancel when PreInsert handler returns true'() {
        given:
        def datastore = getDatastore()
        def listener = new CancellingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePreInsertEvent)

        def gormEvent = new GormPreInsertEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        gormEvent.isCancelled()
    }

    void 'test onPersistenceEvent calls event.cancel when PreUpdate handler returns true'() {
        given:
        def datastore = getDatastore()
        def listener = new CancellingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePreUpdateEvent)

        def gormEvent = new GormPreUpdateEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        gormEvent.isCancelled()
    }

    void 'test onPersistenceEvent calls event.cancel when PreDelete handler returns true'() {
        given:
        def datastore = getDatastore()
        def listener = new CancellingHibernateEventListener(datastore)
        def entity = new Object()
        def mockNativeEvent = Mock(HibernatePreDeleteEvent)

        def gormEvent = new GormPreDeleteEvent(datastore, entity)
        gormEvent.setNativeEvent(mockNativeEvent)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        gormEvent.isCancelled()
    }

    void 'test onPersistenceEvent handles Validation event via onValidate'() {
        given:
        def datastore = getDatastore()
        def listener = new CancellingHibernateEventListener(datastore)
        def entity = new Object()

        def gormEvent = new GormValidationEvent(datastore, entity)

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        noExceptionThrown()
    }

    void 'test onPersistenceEvent throws for unexpected EventType'() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)

        def gormEvent = new org.grails.datastore.mapping.engine.event.SaveOrUpdateEvent(datastore, new Object())

        when:
        listener.onApplicationEvent(gormEvent)

        then:
        thrown(IllegalStateException)
    }

    void 'test getDatastore returns the HibernateDatastore'() {
        given:
        def datastore = getDatastore()
        def listener = new CancellingHibernateEventListener(datastore)

        expect:
        listener.getDatastore().is(datastore)
    }

    void 'test getTimestampProvider returns DefaultTimestampProvider'() {
        given:
        def listener = new CancellingHibernateEventListener(getDatastore())

        expect:
        listener.getTimestampProvider() instanceof DefaultTimestampProvider
    }

    void 'test real HibernateEventListener methods for coverage'() {
        given:
        def datastore = getDatastore()
        def listener = new HibernateEventListener(datastore) {
            @Override
            protected boolean isValidSource(org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent event) {
                return true
            }
        }
        def entity = new Object()
        def mockEventSource = Mock(EventSource)
        def mockSessionFactory = Mock(org.hibernate.engine.spi.SessionFactoryImplementor)
        def mockPersister = Mock(org.hibernate.persister.entity.EntityPersister)
        mockPersister.getFactory() >> mockSessionFactory
        mockEventSource.getSessionFactory() >> mockSessionFactory

        when: 'Calling onPersistEvent'
        def persistEvent = new HibernatePersistEvent('Foo', entity, mockEventSource)
        listener.onPersistEvent(persistEvent)

        then:
        noExceptionThrown()

        when: 'Calling onMergeEvent'
        def mergeEvent = new HibernateMergeEvent('Foo', entity, mockEventSource)
        listener.onMergeEvent(mergeEvent)

        then:
        noExceptionThrown()

        when: 'Calling onPreLoad'
        def preLoadEvent = new HibernatePreLoadEvent(mockEventSource)
        preLoadEvent.setEntity(entity)
        preLoadEvent.setPersister(mockPersister)
        listener.onPreLoad(preLoadEvent)

        then:
        noExceptionThrown()

        when: 'Calling onPostLoad'
        def postLoadEvent = new HibernatePostLoadEvent(mockEventSource)
        postLoadEvent.setEntity(entity)
        postLoadEvent.setPersister(mockPersister)
        listener.onPostLoad(postLoadEvent)

        then:
        noExceptionThrown()

        when: 'Calling onPostInsert'
        def postInsertEvent = new HibernatePostInsertEvent(entity, 1L, new Object[0], mockPersister, mockEventSource)
        listener.onPostInsert(postInsertEvent)

        then:
        noExceptionThrown()

        when: 'Calling onPreInsert'
        def preInsertEvent = new HibernatePreInsertEvent(entity, 1L, new Object[0], mockPersister, mockEventSource)
        listener.onPreInsert(preInsertEvent)

        then:
        noExceptionThrown()

        when: 'Calling onPreUpdate'
        def preUpdateEvent = new HibernatePreUpdateEvent(entity, 1L, new Object[0], new Object[0], mockPersister, mockEventSource)
        listener.onPreUpdate(preUpdateEvent)

        then:
        noExceptionThrown()

        when: 'Calling onPostUpdate'
        def postUpdateEvent = new HibernatePostUpdateEvent(entity, 1L, new Object[0], new Object[0], [0] as int[], mockPersister, mockEventSource)
        listener.onPostUpdate(postUpdateEvent)

        then:
        noExceptionThrown()

        when: 'Calling onPreDelete'
        def preDeleteEvent = new HibernatePreDeleteEvent(entity, 1L, new Object[0], mockPersister, mockEventSource)
        listener.onPreDelete(preDeleteEvent)

        then:
        noExceptionThrown()

        when: 'Calling onPostDelete'
        def postDeleteEvent = new HibernatePostDeleteEvent(entity, 1L, new Object[0], mockPersister, mockEventSource)
        listener.onPostDelete(postDeleteEvent)

        then:
        noExceptionThrown()

        when: 'Calling onValidate'
        def validationEvent = new GormValidationEvent(datastore, entity)
        listener.onValidate(validationEvent)

        then:
        noExceptionThrown()
    }
}
class CancellingHibernateEventListener extends HibernateEventListener {

    CancellingHibernateEventListener(org.grails.orm.hibernate.HibernateDatastore datastore) {
        super(datastore)
    }

    @Override
    boolean onPreInsert(HibernatePreInsertEvent event) { return true }

    @Override
    boolean onPreUpdate(HibernatePreUpdateEvent event) { return true }

    @Override
    boolean onPreDelete(HibernatePreDeleteEvent event) { return true }

    @Override
    protected boolean isValidSource(org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent event) {
        return true
    }
}
