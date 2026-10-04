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
import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.event.SmartApplicationListener

class DefaultApplicationEventPublisherSpec extends Specification {

    DefaultApplicationEventPublisher publisher = new DefaultApplicationEventPublisher()

    void "publishEvent(ApplicationEvent) does nothing when no listeners are registered"() {
        given:
        ApplicationEvent event = new PayloadApplicationEvent<>(this, 'payload')

        when:
        publisher.publishEvent(event)

        then:
        noExceptionThrown()
    }

    void "publishEvent(Object) does nothing when no listeners are registered"() {
        when:
        publisher.publishEvent('a plain payload')

        then:
        noExceptionThrown()
    }

    void "publishEvent notifies a plain ApplicationListener regardless of event or source type"() {
        given:
        ApplicationListener listener = Mock(ApplicationListener)
        publisher.addApplicationListener(listener)
        ApplicationEvent event = new PayloadApplicationEvent<>(this, 'payload')

        when:
        publisher.publishEvent(event)

        then:
        1 * listener.onApplicationEvent(event)
    }

    void "publishEvent notifies every registered listener in order"() {
        given:
        ApplicationListener first = Mock(ApplicationListener)
        ApplicationListener second = Mock(ApplicationListener)
        publisher.addApplicationListener(first)
        publisher.addApplicationListener(second)
        ApplicationEvent event = new PayloadApplicationEvent<>(this, 'payload')

        when:
        publisher.publishEvent(event)

        then:
        1 * first.onApplicationEvent(event)
        1 * second.onApplicationEvent(event)
    }

    void "publishEvent notifies a SmartApplicationListener when it supports both the event type and source type"() {
        given:
        SmartApplicationListener listener = Mock(SmartApplicationListener) {
            supportsEventType(_) >> true
            supportsSourceType(_) >> true
        }
        publisher.addApplicationListener(listener)
        ApplicationEvent event = new PayloadApplicationEvent<>(this, 'payload')

        when:
        publisher.publishEvent(event)

        then:
        1 * listener.onApplicationEvent(event)
    }

    void "publishEvent skips a SmartApplicationListener that does not support the event type"() {
        given:
        SmartApplicationListener listener = Mock(SmartApplicationListener) {
            supportsEventType(_) >> false
        }
        publisher.addApplicationListener(listener)
        ApplicationEvent event = new PayloadApplicationEvent<>(this, 'payload')

        when:
        publisher.publishEvent(event)

        then:
        0 * listener.onApplicationEvent(_)
    }

    void "publishEvent skips a SmartApplicationListener that supports the event type but not the source type"() {
        given:
        SmartApplicationListener listener = Mock(SmartApplicationListener) {
            supportsEventType(_) >> true
            supportsSourceType(_) >> false
        }
        publisher.addApplicationListener(listener)
        ApplicationEvent event = new PayloadApplicationEvent<>(this, 'payload')

        when:
        publisher.publishEvent(event)

        then:
        0 * listener.onApplicationEvent(_)
    }

    void "publishEvent(Object) wraps the payload in a PayloadApplicationEvent sourced from the publisher itself"() {
        given:
        ApplicationListener listener = Mock(ApplicationListener)
        publisher.addApplicationListener(listener)

        when:
        publisher.publishEvent('a plain payload')

        then:
        1 * listener.onApplicationEvent({ ApplicationEvent e ->
            e instanceof PayloadApplicationEvent &&
                    e.source.is(publisher) &&
                    e.payload == 'a plain payload'
        })
    }

    void "publishEvent(Object) applies the same SmartApplicationListener filtering as publishEvent(ApplicationEvent)"() {
        given:
        SmartApplicationListener supported = Mock(SmartApplicationListener) {
            supportsEventType(_) >> true
            supportsSourceType(_) >> true
        }
        SmartApplicationListener unsupported = Mock(SmartApplicationListener) {
            supportsEventType(_) >> false
        }
        publisher.addApplicationListener(supported)
        publisher.addApplicationListener(unsupported)

        when:
        publisher.publishEvent('a plain payload')

        then:
        1 * supported.onApplicationEvent(_)
        0 * unsupported.onApplicationEvent(_)
    }
}
