package org.grails.events.bus

import grails.events.Event
import grails.events.subscriber.Subscription
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import java.util.concurrent.Callable

/**
 * A default synchronous event bus for testing
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class SynchronousEventBus extends AbstractEventBus {

    @Override
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        return {
            for(Subscription subscription : eventSubscriptions) {
                subscription.buildTrigger(event, reply)
                        .proceed()
            }
        }
    }
}
