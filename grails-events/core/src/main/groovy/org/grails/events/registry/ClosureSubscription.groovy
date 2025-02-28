package org.grails.events.registry

import grails.events.Event
import grails.events.trigger.EventTrigger
import grails.events.subscriber.Subscription
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.grails.events.ClosureEventTrigger

/**
 * A subscription via a Closure
 *
 * @since 3.3
 * @author Graeme Rocher
 */
@AutoFinal
@CompileStatic
class ClosureSubscription extends AbstractSubscription {

    final Closure listener

    ClosureSubscription(CharSequence eventKey, Map<CharSequence, Collection<Subscription>> subscriptions, Closure listener) {
        super(eventKey, subscriptions)
        this.listener = listener
    }

    @Override
    EventTrigger buildTrigger(Event event, Closure reply = null) {
        return new ClosureEventTrigger(event, listener, reply)
    }
}