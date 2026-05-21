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

package org.grails.events.gpars

import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ActorEventBusSpec extends Specification {

    void 'Test actor event bus with single arg'() {

        given: 'an actor event bus'
            def eventBus = new ActorEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar')

        then: 'the result is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result == 'foo bar'
            }
    }

    void 'Test actor event bus with multiple args'() {

        given: 'an actor event bus'
            def eventBus = new ActorEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
        eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result == 'foo [bar, baz]'
            }
    }

    void 'Test actor event bus with multiple args listener'() {

        given: 'an actor event bus'
            def eventBus = new ActorEventBus()

        when: 'we subscribe to an event with a multiple args listener'
            def result = null
            eventBus.on('test') { String one, String two -> result = "foo $one $two" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result == 'foo bar baz'
            }
    }

    void 'Test actor event bus send and receive'() {

        given: 'an actor event bus'
            def eventBus = new ActorEventBus()

        when: 'we subscribe to an event'
            eventBus.on('test') { String data -> "foo $data" }

        and: 'we send and receive the event'
        def result = null
        eventBus.sendAndReceive('test', 'bar') { result = it }

        then: 'the result is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result == 'foo bar'
            }
    }

    void 'Test actor event bus error handling'() {

        given: 'an actor event bus'
            def eventBus = new ActorEventBus()

        when: 'we subscribe to an event'
            eventBus.on('test') { String data ->
                throw new RuntimeException('bad')
            }

        and: 'we send and receive the event'
            def result = null
            eventBus.sendAndReceive('test', 'bar') { result = it }

        then: 'the result is a throwable'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result instanceof Throwable
            }
    }

}