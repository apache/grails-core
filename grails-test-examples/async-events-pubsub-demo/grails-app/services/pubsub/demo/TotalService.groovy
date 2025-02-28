package pubsub.demo

import grails.events.Event
import grails.events.annotation.Subscriber
import groovy.transform.CompileStatic

import java.util.concurrent.atomic.AtomicInteger

@CompileStatic
class TotalService {

    AtomicInteger accumulatedTotalInstance = new AtomicInteger(0)

    int getAccumulatedTotal() {
        accumulatedTotalInstance.get()
    }

    @Subscriber
    @SuppressWarnings('unused')
    void onSum(int total) {
        accumulatedTotalInstance.addAndGet(total)
    }

    @Subscriber
    @SuppressWarnings('unused')
    void onSum(Event<Integer> event) {
        println "Event $event.id"
        println "Data $event.data"
        println "Parameters $event.parameters"
    }

    @Subscriber
    @SuppressWarnings('unused')
    void onSum(Throwable error) {
        println "Oh NO!!! $error.message"
    }
}
