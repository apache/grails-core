package org.grails.events.gorm

import grails.events.annotation.Events
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.events.transform.AnnotatedSubscriber

/**
 * The events subscribed to
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
@Events(namespace = "gorm")
trait GormAnnotatedSubscriber extends AnnotatedSubscriber {

    Set<Class<? extends AbstractPersistenceEvent>> getSubscribedEvents() {
        return [AbstractPersistenceEvent] as Set<Class<? extends AbstractPersistenceEvent>>
    }

}
