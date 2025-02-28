package org.grails.events

import org.grails.events.bus.ExecutorEventBus
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

