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
