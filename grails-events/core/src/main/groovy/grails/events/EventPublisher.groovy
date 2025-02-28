package grails.events

import grails.events.bus.EventBusAware
import grails.events.emitter.EventEmitter
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.springframework.transaction.event.TransactionPhase

/**
 * A trait that can be implemented to make a class an event publisher
 *
 * @since 3.3
 * @author Graeme Rocher
 */
@AutoFinal
@CompileStatic
trait EventPublisher extends EventBusAware implements EventEmitter {

    /**
     * @see {@link EventEmitter#notify(java.lang.CharSequence, java.lang.Object[])}
     */
    @Override
    EventEmitter notify(CharSequence eventId, Object... data) {
        return eventBus.notify(eventId, data)
    }

    /**
     * @see {@link EventEmitter#notify(Event)}
     */
    @Override
    EventEmitter notify(Event event) {
        return eventBus.notify(event)
    }

    /**
     * @see {@link EventEmitter#notify(Event, org.springframework.transaction.event.TransactionPhase)}
     */
    @Override
    EventEmitter notify(Event event, TransactionPhase transactionPhase) {
        return eventBus.notify(event, transactionPhase)
    }

    /**
     * @see {@link EventEmitter#notify(Event, org.springframework.transaction.event.TransactionPhase)}
     */
    @Override
    EventEmitter publish(Event event, TransactionPhase transactionPhase) {
        return eventBus.notify(event, transactionPhase)
    }

    /**
     * @see {@link EventEmitter#notify(java.lang.CharSequence, java.lang.Object[])} )}
     */
    @Override
    EventEmitter publish(CharSequence eventId, Object... data) {
        return eventBus.publish(eventId, data)
    }

    /**
     * @see {@link EventEmitter#publish(Event)}
     */
    @Override
    EventEmitter publish(Event event) {
        return eventBus.publish(event)
    }

    /**
     * @see {@link EventEmitter#sendAndReceive(Event, groovy.lang.Closure)}
     */
    @Override
    EventEmitter sendAndReceive(Event event, Closure reply) {
        return eventBus.sendAndReceive(event, reply)
    }

    /**
     * @see {@link EventEmitter#sendAndReceive(Event, groovy.lang.Closure)}
     */
    @Override
    EventEmitter sendAndReceive(CharSequence eventId, Object data, Closure reply) {
        return eventBus.sendAndReceive(eventId, data, reply)
    }
}