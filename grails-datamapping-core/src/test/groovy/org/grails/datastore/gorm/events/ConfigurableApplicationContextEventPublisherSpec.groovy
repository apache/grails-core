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

package org.grails.datastore.gorm.events

import spock.lang.Specification

import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.PayloadApplicationEvent

class ConfigurableApplicationContextEventPublisherSpec extends Specification {

    ConfigurableApplicationContext applicationContext = Mock(ConfigurableApplicationContext)
    ConfigurableApplicationContextEventPublisher publisher = new ConfigurableApplicationContextEventPublisher(applicationContext)

    void "exposes the ApplicationContext it was constructed with"() {
        expect:
        publisher.applicationContext.is(applicationContext)
    }

    void "addApplicationListener delegates to the wrapped ApplicationContext"() {
        given:
        ApplicationListener listener = Mock(ApplicationListener)

        when:
        publisher.addApplicationListener(listener)

        then:
        1 * applicationContext.addApplicationListener(listener)
    }

    void "publishEvent(ApplicationEvent) delegates to the wrapped ApplicationContext"() {
        given:
        ApplicationEvent event = new PayloadApplicationEvent<>(this, 'payload')

        when:
        publisher.publishEvent(event)

        then:
        1 * applicationContext.publishEvent(event)
    }

    void "publishEvent(Object) delegates to the wrapped ApplicationContext"() {
        given:
        Object payload = 'a plain payload'

        when:
        publisher.publishEvent(payload)

        then:
        1 * applicationContext.publishEvent(payload)
    }
}
