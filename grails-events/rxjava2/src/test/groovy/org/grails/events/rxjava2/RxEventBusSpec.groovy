package org.grails.events.rxjava2

import io.reactivex.schedulers.Schedulers
import spock.lang.Specification

/**
 * Created by graemerocher on 27/03/2017.
 */
class RxEventBusSpec extends Specification {

    void 'Test rx event bus with single arg'() {

        given: 'an rx event bus'
            def eventBus = new RxEventBus(Schedulers.trampoline())

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar')

        then: 'the result is correct'
            result == 'foo bar'
    }

    void 'Test rx event bus with multiple args'() {

        given: 'an rx event bus'
            def eventBus = new RxEventBus(Schedulers.trampoline())

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { result = "foo $it" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            result == 'foo [bar, baz]'
    }

    void 'Test rx event bus with a multiple args listener'() {

        given: 'an rx event bus'
            def eventBus = new RxEventBus(Schedulers.trampoline())

        when: 'we subscribe to an event'
            def result = null
            eventBus.on('test') { String one, String two -> result = "foo $one $two" }

        and: 'we notify the event'
            eventBus.notify('test', 'bar', 'baz')

        then: 'the result is correct'
            result == 'foo bar baz'
    }

    void 'Test rx event bus send and receive'() {

        given: 'an rx event bus'
            def eventBus = new RxEventBus(Schedulers.trampoline())

        when: 'we subscribe to an event'
            eventBus.on('test') { String data -> "foo $data" }

        and: 'we send and receive'
            def result = null
            eventBus.sendAndReceive('test', 'bar') { result = it }

        then: 'the result is correct'
            result == 'foo bar'
    }

    void 'Test rx event bus error handling'() {

        given: 'an rx event bus'
            def eventBus = new RxEventBus(Schedulers.trampoline())

        when: 'we subscribe to an event with a closure that throws an exception'
            eventBus.on('test') { String data -> throw new RuntimeException('bad') }

        and: 'we send and receive'
            def result = null
            eventBus.sendAndReceive('test', 'bar') { result = it }

        then: 'the result is correct'
            result instanceof Throwable
            result.message == 'bad'
    }
}
