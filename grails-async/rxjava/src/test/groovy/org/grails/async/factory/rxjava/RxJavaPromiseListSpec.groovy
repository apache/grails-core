package org.grails.async.factory.rxjava

import grails.async.PromiseList
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Created by graemerocher on 29/03/2017.
 */
class RxJavaPromiseListSpec extends Specification{


    void 'Test promise list handling'() {

        when: 'a list of promises is created'
            def list = new PromiseList()
            list << { 1 }
            list << { 2 }
            list << { 3 }
            def result
            list.onComplete { result = it }

        then: 'then the result from onComplete is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result == [1,2,3]
            }
    }

    void 'Test promise list handling with some async operations and some values'() {

        when: 'a list of promises is created'
            def list = new PromiseList()
            list << { 1 }
            list <<  2
            list << { 3 }
            def result
            list.onComplete { result = it }

        then: 'then the result from onComplete is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result == [1,2,3]
            }
    }

    void 'Test promise list with then chaining'() {

        when: 'a promise list is used with then chaining'
            def list = new PromiseList<Integer>()
            list << { 1 }
            def promise = list
                .then { it << 2; it }
                .then {
                    Thread.dumpStack()
                    it << 3; it
                }
            def result = promise.get()

        then: 'an appropriately populated list is produced'
            result == [1,2,3]
    }

    void 'Test promise list with an exception'() {

        given: 'a promise list with a promise that throws an exception'
            def list = new PromiseList()
            list << { 1 }
            list << { throw new RuntimeException('bad') }
            list << { 3 }

        when: 'the list is completed'
            def result
            Throwable error
            list.onComplete { result = it }
            list.onError { error = it }.get()
            list.get()

        then: 'the onError handler is invoked with the exception'
            thrown(RuntimeException)
            error
            error.message == 'bad'
            !result
    }
}

