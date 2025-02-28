package grails.events.bus

import grails.events.emitter.EventEmitter
import grails.events.subscriber.Subjects

/**
 * An EventBus is both an {@link EventEmitter} and a {@link Subjects}
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@FunctionalInterface
interface EventBus extends EventEmitter, Subjects {

    /**
     * @return Whether the event bus is active or has been shutdown
     */
    boolean isActive()
}
