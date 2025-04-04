package org.grails.datastore.gorm.events

import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener

/**
 * An {@link ApplicationEventPublisher} that is configurable with new listeners
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface ConfigurableApplicationEventPublisher extends ApplicationEventPublisher {

    /**
     * Adds a new application listener
     *
     * @param listener The application listener
     */
    void addApplicationListener(ApplicationListener<?> listener)
}