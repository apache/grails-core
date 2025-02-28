package grails.events.emitter

import grails.events.Event
import org.springframework.transaction.event.TransactionPhase

/**
 * An emitter sends events
 *
 * @author Graeme Rocher
 * @since 3.3
 */
interface EventEmitter {

    /**
     * Notify of an event
     *
     * @param eventId The event
     * @param data The data
     *
     * @return This emitter
     */
    EventEmitter notify(CharSequence eventId, Object...data)

    /**
     * Notify of an event
     *
     * @param event The event
     *
     * @return This emitter
     */
    EventEmitter notify(Event event)

    /**
     * Notify of an event
     *
     * @param event The event
     * @param transactionPhase The transaction Phase to use if a transaction is present (defaults to {@link TransactionPhase#AFTER_COMMIT}
     *
     * @return This emitter
     */
    EventEmitter notify(Event event, TransactionPhase transactionPhase)

    /**
     * Synonym for {@link #notify(Event)}
     */
    EventEmitter publish(CharSequence eventId, Object...data)

    /**
     * Synonym for {@link #notify(Event)}
     */
    EventEmitter publish(Event event)

    /**
     * Synonym for {@link #notify(Event, org.springframework.transaction.event.TransactionPhase)}
     */
    EventEmitter publish(Event event, TransactionPhase transactionPhase)
    /**
     * Send and event and receive a reply. If the EventBus is asynchronous the reply may be invoked on a different thread to the caller
     *
     * @param event The event
     * @param reply The reply logic
     * @return This emitter
     */
    EventEmitter sendAndReceive(Event event, Closure reply)

    /**
     * Send and event and receive a reply. If the EventBus is asynchronous the reply may be invoked on a different thread to the caller
     *
     * @param eventId The event
     * @param reply The reply logic
     * @return This emitter
     */
    EventEmitter sendAndReceive(CharSequence eventId, Object data, Closure reply)
}