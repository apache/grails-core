package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Plant
import grails.persistence.Entity
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.springframework.context.ApplicationEvent
import static org.grails.datastore.mapping.engine.event.EventType.*

/**
 */
class CustomMongoEventListenerSpec extends GormDatastoreSpec{

    void "Test corrects are triggered for persistence life cycle"() {
        given:"A registered event listener"
            def listener = new MyPersistenceListener(session.datastore)
            session.datastore.applicationEventPublisher.addApplicationListener(listener)

        when:"An entity is saved"
            def p = new Listener(name:"Cabbage")
            p.save(flush:true)

        then:
            listener.preInsertCount == 1
            listener.postInsertCount == 1
            listener.preUpdateCount == 0
            listener.postUpdateCount == 0
            listener.preDeleteCount == 0
            listener.postDeleteCount == 0
            listener.preLoadCount == 0
            listener.postLoadCount == 0
    }

    @Override
    List getDomainClasses() {
        [Listener]
    }
}

@Entity
class Listener {
    Long id
    Long version
    String name

    def beforeInsert() {
        println "ENTITY PRE INSERT"
    }

    def afterInsert() {
        println "ENTITY POST INSERT"
    }
}

class MyPersistenceListener extends AbstractPersistenceEventListener {
    int preInsertCount
    int postInsertCount
    int preUpdateCount
    int postUpdateCount
    int preDeleteCount
    int postDeleteCount
    int preLoadCount
    int postLoadCount

    public MyPersistenceListener(final Datastore datastore) {
        super(datastore)
    }
    @Override
    protected void onPersistenceEvent(final AbstractPersistenceEvent event) {
        switch(event.eventType) {
            case PreInsert:
                println "LISTENER PRE INSERT ${event.entityObject}"
                preInsertCount++
                break
            case PostInsert:
                println "LISTENER POST INSERT ${event.entityObject}"
                postInsertCount++
                break
            case PreUpdate:
                println "LISTENER PRE UPDATE ${event.entityObject}"
                preUpdateCount++
                break;
            case PostUpdate:
                println "LISTENER POST UPDATE ${event.entityObject}"
                postUpdateCount++
                break;
            case PreDelete:
                println "LISTENER PRE DELETE ${event.entityObject}"
                preDeleteCount++
                break;
            case PostDelete:
                println "LISTENER POST DELETE ${event.entityObject}"
                postDeleteCount++
                break;
            case PreLoad:
                println "LISTENER PRE LOAD ${event.entityObject}"
                preLoadCount++
                break;
            case PostLoad:
                println "LISTENER POST LOAD ${event.entityObject}"
                postLoadCount++
                break;
        }
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return true
    }
}