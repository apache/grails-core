package org.grails.datastore.mapping.simple

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.DatastoreUtils
import spock.lang.Specification
import org.grails.datastore.mapping.model.PersistentEntity
import grails.gorm.annotation.Entity
import org.springframework.context.ApplicationEventPublisher
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent

class SimpleMapEventsSpec extends Specification {

    void "test events are fired during persistence"() {
        given:
        def events = []
        def publisher = [
            publishEvent: { event -> events << event }
        ] as ApplicationEventPublisher
        
        def datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(null), publisher, TestEventEntity)
        def session = datastore.connect()
        
        when:
        def entity = new TestEventEntity(name: "test")
        session.insert(entity)
        session.flush()
        
        then:
        events.any { it instanceof PreInsertEvent }
        events.any { it instanceof PostInsertEvent }
    }
}

@Entity
class TestEventEntity {
    Long id
    String name
}
