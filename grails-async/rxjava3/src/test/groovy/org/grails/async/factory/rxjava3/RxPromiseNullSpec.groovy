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

import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import grails.async.Promise
import grails.async.PromiseList
import grails.async.Promises

/**
 * RxJava 3 cannot carry {@code null} through a stream, so the factory carries a sentinel in its place and restores
 * {@code null} wherever a value leaves the stream, as every other promise factory hands {@code null} back.
 */
class RxPromiseNullSpec extends Specification {

    void 'an unbound promise resolves to null'() {
        expect: 'both unbound forms, which Promises.createPromise() and WebPromises.createPromise() call, hold null'
            Promises.createPromise().get() == null
            Promises.createPromise(String).get() == null
    }

    void 'a task that returns null resolves to null'() {
        when: 'a task returns null'
            Promise<Object> promise = Promises.createPromise { null }

        then: 'the promise resolves to null, with and without a timeout'
            promise.get() == null
            promise.get(5, TimeUnit.SECONDS) == null
    }

    void 'null passes through onComplete and a chained task can return null'() {
        given: 'callbacks that record what they receive'
            List received = []

        when: 'a null result is chained through a callback that returns a value and one that returns null'
            Promise<Object> promise = Promises.createPromise { null }
                    .then { received << it; 'value' }
                    .then { received << it; null }

        then: 'each callback received the previous result and the chain resolves to null'
            promise.get() == null
            received == [null, 'value']
    }

    void 'onComplete over a list keeps a null value in its position'() {
        given: 'promises that hold null, bound and computed, next to one that does not'
            List<Promise<Object>> promises = [
                    Promises.createBoundPromise(null),
                    Promises.createPromise { null },
                    Promises.createPromise { 1 }
            ]
            List received = null

        when: 'the list completes'
            Promises.onComplete(promises) { received = it }

        then: 'the callback receives every value in order, null included'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert received == [null, null, 1]
            }
    }

    void 'waitAll and a promise list keep a null value in its position'() {
        given: 'a promise list with a task that returns null'
            PromiseList<Object> list = new PromiseList<>()
            list << { 1 }
            list << { null }

        expect: 'the null is where its task is'
            Promises.waitAll(Promises.createPromise { null }, Promises.createPromise { 2 }) == [null, 2]
            list.get() == [1, null]
    }

    void 'onError over a list resolves to the values, null included, when nothing fails'() {
        expect:
            Promises.onError([Promises.createPromise { null }, Promises.createPromise { 2 }]) { }.get() == [null, 2]
    }
}
