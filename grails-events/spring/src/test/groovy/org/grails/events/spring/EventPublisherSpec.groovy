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
package org.grails.events.spring

import grails.events.EventPublisher
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Component
import spock.lang.Specification

/**
 * Created by graemerocher on 27/03/2017.
 */
class EventPublisherSpec extends Specification {

    def 'Test event publisher within Spring'() {

        given: 'a Spring application context with an event bus'
            AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()
            def bus = new SpringEventBus(applicationContext)
            applicationContext.beanFactory.registerSingleton("eventBus", bus)

        and: 'we register a publisher in the application context'
            applicationContext.register(MyPublisher)
            applicationContext.refresh()
            MyPublisher publisher = applicationContext.getBean(MyPublisher)

        and: 'we subscribe to an event'
            def result = null
            bus.on('test') { result = "good $it" }

        when: 'we publish an event'
            publisher.publish('test', 'data')

        then: 'the result is correct'
            result == "good data"
    }
}

@Component
class MyPublisher implements EventPublisher {
}
