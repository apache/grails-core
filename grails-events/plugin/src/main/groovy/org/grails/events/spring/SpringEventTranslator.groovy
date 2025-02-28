/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.events.spring

import grails.events.bus.EventBus
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.support.GenericApplicationContext
import java.util.concurrent.ConcurrentHashMap


/**
 * Translates Spring Events into Reactor events
 *
 * @author Graeme Rocher
 * @since 3.0.8
 */
@CompileStatic
class SpringEventTranslator implements ApplicationListener,  ApplicationContextAware {

    public static final String GDM_EVENT_PACKAGE = 'org.grails.datastore'
    public static final String EVENT_SUFFIX = "Event"
    public static final String SPRING_PACKAGE = "org.springframework"

    private Map<Class, String> eventClassToName = new ConcurrentHashMap<Class, String>().withDefault { Class eventClass ->
        def clsName = eventClass.name
        def logicalName = GrailsNameUtils.getLogicalPropertyName(clsName, EVENT_SUFFIX)
        if(clsName.startsWith(GDM_EVENT_PACKAGE)) {
            return "gorm:${logicalName}".toString()
        }
        else if(clsName.startsWith(SPRING_PACKAGE)) {
            return "spring:${logicalName}".toString()
        }
        else {
            return "grails:${logicalName}".toString()
        }
    }


    private final EventBus eventBus

    SpringEventTranslator(EventBus eventBus) {
        this.eventBus = eventBus
    }

    void onApplicationEvent(ApplicationEvent event) {
        def eventName = eventClassToName[event.getClass()]

        if(eventBus.isActive()) {
            // don't relay context closed events because Reactor would have been shutdown
            if(!(event instanceof ContextClosedEvent)) {
                eventBus.notify(eventName, event)
            }
        }
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ((GenericApplicationContext)applicationContext).addApplicationListener(this)
    }
}
