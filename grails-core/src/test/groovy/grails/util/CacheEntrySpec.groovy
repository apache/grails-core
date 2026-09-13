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
package grails.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import spock.lang.Specification

class CacheEntrySpec extends Specification {

    void 'a fresh entry constructed with a value is initialized and not expired'() {
        given:
        CacheEntry<String> entry = new CacheEntry<>('initial')

        expect:
        entry.initialized
        entry.value == 'initial'
    }

    void 'a fresh no-arg entry starts expired'() {
        given:
        CacheEntry<String> entry = new CacheEntry<>()

        expect:
        !entry.initialized
        entry.createdMillis == 0L
    }

    void 'expire resets the created timestamp to zero'() {
        given:
        CacheEntry<String> entry = new CacheEntry<>('value')

        when:
        entry.expire()

        then:
        entry.createdMillis == 0L
    }

    void 'setValue marks the entry initialized and refreshes the timestamp'() {
        given:
        CacheEntry<String> entry = new CacheEntry<>()

        when:
        entry.setValue('value')

        then:
        entry.initialized
        entry.value == 'value'
        entry.createdMillis > 0L
    }

    void 'getValue with a negative timeout never expires and never calls the updater'() {
        given:
        CacheEntry<String> entry = new CacheEntry<>('cached')

        when:
        String result = entry.getValue(-1, { 'updated' })

        then:
        result == 'cached'
    }

    void 'getValue on an uninitialized entry invokes the updater and caches the result'() {
        given:
        CacheEntry<String> entry = new CacheEntry<>()

        when:
        String result = entry.getValue(10000, { 'computed' })

        then:
        result == 'computed'
        entry.value == 'computed'
        entry.initialized
    }

    void 'the static map-based getValue creates and caches an entry per key'() {
        given:
        ConcurrentMap<String, CacheEntry<String>> map = new ConcurrentHashMap<>()

        when:
        String first = CacheEntry.getValue(map, 'key', 10000, { 'computed' })
        String second = CacheEntry.getValue(map, 'key', 10000, { 'should not be called' })

        then:
        first == 'computed'
        second == 'computed'
        map.containsKey('key')
    }

    void 'the static map-based getValue re-invokes the updater once the entry has expired'() {
        given:
        ConcurrentMap<String, CacheEntry<String>> map = new ConcurrentHashMap<>()
        CacheEntry.getValue(map, 'key', 0, { 'first' })
        Thread.sleep(5)

        when:
        String result = CacheEntry.getValue(map, 'key', 0, { 'second' })

        then:
        result == 'second'
    }

    void 'UpdateException rethrows a RuntimeException cause as itself'() {
        given:
        RuntimeException cause = new IllegalStateException('boom')
        CacheEntry.UpdateException updateException = new CacheEntry.UpdateException(cause)

        when:
        updateException.rethrowRuntimeException()

        then:
        IllegalStateException thrown = thrown(IllegalStateException)
        thrown.is(cause)
    }

    void 'UpdateException rethrows itself when the cause is not a RuntimeException'() {
        given:
        CacheEntry.UpdateException updateException = new CacheEntry.UpdateException(new java.io.IOException('boom'))

        when:
        updateException.rethrowRuntimeException()

        then:
        thrown(CacheEntry.UpdateException)
    }
}
