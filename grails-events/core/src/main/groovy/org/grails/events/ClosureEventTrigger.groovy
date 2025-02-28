package org.grails.events

import grails.events.Event
import grails.events.subscriber.Subscriber
import grails.events.trigger.EventTrigger
import groovy.transform.AutoFinal
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

/**
 * Triggers an event
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class ClosureEventTrigger<T> implements EventTrigger<T> {

    final Event<T> event
    final Closure subscriberClosure
    final Closure reply
    private final T data
    private final int argCount
    private final boolean eventArg

    ClosureEventTrigger(Event<T> event, Closure subscriber, Closure reply = null) {
        this.event = event
        this.subscriberClosure = subscriber
        this.reply = reply
        this.data = event.data
        Closure closure = (Closure)subscriber
        Class[] parameterTypes = closure.parameterTypes
        this.argCount = parameterTypes.length
        this.eventArg = argCount == 1 && Event.isAssignableFrom(parameterTypes[0])
    }

    @Override
    Subscriber getSubscriber() {
        return subscriberClosure as Subscriber
    }

    @Override
    Object proceed() {
        int dataLength = data.getClass().isArray() ? ((Object[])data).length : 1

        boolean isSpread = !eventArg && dataLength > 1 && argCount == dataLength
        try {
            def result
            if(isSpread) {
                result = callSpread(subscriberClosure, data)
            }
            else {
                if(eventArg) {
                    result = subscriberClosure.call(event)
                }
                else {
                    result = subscriberClosure.call(data)
                }
            }
            if(reply != null) {
                return reply.call(result)
            }
            return result
        } catch (Throwable e) {
            if(reply != null && reply.parameterTypes && reply.parameterTypes[0].isInstance(e)) {
                reply.call(e)
            }
            else {
                throw e
            }
        }
    }

    @CompileDynamic
    protected Object callSpread(Closure listener, Object data) {
        listener.call(*data)
    }
}
