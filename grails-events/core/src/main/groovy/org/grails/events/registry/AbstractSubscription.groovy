package org.grails.events.registry

import grails.events.subscriber.Subscription
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

/**
 * Abstract subscription
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
abstract class AbstractSubscription implements Subscription {

    final CharSequence eventKey
    private final Map<CharSequence, Collection<Subscription>> subscriptions

    AbstractSubscription(CharSequence eventKey, Map<CharSequence, Collection<Subscription>> subscriptions) {
        this.eventKey = eventKey
        this.subscriptions = subscriptions
        this.subscriptions.get(eventKey).add(this)
    }

    @Override
    Subscription cancel() {
        subscriptions.get(eventKey).remove(this)
        return this
    }

    @Override
    boolean isCancelled() {
        return !subscriptions.get(eventKey).contains(this)
    }
}
