package org.grails.events.gpars

import grails.events.Event
import grails.events.subscriber.Subscription
import grails.events.trigger.EventTrigger
import groovy.transform.AutoFinal
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovyx.gpars.actor.Actor
import org.grails.events.bus.AbstractEventBus

import java.util.concurrent.Callable

import static groovyx.gpars.actor.Actors.actor

/**
 * A event bus that uses GPars actors
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class ActorEventBus extends AbstractEventBus implements Closeable {

    final Actor actor

    @CompileDynamic
    ActorEventBus() {
        actor = actor {
            loop {
                react() { Event msg ->
                    if (subscriptions.containsKey(msg.id)) {
                        Collection<Subscription> subscriptions = subscriptions.get(msg.id)
                        for (Subscription sub : subscriptions) {
                            try {
                                EventTrigger eventTrigger = sub.buildTrigger(msg)
                                replyIfExists( eventTrigger.proceed() )
                            } catch (Throwable e) {
                                replyIfExists(e)
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        Actor actor = this.actor
        return {
            if (reply != null) {
                actor.sendAndContinue(event, reply)
            }
            else {
                actor.send(event)
            }
        }
    }

    @Override
    void close() throws IOException {
        if (actor.isActive()) {
            actor.stop()
        }
    }

    @Override
    boolean isActive() {
        return actor.isActive()
    }
}
