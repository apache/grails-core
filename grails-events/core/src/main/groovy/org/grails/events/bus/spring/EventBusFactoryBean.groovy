package org.grails.events.bus.spring

import grails.events.bus.EventBus
import grails.events.bus.EventBusBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.events.bus.ExecutorEventBus
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

import java.util.concurrent.ExecutorService

/**
 * Factory bean for usage within Spring
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Slf4j
@CompileStatic
class EventBusFactoryBean extends EventBusBuilder implements FactoryBean<EventBus>, InitializingBean, ApplicationContextAware {

    ApplicationContext applicationContext
    EventBus eventBus

    @Override
    EventBus getObject() throws Exception {
        return eventBus
    }

    @Override
    Class<?> getObjectType() {
        return EventBus
    }

    @Override
    boolean isSingleton() {
        return true
    }

    @Override
    void afterPropertiesSet() throws Exception {
        this.eventBus = super.build()
    }

    @Override
    protected EventBus createDefaultEventBus() {
        if(applicationContext.containsBean('grailsPromiseFactory')) {
            Object promiseFactory = applicationContext.getBean('grailsPromiseFactory')
            if(promiseFactory instanceof ExecutorService) {
                log.debug('Creating event bus from PromiseFactory {}', promiseFactory)
                return new ExecutorEventBus((ExecutorService)promiseFactory)
            }
        }
        return super.createDefaultEventBus()
    }
}
