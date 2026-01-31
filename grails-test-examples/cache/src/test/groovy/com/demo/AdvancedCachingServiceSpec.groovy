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

package com.demo

import grails.plugin.cache.CustomCacheKeyGenerator
import grails.plugin.cache.GrailsConcurrentMapCacheManager
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

/**
 * Unit tests for advanced @Cacheable scenarios.
 *
 * Tests cover:
 * - null value handling
 * - exception handling
 * - collection caching
 * - custom cache key with closures
 */
class AdvancedCachingServiceSpec extends Specification implements ServiceUnitTest<AdvancedCachingService> {

    @Override
    Closure doWithSpring() {{ ->
        grailsCacheManager(GrailsConcurrentMapCacheManager)
        customCacheKeyGenerator(CustomCacheKeyGenerator)
    }}

    def setup() {
        service.resetCounters()
        service.evictNullCache()
        service.evictExceptionCache()
        service.evictListCache()
        service.evictMapCache()
        service.evictAllKeyCache()
    }

    // ========== null value caching tests ==========

    void 'test null values are cached by default'() {
        when: 'calling with null-producing value first time'
        def result1 = service.getDataOrNull('null')

        then: 'method is invoked and returns null'
        result1 == null
        service.nullInvocationCounter == 1

        when: 'calling again with same value'
        def result2 = service.getDataOrNull('null')

        then: 'method is not invoked (null is cached)'
        result2 == null
        service.nullInvocationCounter == 1
    }

    void 'test non-null values are cached normally'() {
        when: 'calling with normal value'
        def result1 = service.getDataOrNull('test')

        then: 'method is invoked'
        result1 == 'Data for test'
        service.nullInvocationCounter == 1

        when: 'calling again with same value'
        def result2 = service.getDataOrNull('test')

        then: 'method is not invoked (cached)'
        result2 == result1
        service.nullInvocationCounter == 1
    }

    // ========== exception handling tests ==========

    void 'test exceptions are not cached'() {
        when: 'calling with error value (throws exception)'
        service.getDataOrThrow('error')

        then: 'exception is thrown and method is invoked'
        thrown(RuntimeException)
        service.exceptionInvocationCounter == 1

        when: 'calling again with error value'
        service.getDataOrThrow('error')

        then: 'exception is thrown again (not cached)'
        thrown(RuntimeException)
        service.exceptionInvocationCounter == 2
    }

    void 'test successful results are cached even after exceptions'() {
        when: 'calling with normal value'
        def result1 = service.getDataOrThrow('normal')

        then: 'method is invoked successfully'
        result1.startsWith('Data for normal:')
        service.exceptionInvocationCounter == 1

        when: 'calling again with same value'
        def result2 = service.getDataOrThrow('normal')

        then: 'method is not invoked (cached)'
        result2 == result1
        service.exceptionInvocationCounter == 1
    }

    void 'test different inputs have separate cache entries even with exceptions'() {
        when: 'calling with normal value first'
        def result1 = service.getDataOrThrow('normal')

        then: 'method is invoked'
        result1.startsWith('Data for normal:')
        service.exceptionInvocationCounter == 1

        when: 'calling with error value (throws exception)'
        service.getDataOrThrow('error')

        then: 'exception is thrown'
        thrown(RuntimeException)
        service.exceptionInvocationCounter == 2

        when: 'calling with normal value again'
        def result2 = service.getDataOrThrow('normal')

        then: 'normal value is still cached'
        result2 == result1
        service.exceptionInvocationCounter == 2
    }

    // ========== collection caching tests ==========

    void 'test List results are cached'() {
        when: 'calling with category first time'
        def result1 = service.getListData('books')

        then: 'method is invoked'
        result1.size() == 3
        result1[0].startsWith('Item 1 for books')
        service.collectionInvocationCounter == 1

        when: 'calling again with same category'
        def result2 = service.getListData('books')

        then: 'method is not invoked (cached)'
        result2 == result1
        service.collectionInvocationCounter == 1
    }

