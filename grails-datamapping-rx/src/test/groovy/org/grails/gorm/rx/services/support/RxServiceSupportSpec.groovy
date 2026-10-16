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

import rx.schedulers.Schedulers
import spock.lang.Specification

import java.util.concurrent.Callable

class RxServiceSupportSpec extends Specification {

    def "create emits the single non-iterable value returned by the callable"() {
        given:
        Callable<String> callable = { 'hello' } as Callable<String>

        when:
        List<String> results = RxServiceSupport.create(Schedulers.trampoline(), callable).toList().toBlocking().single()

        then:
        results == ['hello']
    }

    def "create emits each element of an iterable returned by the callable in order"() {
        given:
        Callable<List<String>> callable = { ['a', 'b', 'c'] } as Callable<List<String>>

        when:
        List<String> results = RxServiceSupport.create(Schedulers.trampoline(), callable).toList().toBlocking().single()

        then:
        results == ['a', 'b', 'c']
    }

    def "create completes without emitting when the callable returns an empty iterable"() {
        given:
        Callable<List<String>> callable = { [] } as Callable<List<String>>

        when:
        List<String> results = RxServiceSupport.create(Schedulers.trampoline(), callable).toList().toBlocking().single()

        then:
        results == []
    }

    def "create completes without emitting when the callable returns null"() {
        given:
        Callable<String> callable = { null } as Callable<String>

        when:
        List<String> results = RxServiceSupport.create(Schedulers.trampoline(), callable).toList().toBlocking().single()

        then:
        results == []
    }

    def "create without an explicit scheduler runs on the IO scheduler and still emits the result"() {
        given:
        Callable<String> callable = { 'hello' } as Callable<String>

        when:
        String result = RxServiceSupport.create(callable).toBlocking().first()

        then:
        result == 'hello'
    }

    def "createSingle emits the value produced by a callable that completes normally"() {
        given:
        Callable<String> callable = { 'hello' } as Callable<String>

        when:
        String result = RxServiceSupport.createSingle(Schedulers.trampoline(), callable).toBlocking().value()

        then:
        result == 'hello'
    }

    def "createSingle propagates the exception thrown by the callable as an error"() {
        given:
        IllegalStateException failure = new IllegalStateException('boom')
        Callable<String> callable = { throw failure } as Callable<String>

        when:
        RxServiceSupport.createSingle(Schedulers.trampoline(), callable).toBlocking().value()

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.is(failure)
    }

    def "createSingle without an explicit scheduler runs on the IO scheduler and still emits the result"() {
        given:
        Callable<String> callable = { 'hello' } as Callable<String>

        when:
        String result = RxServiceSupport.createSingle(callable).toBlocking().value()

        then:
        result == 'hello'
    }
}
