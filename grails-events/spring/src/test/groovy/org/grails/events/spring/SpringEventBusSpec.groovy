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

import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

/**
 * Created by graemerocher on 27/03/2017.
 */
class SpringEventBusSpec extends Specification {

    void 'Test spring event bus with single arg'() {

        given: 'a spring event bus'
            def context = new GenericApplicationContext()
            context.refresh()
            def eventBus = new SpringEventBus(context)

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar')

        then: 'the result is correct'
            result == 'foo bar'
    }

    void 'Test spring event bus multiple args'() {

        given: 'a spring event bus'
            def context = new GenericApplicationContext()
            context.refresh()
            def eventBus = new SpringEventBus(context)

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            result == 'foo [bar, baz]'
    }

    void 'Test spring event bus multiple args listener'() {

        given: 'a spring event bus'
            def context = new GenericApplicationContext()
            context.refresh()
            def eventBus = new SpringEventBus(context)

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { String one, String two -> result = "foo $one $two" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            result == 'foo bar baz'
    }

    void 'Test spring event bus error handling'() {

        given: 'a spring event bus'
            def context = new GenericApplicationContext()
            context.refresh()
            def eventBus = new SpringEventBus(context)

        when: 'we subscribe to an event with a closure that throws an exception'
            eventBus.on('test') { String data -> throw new RuntimeException('bad') }

        and: 'we send and receive the event'
            def result = null
            eventBus.sendAndReceive('test', 'bar') { result = it }

        then:
            result instanceof Throwable
            result.message == 'bad'
    }
}
