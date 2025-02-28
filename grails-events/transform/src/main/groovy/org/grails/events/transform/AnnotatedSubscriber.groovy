package org.grails.events.transform

import grails.events.Event
import grails.events.annotation.Events
import grails.events.bus.EventBusAware
import grails.events.subscriber.MethodEventSubscriber
import grails.events.subscriber.MethodSubscriber
import grails.events.annotation.Subscriber
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.events.EventIdUtils
import org.springframework.util.ReflectionUtils

import jakarta.annotation.PostConstruct
import java.beans.Introspector
import java.lang.reflect.Method

/**
 * Registers subscribed methods. Used by the {@link Subscriber} transformation
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
trait AnnotatedSubscriber extends EventBusAware {

    List<Method> getSubscribedMethods() {
        return []
    }

    @PostConstruct
    void registerMethods() {
        Events events = getClass().getAnnotation(Events)
        for(Method m in subscribedMethods) {
            ReflectionUtils.makeAccessible(m)
            Subscriber sub = m.getAnnotation(Subscriber)
            if(sub != null) {
                String eventId = sub.value()
                if(!eventId) {
                    eventId = EventIdUtils.eventIdForMethodName(m.name)
                }

                String namespace = events?.namespace()
                if(namespace) {
                    eventId = namespace + ':' + eventId
                }

                Class[] parameterTypes = m.parameterTypes
                boolean hasArgument = parameterTypes.length == 1
                if(hasArgument && AbstractPersistenceEvent.isAssignableFrom(parameterTypes[0])) {
                    eventId = "gorm:${Introspector.decapitalize(parameterTypes[0].simpleName)}" - "Event"
                    eventBus.subscribe(eventId, new MethodSubscriber(this, m))
                }
                else if(hasArgument && parameterTypes[0].isAssignableFrom(Event)) {
                    eventBus.subscribe(eventId, new MethodEventSubscriber(this, m))
                }
                else {
                    eventBus.subscribe(eventId, new MethodSubscriber(this, m))
                }
            }
        }
    }
}