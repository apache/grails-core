package org.grails.events.gorm

import grails.events.annotation.Subscriber
import grails.events.bus.EventBus
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher

/**
 * Handles registering of GORM event listeners for {@link GormAnnotatedSubscriber} instances
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class GormDispatcherRegistrar implements FactoryBean<GormDispatcherRegistrar>, InitializingBean {

    @Autowired(required = false) Datastore[] datastores
    @Autowired(required = false) GormAnnotatedSubscriber[] subscribers

    protected final EventBus eventBus

    GormDispatcherRegistrar(EventBus eventBus) {
        this.eventBus = eventBus
    }

    @Override
    GormDispatcherRegistrar getObject() throws Exception {
        return this
    }

    @Override
    Class<?> getObjectType() {
        return GormDispatcherRegistrar
    }

    @Override
    boolean isSingleton() {
        return true
    }

    @Override
    void afterPropertiesSet() throws Exception {
        if(datastores && subscribers && eventBus != null) {
            Set<Class<? extends AbstractPersistenceEvent>> subscribedEvents = []
            List<GormAnnotatedListener> listeners = []
            for(sub in subscribers) {
                if(sub instanceof GormAnnotatedListener) {
                    listeners.add((GormAnnotatedListener)sub)
                }
                else {
                    subscribedEvents.addAll(sub.getSubscribedEvents())
                }
            }
            for(Datastore datastore in datastores) {
                ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher()
                if(applicationEventPublisher instanceof ConfigurableApplicationEventPublisher) {

                    GormEventDispatcher eventDispatcher = new GormEventDispatcher(eventBus, datastore, subscribedEvents, listeners )
                    ((ConfigurableApplicationEventPublisher)applicationEventPublisher).addApplicationListener(
                            eventDispatcher
                    )
                }
            }
        }
    }
}
