package org.grails.events

import grails.events.Event
import grails.events.subscriber.EventSubscriber
import grails.events.subscriber.Subscriber
import grails.events.trigger.EventTrigger
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Simple trigger for an Subscriber
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Slf4j
@AutoFinal
@CompileStatic
class EventSubscriberTrigger implements EventTrigger {

    final Event event
    final Subscriber subscriber

    EventSubscriberTrigger(Event event, Subscriber subscriber) {
        this.event = event
        this.subscriber = subscriber
    }

    @Override
    Object proceed() {
        try {
            if(subscriber instanceof EventSubscriber) {
                return subscriber.call(event)
            }
            else {
                return subscriber.call(event.data)
            }
        } catch (Throwable e) {
            log.error("Error triggering event [$event.id] for subscriber [${subscriber}]: $e.message", e)
            throw e
        }
    }
}
