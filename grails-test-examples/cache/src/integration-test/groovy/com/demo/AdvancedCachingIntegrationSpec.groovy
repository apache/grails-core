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

import spock.lang.Narrative
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

/**
 * Integration tests for advanced @Cacheable scenarios via HTTP endpoints.
 *
 * Tests verify advanced caching features work correctly through HTTP calls:
 * - unless condition
 * - null value handling
 * - exception handling
 * - collection caching
 * - multiple cache names
 */
@Integration
@Narrative('''
Advanced Grails caching scenarios tested via HTTP endpoints.
These tests verify that complex caching behaviors work correctly
in a full application context.
''')
class AdvancedCachingIntegrationSpec extends Specification implements HttpClientSupport {

    @Autowired
    AdvancedCachingService advancedCachingService

    def setup() {
        // Evict all caches before each test to ensure clean state
        advancedCachingService.evictNullCache()
        advancedCachingService.evictExceptionCache()
        advancedCachingService.evictListCache()
        advancedCachingService.evictMapCache()
        advancedCachingService.evictAllKeyCache()
        advancedCachingService.resetCounters()
    }

    // ========== collection caching integration tests ==========

    def "list data is cached via HTTP"() {
        when: "fetching list data twice"
        def response1 = http('/advancedCaching/listData?category=books')
        def response2 = http('/advancedCaching/listData?category=books')

        then: "both calls return same data (cached)"
        response1.expectStatus(200)
        response2.expectStatus(200)
        def json1 = response1.json()
        def json2 = response2.json()
        json1.data == json2.data
        json1.data.size() == 3
        json1.data[0].startsWith('Item 1 for books')
    }

    def "map data is cached via HTTP"() {
        when: "fetching map data twice"
        def response1 = http('/advancedCaching/mapData?key=mykey')
        def response2 = http('/advancedCaching/mapData?key=mykey')

        then: "both calls return same data (cached)"
        response1.expectStatus(200)
        response2.expectStatus(200)
        def json1 = response1.json()
        def json2 = response2.json()
        json1.data == json2.data
        json1.data.key == 'mykey'
        json1.data.value == 'Value for mykey'
        json1.data.nested.a == 1
    }

    def "different categories have separate list cache entries via HTTP"() {
        when: "fetching different categories"
        def booksResponse = http('/advancedCaching/listData?category=books')
        def moviesResponse = http('/advancedCaching/listData?category=movies')

        then: "different categories return different data"
        booksResponse.expectStatus(200)
        moviesResponse.expectStatus(200)
        def books = booksResponse.json()
        def movies = moviesResponse.json()
        books.data != movies.data
        books.data[0].startsWith('Item 1 for books')
        movies.data[0].startsWith('Item 1 for movies')
    }

    // ========== exception handling integration tests ==========

    def "exception is thrown and not cached via HTTP"() {
        when: "calling endpoint that throws exception"
        def response = http('/advancedCaching/dataOrThrow?input=error')

        then: "exception results in error response"
        response.expectStatus(500)
    }

    def "successful calls are cached even after exceptions via HTTP"() {
        when: "calling with normal value twice"
        def response1 = http('/advancedCaching/dataOrThrow?input=normal')
        def response2 = http('/advancedCaching/dataOrThrow?input=normal')

        then: "second call returns cached result"
        response1.expectStatus(200)
        response2.expectStatus(200)
        def json1 = response1.json()
        def json2 = response2.json()
        json1.data == json2.data
    }

    // ========== eviction integration tests ==========

    def "eviction clears list cache via HTTP"() {
        given:
        // First call to populate cache
        def first = http('/advancedCaching/listData?category=books')
        def firstData = first.json().data

        when: "evicting cache and fetching again"
        http('/advancedCaching/evictListCache')
        def second = http('/advancedCaching/listData?category=books')
        def secondData = second.json().data

        then: "new data is generated after eviction"
        firstData != secondData
    }

    def "eviction clears map cache via HTTP"() {
        given:
        // First call to populate cache
        def first = http('/advancedCaching/mapData?key=mykey')
        def firstData = first.json().data

        when: "evicting cache and fetching again"
        http('/advancedCaching/evictMapCache')
        def second = http('/advancedCaching/mapData?key=mykey')
        def secondData = second.json().data

        then: "new data is generated after eviction"
        firstData != secondData
    }

    // ========== custom key caching integration tests ==========

    def "custom key caching works via HTTP"() {
        when: "fetching data by custom key twice"
        def response1 = http('/advancedCaching/getDataByKey?key=testkey')
        def response2 = http('/advancedCaching/getDataByKey?key=testkey')

        then: "second call returns cached result"
        response1.expectStatus(200)
        response2.expectStatus(200)
        response1.json().data == response2.json().data
    }

    def "eviction by custom key works via HTTP"() {
        given:
        // First call to populate cache
        def first = http('/advancedCaching/getDataByKey?key=mykey')
        def firstData = first.json().data

        when: "evicting by key and fetching again"
        http('/advancedCaching/evictByKey?key=mykey')
        def second = http('/advancedCaching/getDataByKey?key=mykey')
        def secondData = second.json().data

        then: "new data is generated after eviction"
        firstData != secondData
    }

    def "eviction of all custom key cache works via HTTP"() {
        given:
        // First call to populate cache
        def first = http('/advancedCaching/getDataByKey?key=anykey')
        def firstData = first.json().data

        when: "evicting all and fetching again"
        http('/advancedCaching/evictAllKeyCache')
        def second = http('/advancedCaching/getDataByKey?key=anykey')
        def secondData = second.json().data

        then: "new data is generated after eviction"
        firstData != secondData
    }
}
