package org.grails.datastore.gorm.events

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.event.SmartApplicationListener

/**
 * Simple implementation that just iterates over a sequence of listeners
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class DefaultApplicationEventPublisher implements ConfigurableApplicationEventPublisher {

    private List<ApplicationListener> applicationListeners = []
    @Override
    void publishEvent(ApplicationEvent event) {
        for(listener in applicationListeners) {
            if(listener instanceof SmartApplicationListener) {
                SmartApplicationListener smartApplicationListener = (SmartApplicationListener) listener
                if( !smartApplicationListener.supportsEventType((Class<ApplicationEvent>)event.getClass()) ) {
                    continue
                }
                else if(!smartApplicationListener.supportsSourceType(event.source.getClass())) {
                    continue
                }
            }
            listener.onApplicationEvent(event)
        }
    }

    @Override
    void publishEvent(Object event) {
        for(listener in applicationListeners) {
            def eventObject = new PayloadApplicationEvent<Object>(this, event)
            if(listener instanceof SmartApplicationListener) {
                SmartApplicationListener smartApplicationListener = (SmartApplicationListener) listener
                if( !smartApplicationListener.supportsEventType((Class<ApplicationEvent>)eventObject.getClass()) ) {
                    continue
                }
                else if(!smartApplicationListener.supportsSourceType(eventObject.source.getClass())) {
                    continue
                }
            }

            listener.onApplicationEvent(eventObject)
        }
    }

    @Override
    void addApplicationListener(ApplicationListener<?> listener) {
        applicationListeners.add(listener)
    }
}
