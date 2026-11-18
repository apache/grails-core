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
package org.grails.events.groovy

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import grails.events.bus.EventBus
import grails.events.bus.EventBusBuilder
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ActorEventBusSpec extends Specification {

    private final PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.2)
    private final List<ActorEventBus> buses = []

    void cleanup() {
        buses*.close()
    }

    private ActorEventBus bus(ActorEventBus eventBus = new ActorEventBus()) {
        buses << eventBus
        return eventBus
    }

    void 'the event bus builder discovers the actor event bus'() {
        when:
        EventBus eventBus = new EventBusBuilder().build()

        then:
        eventBus instanceof ActorEventBus

        cleanup:
        ((ActorEventBus) eventBus).close()
    }

    void 'Test actor event bus with single arg'() {
        given: 'an actor event bus'
        def eventBus = bus()

        when: 'we subscribe to an event'
        def result = null
        eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
        eventBus.notify('test', 'bar')

        then: 'the result is correct'
        conditions.eventually {
            assert result == 'foo bar'
        }
    }

    void 'Test actor event bus with multiple args'() {
        given: 'an actor event bus'
        def eventBus = bus()

        when: 'we subscribe to an event'
        def result = null
        eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
        eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
        conditions.eventually {
            assert result == 'foo [bar, baz]'
        }
    }

    void 'Test actor event bus with multiple args listener'() {
        given: 'an actor event bus'
        def eventBus = bus()

        when: 'we subscribe to an event with a multiple args listener'
        def result = null
        eventBus.on('test') { String one, String two -> result = "foo $one $two" }

        and: 'we notify the event'
        eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
        conditions.eventually {
            assert result == 'foo bar baz'
        }
    }

    void 'Test actor event bus send and receive'() {
        given: 'an actor event bus'
        def eventBus = bus()

        when: 'we subscribe to an event'
        eventBus.on('test') { String data -> "foo $data" }

        and: 'we send and receive the event'
        def result = null
        eventBus.sendAndReceive('test', 'bar') { result = it }

        then: 'the result is correct'
        conditions.eventually {
            assert result == 'foo bar'
        }
    }

    void 'Test actor event bus error handling'() {
        given: 'an actor event bus'
        def eventBus = bus()

        when: 'we subscribe to an event'
        eventBus.on('test') { String data ->
            throw new RuntimeException('bad')
        }

        and: 'we send and receive the event'
        def result = null
        eventBus.sendAndReceive('test', 'bar') { result = it }

        then: 'the result is a throwable'
        conditions.eventually {
            assert result instanceof Throwable
            assert result.message == 'bad'
        }
    }

    void 'a failing subscriber does not stop later deliveries'() {
        given: 'a bus with a failing subscriber and a working one'
        def eventBus = bus()
        def delivered = new LinkedBlockingQueue<String>()
        eventBus.on('test') { String data -> throw new IllegalStateException('bad') }
        eventBus.on('test') { String data -> delivered.add(data) }

        when: 'two events are published'
        eventBus.notify('test', 'first')
        eventBus.notify('test', 'second')

        then: 'the working subscriber receives both, in order'
        delivered.poll(5, TimeUnit.SECONDS) == 'first'
        delivered.poll(5, TimeUnit.SECONDS) == 'second'
        eventBus.active
    }

    void 'events are delivered in publication order off the publishing thread'() {
        given:
        def eventBus = bus()
        def delivered = new LinkedBlockingQueue<Object>()
        eventBus.on('test') { Integer value -> delivered.add([value, Thread.currentThread()]) }

        when:
        (1..20).each { eventBus.notify('test', it) }
        def received = (1..20).collect { delivered.poll(5, TimeUnit.SECONDS) }

        then:
        received*.get(0) == (1..20).toList()
        received.every { it[1] != Thread.currentThread() }
    }

    void 'a supplied executor runs the actor'() {
        given:
        ExecutorService executor = Executors.newSingleThreadExecutor { Runnable task -> new Thread(task, 'event-bus-actor') }
        def eventBus = bus(new ActorEventBus(executor))
        def delivered = new LinkedBlockingQueue<String>()
        eventBus.on('test') { delivered.add(Thread.currentThread().name) }

        when:
        eventBus.notify('test', 'bar')

        then:
        delivered.poll(5, TimeUnit.SECONDS) == 'event-bus-actor'

        cleanup:
        eventBus.close()
        executor.shutdownNow()
    }

    void 'closing the bus stops the actor'() {
        given:
        def eventBus = new ActorEventBus()
        eventBus.on('test') { }

        expect:
        eventBus.active

        when:
        eventBus.close()
        eventBus.close()

        then:
        !eventBus.active

        when: 'an event is published to the closed bus'
        eventBus.notify('test', 'bar')

        then:
        thrown(IllegalStateException)
    }
}
