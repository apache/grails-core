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
package org.grails.async.factory.rxjava3

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import grails.async.Promise
import grails.async.PromiseList
import grails.async.Promises
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class RxPromiseListSpec extends Specification {

    void 'Test promise list handling'() {

        when: 'a list of promises is created'
            def list = new PromiseList()
            list << { 1 }
            list << { 2 }
            list << { 3 }
            def result = null
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
            def result = null
            list.onComplete { result = it }

        then: 'then the result from onComplete is correct'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result == [1,2,3]
            }
    }

    void 'Test promise list with then chaining'() {

        when: 'a promise list is used with then chaining'
            def list = new PromiseList()
            list << { 1 }
            def promise = list
                .then { it << 2; it }
                .then {
                    Thread.dumpStack()
                    it << 3; it
                }
            def result = promise.get()

        then: 'An appropriately populated list is produced'
            result == [1,2,3]

    }

    void 'Test promise list with an exception'() {

        given: 'a promise list with a promise that throws an exception'
            def list = new PromiseList()
            list << { 1 }
            list << { throw new RuntimeException('bad') }
            list << { 3 }

        when: 'the list is completed'
            def result = null
            Throwable error = null
            list.onComplete { result = it }
            list.onError { error = it }.get()
            list.get()

        then: 'the onError handler is invoked with the exception'
            thrown(RuntimeException)
            !result
            error
            error.message == 'bad'
    }

    void 'onComplete over a list runs each task once'() {
        given: 'a task that counts its runs'
            AtomicInteger runs = new AtomicInteger()
            Promise<Integer> task = Promises.createPromise { runs.incrementAndGet() }

        when: 'the list completes'
            List result = Promises.onComplete([task]) { it }.get(5, TimeUnit.SECONDS)

        then: 'the task ran once and the callback received its value'
            task.get() == 1
            result == [1]
            runs.get() == 1
    }

    void 'onError over a list runs each task once'() {
        given: 'a task that counts its runs'
            AtomicInteger runs = new AtomicInteger()
            Promise<Integer> task = Promises.createPromise { runs.incrementAndGet() }

        when: 'the list resolves without an error'
            List result = Promises.onError([task]) { }.get(5, TimeUnit.SECONDS)

        then: 'the task ran once and the list holds its value'
            task.get() == 1
            result == [1]
            runs.get() == 1
    }

    void 'onComplete over a list completes when a promise in it was chained with then'() {
        given: 'a promise chained from a task, with a callback that counts its runs'
            AtomicInteger callbacks = new AtomicInteger()
            Promise<Integer> chained = Promises.createPromise { 1 }.then { callbacks.incrementAndGet(); it + 1 }

        when: 'the list completes'
            List result = Promises.onComplete([chained]) { it }.get(5, TimeUnit.SECONDS)

        then: 'the callback received the chained value and the then callback ran once'
            result == [2]
            callbacks.get() == 1
    }

    void 'onError over a list reports the failure a bound promise holds'() {
        given: 'a bound promise whose then callback failed, after a task'
            def failure = new IllegalStateException('bad')
            Promise<Object> failed = Promises.createBoundPromise(null).then { throw failure }
            Throwable observed = null

        when: 'the list is read'
            Promises.onError([Promises.createPromise { 1 }, failed]) { observed = it }.get(5, TimeUnit.SECONDS)

        then: 'the aggregate throws the failure and the error callback received it'
            IllegalStateException e = thrown()
            e.is(failure)
            observed.is(failure)
    }

    void 'onComplete over a list skips its callback when a bound promise holds a failure'() {
        given: 'a bound promise whose then callback failed, after a task'
            def failure = new IllegalStateException('bad')
            Promise<Object> failed = Promises.createBoundPromise(null).then { throw failure }
            List completed = null

        when: 'the list is read'
            Promises.onComplete([Promises.createPromise { 1 }, failed]) { completed = it }.get(5, TimeUnit.SECONDS)

        then: 'the aggregate throws the failure and the completion callback never ran'
            IllegalStateException e = thrown()
            e.is(failure)
            completed == null
    }
}
