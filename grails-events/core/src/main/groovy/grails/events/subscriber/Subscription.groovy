package grails.events.subscriber

import grails.events.Event
import grails.events.trigger.EventTrigger

/**
 * Represents a subscription to an event
 *
 * @author Graeme Rocher
 * @since 3.3
 */
interface Subscription {

    /**
     * @return Cancel the registration
     */
    Subscription cancel()
    /**
     * @return Whether it is cancelled
     */
    boolean isCancelled()

    /**
     * @return Builds a trigger
     */
    EventTrigger buildTrigger(Event event)

    /**
     * @return Builds a trigger
     */
    EventTrigger buildTrigger(Event event, Closure reply)
}