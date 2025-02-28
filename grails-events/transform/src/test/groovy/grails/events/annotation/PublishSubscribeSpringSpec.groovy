package grails.events.annotation

import grails.events.Event
import grails.events.bus.EventBusBuilder
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Component
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Created by graemerocher on 29/03/2017.
 */
class PublishSubscribeSpringSpec extends Specification {

    // This is needed to configure GORM
    @SuppressWarnings('unused')
    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore()

    def 'Test event publisher within Spring'() {

        given: 'An application context with a publisher and subscriber'
            def applicationContext = new AnnotationConfigApplicationContext()
            applicationContext.beanFactory.registerSingleton('eventBus', new EventBusBuilder().build())
            applicationContext.register(OneService, TwoService)
            applicationContext.refresh()
            def publisher = applicationContext.getBean(OneService)
            def subscriber = applicationContext.getBean(TwoService)

        when: 'we invoke a method on the publisher'
            publisher.sum(1, 2)

        then: 'the subscriber should receive the event'
            !subscriber.error
            subscriber.total == 3
            subscriber.events.size() == 1
            subscriber.events[0].parameters == [a:1,b:2]
            subscriber.transactionalInvoked

        when: 'we invoke a method on the publisher that returns the wrong type'
            publisher.wrongType()

        then: 'the subscriber should receive the event'
            subscriber.total == 3
            subscriber.events.size() == 2
            !subscriber.error

        when: 'we invoke a method on the publisher that throws an exception'
            publisher.badSum(1, 2)

        then: 'an exception should be thrown and the subscriber should receive the event'
            def e = thrown(RuntimeException)
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert e.message == 'bad'
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
        transactionStatus != null
        transactionalInvoked = true
    }
}