    void 'test different List parameters have separate cache entries'() {
        when: 'calling with different categories'
        def books = service.getListData('books')
        def movies = service.getListData('movies')

        then: 'both methods are invoked'
        books[0].startsWith('Item 1 for books')
        movies[0].startsWith('Item 1 for movies')
        service.collectionInvocationCounter == 2

        when: 'calling both again'
        def books2 = service.getListData('books')
        def movies2 = service.getListData('movies')

        then: 'both are cached'
        books2 == books
        movies2 == movies
        service.collectionInvocationCounter == 2
    }

    void 'test Map results are cached'() {
        when: 'calling with key first time'
        def result1 = service.getMapData('mykey')

        then: 'method is invoked'
        result1.key == 'mykey'
        result1.value == 'Value for mykey'
        result1.nested.a == 1
        service.collectionInvocationCounter == 1

        when: 'calling again with same key'
        def result2 = service.getMapData('mykey')

        then: 'method is not invoked (cached)'
        result2 == result1
        service.collectionInvocationCounter == 1
    }

    void 'test Map results are cached correctly'() {
        when: 'calling with key first time'
        def result1 = service.getMapData('mykey')

        then: 'method is invoked with correct data'
        result1.key == 'mykey'
        result1.value == 'Value for mykey'
        result1.nested.a == 1
        service.collectionInvocationCounter == 1

        when: 'calling again with same key'
        def result2 = service.getMapData('mykey')

        then: 'method is not invoked (cached)'
        result2 == result1
        service.collectionInvocationCounter == 1
    }

    // ========== custom key caching tests ==========

    void 'test custom key closure creates separate cache entries'() {
        when: 'calling with different keys'
        def result1 = service.getDataByKey('key1')
        def result2 = service.getDataByKey('key2')

        then: 'both methods are invoked'
        result1.startsWith('Value for key1:')
        result2.startsWith('Value for key2:')
        service.keyInvocationCounter == 2

        when: 'calling with same keys again'
        def result1Again = service.getDataByKey('key1')
        def result2Again = service.getDataByKey('key2')

        then: 'both are cached'
        result1Again == result1
        result2Again == result2
        service.keyInvocationCounter == 2
    }

    void 'test custom key eviction'() {
        given: 'cached data for a key'
        def result1 = service.getDataByKey('mykey')
        service.keyInvocationCounter == 1

        when: 'evicting specific key and calling again'
        service.evictByKey('mykey')
        def result2 = service.getDataByKey('mykey')

        then: 'method is invoked again'
        result2 != result1
        service.keyInvocationCounter == 2
    }

    void 'test evicting one key does not affect other keys'() {
        given: 'cached data for two keys'
        def result1 = service.getDataByKey('key1')
        def result2 = service.getDataByKey('key2')
        service.keyInvocationCounter == 2

        when: 'evicting only key1'
        service.evictByKey('key1')
        def result1After = service.getDataByKey('key1')
        def result2After = service.getDataByKey('key2')

        then: 'key1 is recomputed, key2 is still cached'
        result1After != result1
        result2After == result2
        service.keyInvocationCounter == 3
    }

    // ========== eviction tests ==========

    void 'test eviction clears null cache'() {
        given: 'cached null value exists'
        service.getDataOrNull('null')
        service.nullInvocationCounter == 1

        when: 'evicting cache and calling again'
        service.evictNullCache()
        service.getDataOrNull('null')

        then: 'method is invoked again'
        service.nullInvocationCounter == 2
    }

    void 'test eviction clears collection caches'() {
        given: 'cached list exists'
        service.getListData('books')

        expect: 'method was invoked once'
        service.collectionInvocationCounter == 1

        when: 'evicting list cache and calling again'
        service.evictListCache()
        service.getListData('books')

        then: 'method is invoked again'
        service.collectionInvocationCounter == 2
    }

    void 'test eviction clears map cache'() {
        given: 'cached map exists'
        service.getMapData('mykey')

        expect: 'method was invoked once'
        service.collectionInvocationCounter == 1

        when: 'evicting map cache and calling again'
        service.evictMapCache()
        service.getMapData('mykey')

        then: 'method is invoked again'
        service.collectionInvocationCounter == 2
    }
}
