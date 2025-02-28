package grails.events.annotation.namespace

import grails.events.Event
import grails.events.annotation.Events
import grails.events.annotation.Publisher
import grails.events.annotation.Subscriber
import grails.events.bus.EventBusAware
import org.grails.events.transform.AnnotatedSubscriber
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by graemerocher on 03/04/2017.
 */
class NamespaceSpec extends Specification {

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
            totalService.eventId == 'math:sum'
    }
}

// tag::publisher[]
@Events(namespace = "math")
class SumService {
    @Publisher
    int sum(int a, int b) {
        a + b
    }
}
// end::publisher[]

// tag::subscriber[]
@Events(namespace = "math")
class TotalService {
    AtomicInteger total = new AtomicInteger(0)
    String eventId

    @Subscriber
    void onSum(Event<Number> event) {
        eventId = event.id
        total.addAndGet(event.data.intValue())
    }
}
// end::subscriber[]

