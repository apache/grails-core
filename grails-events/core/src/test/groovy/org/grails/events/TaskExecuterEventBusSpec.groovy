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
package org.grails.events

import org.grails.events.bus.ExecutorEventBus
import spock.lang.PendingFeatureIf
import spock.lang.Specification

/**
 * Created by graemerocher on 28/03/2017.
 */
class TaskExecuterEventBusSpec  extends Specification {

    void 'Test task executor event bus single arg'() {

        given: 'a task executor event bus'
            def eventBus = new ExecutorEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar')

        then: 'the result is correct'
            result == 'foo bar'
    }

    void 'Test task executor event bus multiple args'() {

        given: 'a task executor event bus'
            def eventBus = new ExecutorEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            result == 'foo [bar, baz]'
    }

    void 'Test task executor event bus multiple args listener'() {

        given: 'a task executor event bus'
            def eventBus = new ExecutorEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { String one, String two -> result = "foo $one $two" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            result == 'foo bar baz'
    }

    void 'Test task executor event bus send and receive'() {

        given: 'a task executor event bus'
            def eventBus = new ExecutorEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { String data -> "foo $data" }

        and: 'we send and receive the event'
            eventBus.sendAndReceive('test', 'bar') { result = it }

        then: 'the result is correct'
            result == 'foo bar'
    }

    void 'Test task executor bus error handling'() {

        given: 'a task executor event bus'
            def eventBus = new ExecutorEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { String data -> throw new RuntimeException('bad') }

        and: 'we send and receive the event'
            eventBus.sendAndReceive('test', 'bar') { result = it }

        then: 'the result is a throwable'
            result instanceof Throwable
    }

    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void 'Test task executor bus error handling with publish'() {

        given: 'a task executor event bus'
        def eventBus = new ExecutorEventBus()

        when: 'we subscribe to an event'
        eventBus.on('test') { String data -> throw new RuntimeException('bad') }

        and: 'we publish the event'
        eventBus.publish('test', 'bar')

        then: 'an exception is thrown'
        def ex = thrown(RuntimeException)
        ex.message == 'bad'
    }
}

