package org.grails.datastore.gorm.events

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext

/**
 * Bridge to Spring ApplicationContext event publishing
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class ConfigurableApplicationContextEventPublisher implements ConfigurableApplicationEventPublisher {

    final ConfigurableApplicationContext applicationContext

    ConfigurableApplicationContextEventPublisher(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }

    @Override
    void addApplicationListener(ApplicationListener<? extends ApplicationEvent> listener) {
        this.applicationContext.addApplicationListener(listener)
    }

    @Override
    void publishEvent(ApplicationEvent event) {
        this.applicationContext.publishEvent(event)
    }

    @Override
    void publishEvent(Object event) {
        this.applicationContext.publishEvent(event)
    }
}
