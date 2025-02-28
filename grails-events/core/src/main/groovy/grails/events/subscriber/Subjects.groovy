package grails.events.subscriber
/**
 * An event subscriber
 *
 * @author Graeme Rocher
 * @since 3.3
 */
interface Subjects {

    /**
     * Listen for an event
     *
     * @param event The event
     * @param subscriber The listener
     */
    Subscription on(CharSequence event, Closure subscriber)

    /**
     * Listen for an event
     *
     * @param event The event
     * @param subscriber The listener
     */
    Subscription subscribe(CharSequence event, Closure subscriber)

    /**
     * Listen for an event
     *
     * @param event The event
     * @param subscriber The listener
     */
    Subscription subscribe(CharSequence event, Subscriber subscriber)


    /**
     * Clear all listeners for the given event
     *
     * @param event The even id
     *
     * @return The subscriber
     */
    Subjects unsubscribeAll(CharSequence event)


}