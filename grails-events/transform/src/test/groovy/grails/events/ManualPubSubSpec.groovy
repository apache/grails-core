package grails.events

import grails.events.bus.EventBusAware
import spock.lang.Specification

import jakarta.annotation.PostConstruct
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by graemerocher on 03/04/2017.
 */
class ManualPubSubSpec extends Specification {

    void 'Test pub/sub with default event bus'() {

        given: 'A publisher and subscriber'
            def sumService = new SumService()
            def totalService = new TotalService()
            def annotatedSubscriber = totalService as EventBusAware
            def publisher = sumService as EventBusAware

        and: 'we set the target event bus'
            annotatedSubscriber.setTargetEventBus(publisher.getEventBus())
            totalService.init()

        when: 'we invoke methods on the publisher'
            sumService.sum(1,2)
            sumService.sum(1,2)

        then: 'the subscriber should receive the events'
            totalService.total.intValue() == 6
    }
}

// tag::publisher[]
class SumService implements EventPublisher {

    int sum(int a, int b) {
        int result = a + b
        notify('sum', result)
        return result
    }
}
// end::publisher[]

// tag::subscriber[]
class TotalService implements EventBusAware {

    AtomicInteger total = new AtomicInteger(0)

    @PostConstruct
    void init() {
        eventBus.subscribe('sum') { int num ->
            total.addAndGet(num)
        }
    }
}
// end::subscriber[]
