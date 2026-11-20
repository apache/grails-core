/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.plugins.events

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.events.bus.EventBus
import grails.plugins.Plugin
import org.grails.events.bus.spring.EventBusFactoryBean
import org.grails.events.gorm.GormDispatcherRegistrar
import org.grails.events.spring.SpringEventTranslator

/**
 * A plugin that integrates Reactor into Grails
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class EventBusGrailsPlugin extends Plugin {

    def grailsVersion = '8.0.0-SNAPSHOT > *'

    /**
     * Whether to translate GORM events into reactor events
     */
    public static final String TRANSLATE_SPRING_EVENTS = 'grails.events.spring'

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('grailsEventBus', EventBusFactoryBean)
            registry.registerBean('gormDispatchEventRegistrar', GormDispatcherRegistrar) {
                it.supplier {
                    new GormDispatcherRegistrar(it.bean('grailsEventBus', EventBus))
                }
            }

            // make it possible to enable reactor events
            if (environment.getProperty(TRANSLATE_SPRING_EVENTS, Boolean, false)) {
                registry.registerBean('springEventTranslator', SpringEventTranslator) {
                    it.supplier {
                        new SpringEventTranslator(it.bean('grailsEventBus', EventBus))
                    }
                }
            }
        }
    }
}
