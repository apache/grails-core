package org.grails.events

import org.grails.events.bus.SynchronousEventBus
import spock.lang.Specification

/**
 * Created by graemerocher on 24/03/2017.
 */
class SynchronousEventBusSpec extends Specification {

    void 'Test synchronous event bus single arg'() {
        
        given: 'a synchronous event bus'
            def eventBus = new SynchronousEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar')

        then: 'the result is correct'
            result == 'foo bar'
    }

    void 'Test synchronous event bus multiple args'() {

        given: 'a synchronous event bus'
            def eventBus = new SynchronousEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            result == 'foo [bar, baz]'
    }

    void 'Test synchronous event bus multiple args listener'() {

        given: 'a synchronous event bus'
            def eventBus = new SynchronousEventBus()

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { String one, String two -> result = "foo $one $two" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            result == 'foo bar baz'
    }
}
