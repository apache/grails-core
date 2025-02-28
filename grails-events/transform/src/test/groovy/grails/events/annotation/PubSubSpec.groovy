package grails.events.annotation

import grails.events.bus.EventBusAware
import org.grails.events.transform.AnnotatedSubscriber
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class PubSubSpec extends Specification {

    void 'Test pub/sub with default event bus'() {

        given: 'A publisher and subscriber'
            def sumService = new SumService()
            def totalService = new TotalService()
            def annotatedSubscriber = totalService as AnnotatedSubscriber
            def publisher = sumService as EventBusAware
            annotatedSubscriber.setTargetEventBus(publisher.getEventBus())
            annotatedSubscriber.registerMethods()

        when: 'we invoke methods on the publisher'
            sumService.sum(1,2)
            sumService.sum(1,2)

        then: 'the subscriber should receive the events'
            totalService.total.intValue() == 6
    }
}

// tag::publisher[]
class SumService {

    @Publisher
    int sum(int a, int b) { a + b }
}
// end::publisher[]

// tag::subscriber[]
class TotalService {

    AtomicInteger total = new AtomicInteger(0)

    @Subscriber
    void onSum(int num) { total.addAndGet(num) }
}
// end::subscriber[]
