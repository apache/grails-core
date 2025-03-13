package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.IgnoreIf
import spock.lang.PendingFeatureIf
import spock.util.concurrent.PollingConditions

class DirtyCheckingAfterListenerSpec extends GormDatastoreSpec {

    TestSaveOrUpdateEventListener listener
    def datastore

    @Override
    List getDomainClasses() {
        return [TestPlayer]
    }

    def setup() {
        datastore = session.datastore
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
        new PollingConditions().eventually { listener.isExecuted && TestPlayer.count()}

        when:
        session.flush()
        session.clear()
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