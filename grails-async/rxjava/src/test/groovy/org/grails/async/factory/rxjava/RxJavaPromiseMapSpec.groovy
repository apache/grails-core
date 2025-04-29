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
package org.grails.async.factory.rxjava

import grails.async.PromiseMap
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Created by graemerocher on 29/03/2017.
 */
class RxJavaPromiseMapSpec extends Specification{

    void 'Test PromiseMap with mixture of normal entries and promises populated via constructor'() {

        when: 'a promise map is used with an onComplete handler'
            def map = new PromiseMap(one: { 1 }, four: 4, eight: { 4 * 2 })
            Map result = null
            map.onComplete { result = it }

        then: 'an appropriately populated map is returned to the onComplete event'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result
                assert result['one'] == 1
                assert result['four'] == 4
                assert result['eight'] == 8
            }
    }

    void 'Test PromiseMap with mixture of normal entries and promises'() {
        
        when: 'a promise map is used with an onComplete handler'
            def map = new PromiseMap()
            map['one'] = { 1 }
            map['four'] = 4
            map['eight'] = { 4 * 2 }
            Map result = null
            map.onComplete { result = it }

        then: 'an appropriately populated map is returned to the onComplete event'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result
                assert result['one'] == 1
                assert result['four'] == 4
                assert result['eight'] == 8
            }
    }

    void 'Test that a PromiseMap populates values from promises onComplete'() {

        when: 'a promise map is used with an onComplete handler'
            def map = new PromiseMap()
            map['one'] = { 1 }
            map['four'] = { 2 + 2 }
            map['eight'] = { 4 * 2 }
            Map result = null
            map.onComplete { result = it }

        then: 'an appropriately populated map is returned to the onComplete event'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert result
                assert result['one'] == 1
                assert result['four'] == 4
                assert result['eight'] == 8
            }
    }


    void 'Test that a PromiseMap triggers onError for an exception and ignores onComplete'() {
        
        when: 'a promise map is used with an onComplete handler'
            def map = new PromiseMap<String, Integer>()
            map['one'] = { 1 }
            map['four'] = { throw new RuntimeException('bad') }
            map['eight'] = { 4 * 2 }

        Map result = null
        Throwable error = null
        map.onComplete { result = it }
        map.onError { error = it }

        then: 'an appropriately populated map is returned to the onComplete event'
            new PollingConditions(timeout: 5, delay: 0.2).eventually {
                assert !result
                assert error
                assert error.message == 'bad'
            }
    }

    @PendingFeature(reason = '''
        This test fails because the chained call to then does not use the
        map returned from the previous closure. So the same first map
        is returned over and over.
    ''')
    void 'Test PromiseMap with then chaining'() {

        when: 'a promise map is used with then chaining'
            def map = new PromiseMap()
            map['one'] = { 1 }
            def promise = map.then {
                it['four'] = 4; it
            }.then {
                it['eight'] = 8; it
            }
            def result = promise.get()

        then:'An appropriately populated map is returned to the onComplete event'
            result != null
            result['one'] == 1
            result['four'] == 4
            result['eight'] == 8

    }
}
