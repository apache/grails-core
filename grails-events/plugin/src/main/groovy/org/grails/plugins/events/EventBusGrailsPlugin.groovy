/*
 * Copyright 2014 original authors
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

package org.grails.plugins.events

import grails.config.Config
import grails.plugins.Plugin
import org.grails.events.bus.spring.EventBusFactoryBean
import org.grails.events.gorm.GormDispatcherRegistrar
import org.grails.events.spring.SpringEventTranslator
import reactor.bus.EventBus

/**
 * A plugin that integrates Reactor into Grails
 *
 * @author Graeme Rocher
 * @since 3.0
 */
class EventBusGrailsPlugin extends Plugin {

    def grailsVersion = "7.0.0 > *"

    /**
     * Whether to translate GORM events into reactor events
     */
    public static final String TRANSLATE_SPRING_EVENTS = "grails.events.spring"

    @Override
    Closure doWithSpring() {
        {->
            Config config = grailsApplication.config
            grailsEventBus(EventBusFactoryBean)
            gormDispatchEventRegistrar(GormDispatcherRegistrar, ref("grailsEventBus"))

            // the legacy reactor EventBus, here for backwards compatibility
            eventBus(EventBus, ref('grailsEventBus'))


            // make it possible to enable reactor events
            if(config.getProperty(TRANSLATE_SPRING_EVENTS, Boolean.class, false)) {
                springEventTranslator(SpringEventTranslator, ref('grailsEventBus'))
            }
        }
    }
}
