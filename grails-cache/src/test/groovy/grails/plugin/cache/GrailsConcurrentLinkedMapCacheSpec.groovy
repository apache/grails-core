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
package grails.plugin.cache

import java.util.concurrent.Callable

import spock.lang.Specification

class GrailsConcurrentLinkedMapCacheSpec extends Specification {

    void 'the two argument constructor allows null values'() {
        when:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('small', 100)

        then:
        cache.name == 'small'
        cache.capacity == 100
        cache.allowNullValues
        cache.size == 0
        cache.nativeCache.is(cache.@store)
    }

    void 'the three argument constructor can refuse null values'() {
        expect:
        !new GrailsConcurrentLinkedMapCache('strict', 10, false).allowNullValues
    }

    void 'a value put is read back and an absent key reads as null'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10)

        when:
        cache.put('k', 'v')

        then:
        cache.get('k').get() == 'v'
        cache.get('missing') == null
        cache.size == 1
        cache.allKeys == ['k'] as Set
    }

    void 'a null value round trips when null values are allowed'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10, true)

        when:
        cache.put('k', null)

        then:
        cache.get('k') != null
        cache.get('k').get() == null
        cache.nativeCache.get('k') != null
    }

    void 'a null value is stored as is when null values are not allowed'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10, false)

        when:
        cache.put('k', null)

        then:
        thrown(NullPointerException)
    }

    void 'get by type returns a value of that type, tolerates a null type and rejects another type'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10)
        cache.put('k', 'v')

        expect:
        cache.get('k', String) == 'v'
        cache.get('k', (Class) null) == 'v'
        cache.get('missing', String) == null

        when:
        cache.get('k', Integer)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cached value is not of required type [java.lang.Integer]: v'
    }

    void 'get with a value loader is not supported'() {
        when:
        new GrailsConcurrentLinkedMapCache('c', 10).get('k', { 'v' } as Callable)

        then:
        thrown(UnsupportedOperationException)
    }

    void 'putIfAbsent keeps the existing value and reports it'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10)

        expect:
        cache.putIfAbsent('k', 'first') == null

        when:
        def existing = cache.putIfAbsent('k', 'second')

        then:
        existing.get() == 'first'
        cache.get('k').get() == 'first'
    }

    void 'putIfAbsent reports an existing null value as a wrapper holding null'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10, true)
        cache.put('k', null)

        expect:
        cache.putIfAbsent('k', 'x').get() == null
    }

    void 'the hottest keys are the most recently used first'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10)
        (0..4).each { cache.put(it, it) }
        cache.get(1)
        cache.get(2)

        expect:
        cache.hottestKeys.toList().take(2) == [2, 1]
    }

    void 'evict removes one key and clear removes all'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10)
        cache.put('a', 1)
        cache.put('b', 2)

        when:
        cache.evict('a')

        then:
        cache.size == 1
        cache.get('a') == null

        when:
        cache.clear()

        then:
        cache.size == 0
    }

    void 'the least recently used entries are evicted beyond the capacity'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 5)

        when:
        (0..20).each { cache.put(it, it) }

        then:
        cache.size == 5
    }

}
