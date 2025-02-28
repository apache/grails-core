package org.grails.events.spring

import grails.events.Event
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.springframework.context.ApplicationEvent

/**
 * An event issues by the {@link SpringEventBus}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@AutoFinal
@CompileStatic
class SpringEventBusEvent extends ApplicationEvent {

    final Closure replyTo

    /**
     * Create a new ApplicationEvent.
     * @param source the object on which the event initially occurred (never {@code null})
     */
    SpringEventBusEvent(Event source, Closure replyTo = null) {
        super(source)
        this.replyTo = replyTo
    }

    @Override
    Event getSource() {
        return (Event) super.getSource()
    }
}
