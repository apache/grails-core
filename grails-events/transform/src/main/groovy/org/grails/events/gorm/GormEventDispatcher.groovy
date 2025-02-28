package org.grails.events.gorm

import grails.events.bus.EventBus
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.springframework.context.ApplicationEvent

import java.beans.Introspector

/**
 * Dispatches GORM events to the {@link EventBus}
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class GormEventDispatcher extends AbstractPersistenceEventListener {

    private static final String GORM_NAMESPACE = "gorm:"
    protected final EventBus eventBus
    protected final Map<Class<? extends AbstractPersistenceEvent>, String> subscribedEvents
    protected final Set<Class<? extends AbstractPersistenceEvent>> listenedForEvents = []
    protected final List<GormAnnotatedListener> listeners
    protected final boolean hasEventSubscribers
    protected final boolean hasListeners

    GormEventDispatcher(EventBus eventBus, Datastore datastore, Set<Class<? extends AbstractPersistenceEvent>> subscribedEvents, List<GormAnnotatedListener> listeners) {
        super(datastore)
        this.eventBus = eventBus
        Map<Class<? extends AbstractPersistenceEvent>, String> subscribedEventMap = [:]
        for(event in subscribedEvents) {
            subscribedEventMap.put(event, GORM_NAMESPACE + (Introspector.decapitalize(event.simpleName) - "Event"))
        }
        this.subscribedEvents = Collections.unmodifiableMap(subscribedEventMap)
        this.listeners = Collections.unmodifiableList(listeners)
        this.hasListeners = !listeners.isEmpty()
        this.hasEventSubscribers = !subscribedEvents.isEmpty() || hasListeners
        if(hasListeners) {
            for(listener in listeners) {
                listenedForEvents.addAll(listener.subscribedEvents)
            }
        }
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        if(hasListeners && listenedForEvents.contains(event.getClass())) {
            for(listener in listeners) {
                if(listener.supports(event)) {
                    listener.dispatch(event)
                }
            }
        }

        String eventName = subscribedEvents.get(event.getClass())
        if(eventName != null) {
            eventBus.notify(eventName, event)
        }
    }

    @Override
    boolean supportsSourceType(Class<?> sourceType) {
        return hasEventSubscribers && super.supportsSourceType(sourceType)
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> aClass) {
        return hasEventSubscribers &&
                AbstractPersistenceEvent.isAssignableFrom(aClass) &&
                (subscribedEvents.containsKey(aClass) || listenedForEvents.contains(aClass) )
    }
}
