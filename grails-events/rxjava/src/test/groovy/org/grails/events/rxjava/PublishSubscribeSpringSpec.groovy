package org.grails.events.rxjava

import grails.events.Event
import grails.events.bus.EventBusBuilder
import grails.events.annotation.Publisher
import grails.events.annotation.Subscriber
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Component
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class PublishSubscribeSpringSpec extends Specification {

    // Used to get transactions working
    @SuppressWarnings('unused')
    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore()

    def 'Test event publisher within Spring'() {

        given: 'a Spring application context with an event bus'
            AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()
            applicationContext.beanFactory.registerSingleton('eventBus', new EventBusBuilder().build())

        when: 'we register a publisher and subscriber'
            applicationContext.register(OneService, TwoService)
            applicationContext.refresh()
            OneService publisher = applicationContext.getBean(OneService)
            TwoService subscriber = applicationContext.getBean(TwoService)

        and: 'we invoke a method on the publisher'
            publisher.sum(1, 2)

        then: 'the subscriber is notified'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert !subscriber.error
                assert subscriber.total == 3
                assert subscriber.events.size() == 1
                assert subscriber.events[0].parameters == [a:1,b:2]
                assert subscriber.transactionalInvoked
            }

        when: 'we invoke a method on the publisher with the wrong type for the subscriber'
            publisher.wrongType()

        then: 'the subscriber is not notified'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert !subscriber.error
                assert subscriber.total == 3
                assert subscriber.events.size() == 2
            }

        when: 'we invoke a method on the publisher that throws an exception'
            publisher.badSum(1,2)

        then: 'an exception is thrown'
            def e = thrown(RuntimeException)
            e.message == 'bad'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert subscriber.error == e
                assert subscriber.events.size() == 3
                assert subscriber.total == 3
            }
    }
}

@Component
class OneService {

    @Publisher
    int sum(int a, int b) { a + b }

    @Publisher('sum')
    int badSum(int a, int b) { throw new RuntimeException('bad') }

    @Publisher('sum')
    Date wrongType() { new Date() }
}

@Component
class TwoService {

    int total = 0
    List<Event> events = []
    boolean transactionalInvoked = false
    Throwable error

    @Subscriber
    void onSum(int num) { total += num }

    @Subscriber
    void onSum(Throwable t) { error = t }

    @Subscriber('sum')
    void onSum2(Event event) { events.add(event) }

    @Subscriber('sum')
    @Transactional
    void doSomething(Event event) {
        assert transactionStatus
        transactionalInvoked = true
    }
}
