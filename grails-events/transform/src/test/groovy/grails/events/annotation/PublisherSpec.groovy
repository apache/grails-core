package grails.events.annotation

import grails.events.Event
import grails.events.EventPublisher
import grails.events.bus.EventBus
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.springframework.transaction.event.TransactionPhase
import spock.lang.Specification

import java.lang.reflect.Constructor

/**
 * Created by graemerocher on 28/03/2017.
 */
class PublisherSpec extends Specification {

    void 'Test publisher transform on method that returns void'() {

        given: 'a class with a method annotated with the @Publisher annotation'
            def service = new GroovyClassLoader().parseClass('''
            class TestService {
                @grails.events.annotation.Publisher('total')
                void sum(int a, int b) { a + b }
            }
            ''').getDeclaredConstructor().newInstance()

        and: 'we set a target event bus for the class'
            def eventBus = Mock(EventBus)
            service.targetEventBus = eventBus

        when: 'we invoke the annotated method on the class'
            service.sum(1,2)

        then: 'the event is published in the event bus'
            1 * eventBus.publish(new Event('total', [a:1, b:2], null))
    }


    void 'Test publisher transform'() {

        given: 'a class with a method annotated with the @Publisher annotation'
            def service = new GroovyClassLoader().parseClass('''
            class TestService {
                @grails.events.annotation.Publisher('total')
                Integer sum(int a, int b) { a + b }
            }
            ''').getDeclaredConstructor().newInstance()

        when: 'we set a target event bus for the class'
            def eventBus = Mock(EventBus)
            service.targetEventBus = eventBus

        then: 'the class is an instance of EventPublisher'
            service instanceof EventPublisher

        when: 'we invoke the annotated method on the class'
            def result = service.sum(1,2)

        then: 'the event is published in the event bus'
            result == 3
            1 * eventBus.publish(new Event('total', [a:1, b:2], 3))
    }

    void 'Test publisher transform on transactional service'() {

        given: 'a class with a method annotated with @Publisher and @Transactional'
            def service = new GroovyClassLoader().parseClass('''
            class TestService {
                @grails.events.annotation.Publisher('total')
                @grails.gorm.transactions.Transactional
                Integer sum(int a, int b) { a + b }
            }''').getDeclaredConstructor().newInstance()

        when: 'we set a target event bus and a transaction manager for the class'
            def eventBus = Mock(EventBus)
            service.targetEventBus = eventBus
            def manager = new DatastoreTransactionManager()
            def datastore = Mock(Datastore)
            datastore.connect() >> Mock(Session)
            manager.setDatastore(datastore)
            service.transactionManager = manager

        then: 'the class is an instance of EventPublisher'
            service instanceof EventPublisher

        when: 'we invoke the annotated method on the class'
            def result = service.sum(1,2)

        then: 'the event is published in the event bus'
            result == 3
            1 * eventBus.notify(new Event('total', [a:1, b:2], 3), TransactionPhase.AFTER_COMMIT)
    }
}
