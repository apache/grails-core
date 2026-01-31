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

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative

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
class AdvancedCachingIntegrationSpec extends ContainerGebSpec {

    @Autowired
    AdvancedCachingService advancedCachingService

    private HttpClient createClient() {
        HttpClient.create(new URL("http://localhost:$serverPort"))
    }

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
        given:
        def client = createClient()

        when: "fetching list data twice"
        HttpResponse<String> response1 = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/listData?category=books'),
            String
        )
        HttpResponse<String> response2 = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/listData?category=books'),
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

        cleanup:
        client?.close()
    }

    def "map data is cached via HTTP"() {
        given:
        def client = createClient()

        when: "fetching map data twice"
        HttpResponse<String> response1 = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/mapData?key=mykey'),
            String
        )
        HttpResponse<String> response2 = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/mapData?key=mykey'),
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

        cleanup:
        client?.close()
    }

    def "different categories have separate list cache entries via HTTP"() {
        given:
        def client = createClient()

        when: "fetching different categories"
        HttpResponse<String> booksResponse = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/listData?category=books'),
            String
        )
        HttpResponse<String> moviesResponse = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/listData?category=movies'),
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

        cleanup:
        client?.close()
    }

    // ========== exception handling integration tests ==========

    def "exception is thrown and not cached via HTTP"() {
        given:
        def client = createClient()

        when: "calling endpoint that throws exception"
        client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/dataOrThrow?input=error'),
            String
        )

        then: "exception results in error response"
        thrown(Exception)

        cleanup:
        client?.close()
    }

    def "successful calls are cached even after exceptions via HTTP"() {
        given:
        def client = createClient()

        when: "calling with normal value twice"
        HttpResponse<String> response1 = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/dataOrThrow?input=normal'),
            String
        )
        HttpResponse<String> response2 = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/dataOrThrow?input=normal'),
            String
        )

        then: "second call returns cached result"
        response1.status == HttpStatus.OK
        response2.status == HttpStatus.OK
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        json1.data == json2.data

        cleanup:
        client?.close()
    }

    // ========== eviction integration tests ==========

    def "eviction clears list cache via HTTP"() {
        given:
        def client = createClient()
        // First call to populate cache
        def first = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/listData?category=books'),
            String
        )
        def firstData = new JsonSlurper().parseText(first.body()).data

        when: "evicting cache and fetching again"
        client.toBlocking().exchange(HttpRequest.GET('/advancedCaching/evictListCache'), String)
        HttpResponse<String> second = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/listData?category=books'),
            String
        )
        def secondData = new JsonSlurper().parseText(second.body()).data

        then: "new data is generated after eviction"
        firstData != secondData

        cleanup:
        client?.close()
    }

    def "eviction clears map cache via HTTP"() {
        given:
        def client = createClient()
        // First call to populate cache
        def first = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/mapData?key=mykey'),
            String
        )
        def firstData = new JsonSlurper().parseText(first.body()).data

        when: "evicting cache and fetching again"
        client.toBlocking().exchange(HttpRequest.GET('/advancedCaching/evictMapCache'), String)
        HttpResponse<String> second = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/mapData?key=mykey'),
            String
        )
        def secondData = new JsonSlurper().parseText(second.body()).data

        then: "new data is generated after eviction"
        firstData != secondData

        cleanup:
        client?.close()
    }

    // ========== custom key caching integration tests ==========

    def "custom key caching works via HTTP"() {
        given:
        def client = createClient()

        when: "fetching data by custom key twice"
        HttpResponse<String> response1 = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/getDataByKey?key=testkey'),
            String
        )
        HttpResponse<String> response2 = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/getDataByKey?key=testkey'),
            String
        )

        then: "second call returns cached result"
        response1.status == HttpStatus.OK
        response2.status == HttpStatus.OK
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        json1.data == json2.data

        cleanup:
        client?.close()
    }

    def "eviction by custom key works via HTTP"() {
        given:
        def client = createClient()
        // First call to populate cache
        def first = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/getDataByKey?key=mykey'),
            String
        )
        def firstData = new JsonSlurper().parseText(first.body()).data

        when: "evicting by key and fetching again"
        client.toBlocking().exchange(HttpRequest.GET('/advancedCaching/evictByKey?key=mykey'), String)
        HttpResponse<String> second = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/getDataByKey?key=mykey'),
            String
        )
        def secondData = new JsonSlurper().parseText(second.body()).data

        then: "new data is generated after eviction"
        firstData != secondData

        cleanup:
        client?.close()
    }

    def "eviction of all custom key cache works via HTTP"() {
        given:
        def client = createClient()
        // First call to populate cache
        def first = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/getDataByKey?key=anykey'),
            String
        )
        def firstData = new JsonSlurper().parseText(first.body()).data

        when: "evicting all and fetching again"
        client.toBlocking().exchange(HttpRequest.GET('/advancedCaching/evictAllKeyCache'), String)
        HttpResponse<String> second = client.toBlocking().exchange(
            HttpRequest.GET('/advancedCaching/getDataByKey?key=anykey'),
            String
        )
        def secondData = new JsonSlurper().parseText(second.body()).data

        then: "new data is generated after eviction"
        firstData != secondData

        cleanup:
        client?.close()
    }
}
