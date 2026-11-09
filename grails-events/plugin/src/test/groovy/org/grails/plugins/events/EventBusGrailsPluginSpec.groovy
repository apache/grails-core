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

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.events.bus.EventBus
import org.grails.events.bus.spring.EventBusFactoryBean
import org.grails.events.gorm.GormDispatcherRegistrar

import spock.lang.Specification

class EventBusGrailsPluginSpec extends Specification {

    void "beanRegistrar registers the event bus beans"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()

        when:
        applyRegistrar(beanFactory, new StandardEnvironment())

        then:
        beanFactory.getBeanDefinition('grailsEventBus').beanClassName == EventBusFactoryBean.name
        beanFactory.getBeanDefinition('gormDispatchEventRegistrar').beanClassName == GormDispatcherRegistrar.name

        and: 'spring event translation is off by default'
        !beanFactory.containsBeanDefinition('springEventTranslator')
    }

    void "beanRegistrar registers the spring event translator when enabled"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(
                new MapPropertySource('test', [(EventBusGrailsPlugin.TRANSLATE_SPRING_EVENTS): 'true']))

        when:
        applyRegistrar(beanFactory, environment)

        then:
        beanFactory.containsBeanDefinition('springEventTranslator')
    }

    void "the gorm dispatcher registrar is created with the registered event bus"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def eventBus = Mock(EventBus)
        applyRegistrar(beanFactory, new StandardEnvironment())
        beanFactory.registerSingleton('grailsEventBus', eventBus)

        when:
        def dispatcherRegistrar = beanFactory.getBean('gormDispatchEventRegistrar', GormDispatcherRegistrar)

        then:
        dispatcherRegistrar.@eventBus.is(eventBus)
    }

    private static void applyRegistrar(DefaultListableBeanFactory beanFactory, StandardEnvironment environment) {
        def registrar = new EventBusGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, environment, registrar.getClass()).register(registrar)
    }
}
