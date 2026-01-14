package org.grails.orm.hibernate.event.listener

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.engine.event.MergeEvent as GormMergeEvent
import org.grails.datastore.mapping.engine.event.PersistEvent as GormPersistEvent
import org.hibernate.event.spi.MergeEvent as HibernateMergeEvent
import org.hibernate.event.spi.PersistEvent as HibernatePersistEvent
import org.hibernate.event.spi.EventSource

class HibernateEventListenerSpec extends HibernateGormDatastoreSpec {

    class RecordingHibernateEventListener extends HibernateEventListener {
        boolean onMergeEventCalled = false
        boolean onPersistEventCalled = false
        HibernateMergeEvent lastMergeEvent
        HibernatePersistEvent lastPersistEvent

        RecordingHibernateEventListener(org.grails.orm.hibernate.AbstractHibernateDatastore datastore) {
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
        protected boolean isValidSource(org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent event) {
            return true
        }
    }

    void "test onPersistenceEvent handles Merge event without fall-through"() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def hibernateSession = Mock(EventSource)

        and: "a GORM Merge event wrapping a Hibernate Merge event"
        def hibernateMergeEvent = new HibernateMergeEvent("Foo", entity, hibernateSession)
        def gormMergeEvent = new GormMergeEvent(datastore, entity)
        gormMergeEvent.setNativeEvent(hibernateMergeEvent)

        when: "Merge event is published"
        listener.onApplicationEvent(gormMergeEvent)

        then: "Only onMergeEvent is called"
        listener.onMergeEventCalled
        listener.lastMergeEvent == hibernateMergeEvent
        !listener.onPersistEventCalled
    }

    void "test onPersistenceEvent handles Persist event without fall-through"() {
        given:
        def datastore = getDatastore()
        def listener = new RecordingHibernateEventListener(datastore)
        def entity = new Object()
        def hibernateSession = Mock(EventSource)

        and: "a GORM Persist event wrapping a Hibernate Persist event"
        def hibernatePersistEvent = new HibernatePersistEvent("Foo", entity, hibernateSession)
        def gormPersistEvent = new GormPersistEvent(datastore, entity)
        gormPersistEvent.setNativeEvent(hibernatePersistEvent)

        when: "Persist event is published"
        listener.onApplicationEvent(gormPersistEvent)

        then: "Only onPersistEvent is called"
        listener.onPersistEventCalled
        listener.lastPersistEvent == hibernatePersistEvent
        !listener.onMergeEventCalled
    }
}