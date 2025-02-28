package grails.events.subscriber

import grails.events.Event
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import java.lang.reflect.Method

/**
 * A method subscribers for methods that accept an event argument
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class MethodEventSubscriber<T> extends MethodSubscriber<Event,T> implements EventSubscriber<T> {

    MethodEventSubscriber(Object target, Method method) {
        super(target, method)
        if( !(parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(Event)) ) {
            throw new IllegalArgumentException('Specified method must accept an Event as an argument')
        }
    }
}
