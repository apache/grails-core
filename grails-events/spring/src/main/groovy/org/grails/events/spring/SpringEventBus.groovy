package org.grails.events.spring

import grails.events.Event
import grails.events.subscriber.Subscription
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.grails.events.bus.AbstractEventBus
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.GenericApplicationListenerAdapter

import java.util.concurrent.Callable

/**
 * An event bus that uses the Spring Event Publisher
 *
 * @since 3.3
 * @author Graeme Rocher
 */
@AutoFinal
@CompileStatic
class SpringEventBus extends AbstractEventBus {

    final ConfigurableApplicationContext applicationContext

    SpringEventBus(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext
        applicationContext.addApplicationListener(new GenericApplicationListenerAdapter(
                new EventBusListener(subscriptions)
        ))
    }

    @Override
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        return {
            applicationContext.publishEvent(new SpringEventBusEvent(event, reply))
        }
    }

    private static class EventBusListener implements ApplicationListener<SpringEventBusEvent> {

        final Map<CharSequence, Collection<Subscription>> registrations

        EventBusListener(Map<CharSequence, Collection<Subscription>> registrations) {
            this.registrations = registrations
        }

        @Override
        void onApplicationEvent(SpringEventBusEvent event) {
            Event e = event.source
            Closure reply = event.replyTo
            for(reg in registrations.get(e.id)) {
                reg.buildTrigger(e, reply)
                    .proceed()
            }
        }
    }
}
