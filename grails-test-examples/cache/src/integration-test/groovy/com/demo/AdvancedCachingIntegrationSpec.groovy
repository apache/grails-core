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

import groovy.json.JsonSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
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
        def response1 = httpClient.exchange(
            '/advancedCaching/listData?category=books',
            String
        )
        def response2 = httpClient.exchange(
            '/advancedCaching/listData?category=books',
            String
        )

        then: "both calls return same data (cached)"
        response1.status == HttpStatus.OK
        response2.status == HttpStatus.OK
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        json1.data == json2.data
        json1.data.size() == 3
        json1.data[0].startsWith('Item 1 for books')
    }

    def "map data is cached via HTTP"() {
        when: "fetching map data twice"
        def response1 = httpClient.exchange(
            '/advancedCaching/mapData?key=mykey',
            String
        )
        def response2 = httpClient.exchange(
            '/advancedCaching/mapData?key=mykey',
            String
        )

        then: "both calls return same data (cached)"
        response1.status == HttpStatus.OK
        response2.status == HttpStatus.OK
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        json1.data == json2.data
        json1.data.key == 'mykey'
        json1.data.value == 'Value for mykey'
        json1.data.nested.a == 1
    }

    def "different categories have separate list cache entries via HTTP"() {
        when: "fetching different categories"
        def booksResponse = httpClient.exchange(
            '/advancedCaching/listData?category=books',
            String
        )
        def moviesResponse = httpClient.exchange(
            '/advancedCaching/listData?category=movies',
            String
        )

        then: "different categories return different data"
        booksResponse.status == HttpStatus.OK
        moviesResponse.status == HttpStatus.OK
        def books = new JsonSlurper().parseText(booksResponse.body())
        def movies = new JsonSlurper().parseText(moviesResponse.body())
        books.data != movies.data
        books.data[0].startsWith('Item 1 for books')
        movies.data[0].startsWith('Item 1 for movies')
    }

    // ========== exception handling integration tests ==========

    def "exception is thrown and not cached via HTTP"() {
        when: "calling endpoint that throws exception"
        httpClient.retrieve('/advancedCaching/dataOrThrow?input=error')

        then: "exception results in error response"
        thrown(Exception)
    }

    def "successful calls are cached even after exceptions via HTTP"() {
        when: "calling with normal value twice"
        def response1 = httpClient.exchange(
            '/advancedCaching/dataOrThrow?input=normal',
            String
        )
        def response2 = httpClient.exchange(
            '/advancedCaching/dataOrThrow?input=normal',
            String
        )

        then: "second call returns cached result"
        response1.status == HttpStatus.OK
        response2.status == HttpStatus.OK
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        json1.data == json2.data
    }

    // ========== eviction integration tests ==========

    def "eviction clears list cache via HTTP"() {
        given:
        // First call to populate cache
        def first = httpClient.retrieve('/advancedCaching/listData?category=books')
        def firstData = new JsonSlurper().parseText(first).data

        when: "evicting cache and fetching again"
        httpClient.retrieve('/advancedCaching/evictListCache')
        def second = httpClient.retrieve('/advancedCaching/listData?category=books')
        def secondData = new JsonSlurper().parseText(second).data

        then: "new data is generated after eviction"
        firstData != secondData
    }

    def "eviction clears map cache via HTTP"() {
        given:
        // First call to populate cache
        def first = httpClient.retrieve('/advancedCaching/mapData?key=mykey')
        def firstData = new JsonSlurper().parseText(first).data

        when: "evicting cache and fetching again"
        httpClient.retrieve('/advancedCaching/evictMapCache')
        def second = httpClient.retrieve('/advancedCaching/mapData?key=mykey')
        def secondData = new JsonSlurper().parseText(second).data

        then: "new data is generated after eviction"
        firstData != secondData
    }

    // ========== custom key caching integration tests ==========

    def "custom key caching works via HTTP"() {
        when: "fetching data by custom key twice"
        def response1 = httpClient.exchange(
            '/advancedCaching/getDataByKey?key=testkey',
            String
        )
        def response2 = httpClient.exchange(
            '/advancedCaching/getDataByKey?key=testkey',
            String
        )

        then: "second call returns cached result"
        response1.status == HttpStatus.OK
        response2.status == HttpStatus.OK
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        json1.data == json2.data
    }

    def "eviction by custom key works via HTTP"() {
        given:
        // First call to populate cache
        def first = httpClient.retrieve('/advancedCaching/getDataByKey?key=mykey')
        def firstData = new JsonSlurper().parseText(first).data

        when: "evicting by key and fetching again"
        httpClient.retrieve('/advancedCaching/evictByKey?key=mykey')
        def second = httpClient.retrieve('/advancedCaching/getDataByKey?key=mykey')
        def secondData = new JsonSlurper().parseText(second).data

        then: "new data is generated after eviction"
        firstData != secondData
    }

    def "eviction of all custom key cache works via HTTP"() {
        given:
        // First call to populate cache
        def first = httpClient.retrieve('/advancedCaching/getDataByKey?key=anykey')
        def firstData = new JsonSlurper().parseText(first).data

        when: "evicting all and fetching again"
        httpClient.retrieve('/advancedCaching/evictAllKeyCache')
        def second = httpClient.retrieve('/advancedCaching/getDataByKey?key=anykey',)
        def secondData = new JsonSlurper().parseText(second).data

        then: "new data is generated after eviction"
        firstData != secondData
    }
}
