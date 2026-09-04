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

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

/**
 * Covers the publisher being built by the container rather than handed a context, which is what
 * lets a datastore reference it as a bean definition instead of an already-constructed object.
 */
class ConfigurableApplicationContextEventPublisherSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        if (context.active) {
            context.close()
        }
    }

    void 'the container supplies the context to a publisher built without one'() {
        given:
            context.registerBean('grailsDatastoreEventPublisher', ConfigurableApplicationContextEventPublisher)

        when:
            context.refresh()
            def publisher = context.getBean('grailsDatastoreEventPublisher',
                    ConfigurableApplicationContextEventPublisher)

        then: 'nothing passed the context in, so only the container callback can have set it'
            publisher.applicationContext.is(context)
    }

    void 'a publisher built that way still delivers events and listeners'() {
        given:
            context.registerBean('grailsDatastoreEventPublisher', ConfigurableApplicationContextEventPublisher)
            context.refresh()
            def publisher = context.getBean('grailsDatastoreEventPublisher',
                    ConfigurableApplicationContextEventPublisher)
            def received = []

        when:
            publisher.addApplicationListener({ event -> received << event } as ApplicationListener)
            def event = new ContextRefreshedEvent(context)
            publisher.publishEvent(event)

        then:
            received == [event]
    }

    void 'the constructor taking a context keeps working'() {
        given: 'the form callers outside the container still use'
            context.refresh()

        when:
            def publisher = new ConfigurableApplicationContextEventPublisher(context)

        then:
            publisher.applicationContext.is(context)
    }

    void 'a context that cannot be configured is declined rather than thrown out of the callback'() {
        given: 'setApplicationContext is the container calling in, so what it throws comes out of ' +
                'the container rather than out of anything this was asked to do'
            def publisher = new ConfigurableApplicationContextEventPublisher()

        when:
            publisher.setApplicationContext(Mock(ApplicationContext))

        then:
            noExceptionThrown()

        and: 'and nothing was taken from it'
            publisher.applicationContext == null
    }

    void 'a publisher that was never given a context says so rather than failing as a null'() {
        given: 'which is what happens where the bean was registered in a plain bean factory: the ' +
                'callback that completes it is applied by a context, so it never runs'
            def publisher = new ConfigurableApplicationContextEventPublisher()

        when:
            publisher.publishEvent(new ContextRefreshedEvent(context))

        then: 'rather than a NullPointerException from inside GORM saying nothing about why'
            IllegalStateException e = thrown()
            e.message.contains('ConfigurableApplicationContext')

        when:
            publisher.addApplicationListener({ event -> } as ApplicationListener)

        then:
            thrown(IllegalStateException)
    }
}
