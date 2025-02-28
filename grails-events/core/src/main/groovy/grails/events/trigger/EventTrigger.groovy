package grails.events.trigger

import grails.events.Event
import grails.events.subscriber.Subscriber

/**
 * Encapsulates the execution of an event
 *
 * @author Graeme Rocher
 * @since 3.3
 */
interface EventTrigger<T> {

    /**
     * @return The event being triggered
     */
    Event<T> getEvent()

    /**
     * @return The event listener
     */
    Subscriber getSubscriber()

    /**
     * @return Proceed to trigger the event
     */
    Object proceed()
}