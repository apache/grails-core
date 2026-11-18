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

package org.grails.gorm.rx.services.support

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RxServiceSupportSpec extends Specification {

    def "create emits the single non-iterable value returned by the callable"() {
        given:
        Callable<String> callable = { 'hello' } as Callable<String>

        when:
        List<String> results = RxServiceSupport.create(Schedulers.trampoline(), callable).toList().blockingGet()

        then:
        results == ['hello']
    }

    def "create emits each element of an iterable returned by the callable in order"() {
        given:
        Callable<List<String>> callable = { ['a', 'b', 'c'] } as Callable<List<String>>

        when:
        List<String> results = RxServiceSupport.create(Schedulers.trampoline(), callable).toList().blockingGet()

        then:
        results == ['a', 'b', 'c']
    }

    def "create completes without emitting when the callable returns an empty iterable"() {
        given:
        Callable<List<String>> callable = { [] } as Callable<List<String>>

        when:
        List<String> results = RxServiceSupport.create(Schedulers.trampoline(), callable).toList().blockingGet()

        then:
        results == []
    }

    def "create completes without emitting when the callable returns null"() {
        given:
        Callable<String> callable = { null } as Callable<String>

        when:
        List<String> results = RxServiceSupport.create(Schedulers.trampoline(), callable).toList().blockingGet()

        then:
        results == []
    }

    def "create without an explicit scheduler delivers the result on the IO scheduler"() {
        given:
        Callable<String> callable = { 'hello' } as Callable<String>

        when:
        String result = RxServiceSupport.create(callable).blockingFirst()

        then:
        result == 'hello'
    }

    def "createSingle emits the value produced by a callable that completes normally"() {
        given:
        Callable<String> callable = { 'hello' } as Callable<String>

        when:
        String result = RxServiceSupport.createSingle(Schedulers.trampoline(), callable).blockingGet()

        then:
        result == 'hello'
    }

    def "createSingle propagates the exception thrown by the callable as an error"() {
        given:
        IllegalStateException failure = new IllegalStateException('boom')
        Callable<String> callable = { throw failure } as Callable<String>

        when:
        RxServiceSupport.createSingle(Schedulers.trampoline(), callable).blockingGet()

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.is(failure)
    }

    def "createSingle signals NoSuchElementException when the callable returns null"() {
        given: 'an RxJava 3 Single cannot complete without a value'
        Callable<String> callable = { null } as Callable<String>

        when:
        RxServiceSupport.createSingle(Schedulers.trampoline(), callable).blockingGet()

        then:
        NoSuchElementException e = thrown(NoSuchElementException)
        e.message == 'The operation produced no result'
    }

    def "create invokes the callable on the subscribing thread and delivers the result on the scheduler"() {
        given:
        ExecutorService executor = Executors.newSingleThreadExecutor({ Runnable r -> new Thread(r, 'rx-delivery') })
        Scheduler scheduler = Schedulers.from(executor)
        String callableThread = null
        Callable<String> callable = { callableThread = Thread.currentThread().name; 'hello' } as Callable<String>

        when:
        String deliveryThread = RxServiceSupport.create(scheduler, callable)
                .map { Thread.currentThread().name }
                .blockingFirst()

        then: 'the blocking call stays on the subscriber, only the emission moves'
        callableThread == Thread.currentThread().name
        deliveryThread == 'rx-delivery'

        cleanup:
        executor.shutdownNow()
    }

    def "createSingle invokes the callable on the subscribing thread and delivers the result on the scheduler"() {
        given:
        ExecutorService executor = Executors.newSingleThreadExecutor({ Runnable r -> new Thread(r, 'rx-single-delivery') })
        Scheduler scheduler = Schedulers.from(executor)
        String callableThread = null
        Callable<String> callable = { callableThread = Thread.currentThread().name; 'hello' } as Callable<String>

        when:
        String deliveryThread = RxServiceSupport.createSingle(scheduler, callable)
                .map { Thread.currentThread().name }
                .blockingGet()

        then: 'the blocking call stays on the subscriber, only the emission moves'
        callableThread == Thread.currentThread().name
        deliveryThread == 'rx-single-delivery'

        cleanup:
        executor.shutdownNow()
    }

    def "createSingle without an explicit scheduler delivers the result on the IO scheduler"() {
        given:
        Callable<String> callable = { 'hello' } as Callable<String>

        when:
        String result = RxServiceSupport.createSingle(callable).blockingGet()

        then:
        result == 'hello'
    }
}
