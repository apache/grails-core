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