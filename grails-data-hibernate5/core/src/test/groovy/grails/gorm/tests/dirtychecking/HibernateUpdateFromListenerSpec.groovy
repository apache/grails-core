package grails.gorm.tests.dirtychecking

import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class HibernateUpdateFromListenerSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(Person)
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    PersonSaveOrUpdatePersistentEventListener listener

    void setup() {
        listener = new PersonSaveOrUpdatePersistentEventListener(datastore)
        ApplicationEventPublisher publisher = datastore.applicationEventPublisher
        if (publisher instanceof ConfigurableApplicationEventPublisher) {
            ((ConfigurableApplicationEventPublisher) publisher).addApplicationListener(listener)
        } else if (publisher instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext) publisher).addApplicationListener(listener)
        }
    }

    @Rollback
    void "test the changes made from the listener are saved"() {
        when:
        Person danny = new Person(name: "Danny", occupation: "manager").save()

        then:
        new PollingConditions().eventually {listener.isExecuted && Person.count()}

        when:
        datastore.currentSession.flush()
        datastore.currentSession.clear()
        danny = Person.get(danny.id)

        then:
        danny.occupation
        danny.occupation.endsWith("listener")
    }

    static class PersonSaveOrUpdatePersistentEventListener extends AbstractPersistenceEventListener {

        boolean isExecuted

        protected PersonSaveOrUpdatePersistentEventListener(Datastore datastore) {
            super(datastore)
        }

        @Override
        protected void onPersistenceEvent(AbstractPersistenceEvent event) {
            if (event.entityObject instanceof Person) {
                Person person = (Person) event.entityObject
                person.occupation = person.occupation + " listener"
            }
            isExecuted = true
        }

        @Override
        boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
            return eventType == PreUpdateEvent || eventType == PreInsertEvent
        }
    }
}
