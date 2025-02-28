package grails.events.subscriber

import grails.events.Event

/**
 * An interface for subscribers that accept the Event as an argument
 *
 * @author Graeme Rocher
 * @since 3.3
 */
interface EventSubscriber<T> extends Subscriber<Event, T> {
}
