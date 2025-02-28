package org.grails.events.gorm

import grails.events.annotation.gorm.Listener
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ReflectionUtils

import java.lang.reflect.Method

/**
 * Marks a class as a synchronous listener of GORM events
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
trait GormAnnotatedListener extends GormAnnotatedSubscriber {

    private static final Logger log = LoggerFactory.getLogger(GormAnnotatedListener)
    /**
     * Whether the listener supports the given event
     * @param event The event
     * @return True if it does
     */
    boolean supports(AbstractPersistenceEvent event) {
        getSubscribedEvents().contains(event.getClass())
    }
    /**
     * Dispatch the event to this listener
     * @param event
     */
    void dispatch(AbstractPersistenceEvent event) {
        def entity = event.getEntityObject()
        for(Method method : getSubscribedMethods()) {
            Class[] types = method.getAnnotation(Listener)?.value()
            boolean applies = types == null || types.length == 0 || types.any() { Class cls -> cls.isInstance(entity) }
            if(applies && method.parameterTypes[0].isInstance(event)) {
                try {
                    log.debug("Invoking method [{}] for event [{}]", method, event)
                    ReflectionUtils.invokeMethod(method, this, event)
                } catch (Throwable e) {
                    log.error("Error triggering event [$event] for listener [${method}]: $e.message", e)
                    throw e
                }
            }
        }
    }
}
