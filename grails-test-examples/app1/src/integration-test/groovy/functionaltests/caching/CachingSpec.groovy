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

package functionaltests.caching

import groovy.json.JsonSlurper

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration

/**
 * Integration tests for Grails caching with @Cacheable, @CacheEvict, @CachePut.
 * 
 * Tests verify that cached methods return consistent data without
 * re-executing the method body, and that cache eviction works correctly.
 * 
 * Note: These tests focus on caching BEHAVIOR (data consistency) rather than
 * counting method invocations, since @Cacheable proxies prevent method body
 * execution on cache hits.
 */
@Integration
@Narrative('''
Grails caching provides method-level caching via annotations @Cacheable,
@CacheEvict, and @CachePut. This allows expensive operations to be cached
and only recomputed when necessary.
''')
class CachingSpec extends Specification {

    @Autowired
    CacheTestService cacheTestService

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:$serverPort"))

        // Evict all caches before each test to ensure clean state
        cacheTestService.evictBasicCache()
        cacheTestService.evictAllFromParamCache()
        cacheTestService.evictAllKeyedCache()
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== Basic @Cacheable Tests - Data Consistency ==========

    def "cached method returns same result on subsequent calls"() {
        when: "calling cached method twice"
        def result1 = cacheTestService.getBasicData()
        def result2 = cacheTestService.getBasicData()

        then: "same result is returned (proving caching works)"
        result1 == result2
        result1.startsWith('Basic data:')
    }

    def "cached method returns different result after eviction"() {
        given: "cached data exists"
        def result1 = cacheTestService.getBasicData()

        when: "cache is evicted and method called again"
        cacheTestService.evictBasicCache()
        // Small delay to ensure timestamp changes
        Thread.sleep(10)
        def result2 = cacheTestService.getBasicData()

        then: "new result is generated (timestamps differ)"
        result1 != result2
        result1.startsWith('Basic data:')
        result2.startsWith('Basic data:')
    }

    def "multiple eviction and fetch cycles work correctly"() {
        when: "performing multiple evict/fetch cycles"
        def results = []
        3.times {
            cacheTestService.evictBasicCache()
            Thread.sleep(5)
            results << cacheTestService.getBasicData()
        }

        then: "each cycle produces a different result"
        results.unique().size() == 3
    }

    // ========== Parameter-Based Cache Key Tests ==========

    def "cached method with parameter uses parameter as key"() {
        when: "calling with different IDs"
        def result1 = cacheTestService.getDataById(1L)
        def result2 = cacheTestService.getDataById(2L)
        def result3 = cacheTestService.getDataById(1L)

        then: "different IDs create different cache entries"
        result1 != result2
        
        and: "same ID returns cached result"
        result1 == result3
    }

    def "evicting specific cache entry leaves others intact"() {
        given: "multiple cached entries"
        def result1a = cacheTestService.getDataById(1L)
        def result2a = cacheTestService.getDataById(2L)

        when: "evicting only ID 1 and fetching again"
        cacheTestService.evictById(1L)
        Thread.sleep(5)
        def result1b = cacheTestService.getDataById(1L)
        def result2b = cacheTestService.getDataById(2L)

        then: "ID 1 was recomputed (different result)"
        result1a != result1b
        
        and: "ID 2 was still cached (same result)"
        result2a == result2b
    }

    def "evicting all entries clears entire cache"() {
        given: "multiple cached entries"
        def result1a = cacheTestService.getDataById(1L)
        def result2a = cacheTestService.getDataById(2L)
        def result3a = cacheTestService.getDataById(3L)

        when: "evicting all entries and fetching again"
        cacheTestService.evictAllFromParamCache()
        Thread.sleep(5)
        def result1b = cacheTestService.getDataById(1L)
        def result2b = cacheTestService.getDataById(2L)

        then: "all entries were recomputed"
        result1a != result1b
        result2a != result2b
    }

    def "cache handles many different parameter values"() {
        when: "caching many different IDs"
        def results = (1L..10L).collect { id ->
            cacheTestService.getDataById(id)
        }
        
        and: "fetching them again"
        def resultsAgain = (1L..10L).collect { id ->
            cacheTestService.getDataById(id)
        }

        then: "all results match their cached values"
        results == resultsAgain
        
        and: "all results are unique (different IDs produce different results)"
        results.unique().size() == 10
    }

    // ========== Complex Cache Key Tests ==========

    def "cache key includes multiple parameters"() {
        when: "calling with different parameter combinations"
        def result1 = cacheTestService.getComplexData('books', 1)
        def result2 = cacheTestService.getComplexData('books', 2)
        def result3 = cacheTestService.getComplexData('movies', 1)
        def result4 = cacheTestService.getComplexData('books', 1)

        then: "each combination has its own cache entry"
        result1 != result2
        result1 != result3
        result2 != result3
        
        and: "same combination returns cached result"
        result1 == result4
        result1.category == 'books'
        result1.page == 1
    }

    def "complex data structure is cached correctly"() {
        when: "fetching complex data"
        def result = cacheTestService.getComplexData('tech', 5)

        then: "all fields are present"
        result.category == 'tech'
        result.page == 5
        result.timestamp != null
        result.items.size() == 5
        result.items == ['Item 1', 'Item 2', 'Item 3', 'Item 4', 'Item 5']
    }

    // ========== @CachePut Tests with Key Closures ==========

    def "CachePut updates cache with new value using key closure"() {
        given: "existing cached value for a key"
        def original = cacheTestService.getByKey('mykey')
        original.startsWith('Value for mykey:')

        when: "updating cache with new value"
        def updated = cacheTestService.updateByKey('mykey', 'New value for mykey')

        then: "updated value is returned"
        updated == 'New value for mykey'
        
        when: "getting by key again"
        def afterUpdate = cacheTestService.getByKey('mykey')

        then: "cached value is the updated one"
        afterUpdate == updated
    }

    def "CachePut can be called multiple times"() {
        when: "updating cache multiple times"
        cacheTestService.updateByKey('testkey', 'Value 1')
        cacheTestService.updateByKey('testkey', 'Value 2')
        def finalValue = cacheTestService.updateByKey('testkey', 'Value 3')

        then: "last update wins"
        finalValue == 'Value 3'
        cacheTestService.getByKey('testkey') == 'Value 3'
    }

    def "CachePut for one key does not affect other keys"() {
        given: "two different cached keys"
        cacheTestService.getByKey('key1')
        def key2Original = cacheTestService.getByKey('key2')

        when: "updating only key1"
        cacheTestService.updateByKey('key1', 'Updated key1')

        then: "key1 is updated, key2 is unchanged"
        cacheTestService.getByKey('key1') == 'Updated key1'
        cacheTestService.getByKey('key2') == key2Original
    }

    // ========== Conditional Caching Tests ==========

    def "conditional data is cached based on input"() {
        when: "fetching non-empty data"
        def result1 = cacheTestService.getConditionalData(false)
        def result2 = cacheTestService.getConditionalData(false)

        then: "results are cached and consistent"
        result1 == result2
        result1 == ['item1', 'item2', 'item3']
    }

    def "different boolean parameters create different cache entries"() {
        when: "fetching with different parameters"
        def nonEmpty = cacheTestService.getConditionalData(false)
        def empty = cacheTestService.getConditionalData(true)

        then: "different results based on parameter"
        nonEmpty == ['item1', 'item2', 'item3']
        empty == []
    }

    // ========== HTTP Endpoint Tests ==========

    def "basic cache works via HTTP"() {
        // Evict cache to start fresh
        client.toBlocking().exchange(HttpRequest.GET('/cacheTest/evictBasic'), String)

        when: "calling endpoint twice"
        def response1 = client.toBlocking().exchange(
            HttpRequest.GET('/cacheTest/basicData'),
            String
        )
        def response2 = client.toBlocking().exchange(
            HttpRequest.GET('/cacheTest/basicData'),
            String
        )

        then: "same data returned (caching works)"
        response1.status == HttpStatus.OK
        response2.status == HttpStatus.OK
        
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        
        json1.data == json2.data
    }

    def "parameter cache works via HTTP"() {
        // Evict cache
        client.toBlocking().exchange(HttpRequest.GET('/cacheTest/evictAllParam'), String)

        when: "calling with same ID twice"
        def response1 = client.toBlocking().exchange(
            HttpRequest.GET('/cacheTest/dataById?id=42'),
            String
        )
        def response2 = client.toBlocking().exchange(
            HttpRequest.GET('/cacheTest/dataById?id=42'),
            String
        )

        then: "cached result returned"
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        
        json1.data == json2.data
    }

    def "eviction works via HTTP"() {
        // Evict cache and populate it
        client.toBlocking().exchange(HttpRequest.GET('/cacheTest/evictBasic'), String)
        def firstCall = client.toBlocking().exchange(
                HttpRequest.GET('/cacheTest/basicData'),
                String
        )
        def firstData = new JsonSlurper().parseText(firstCall.body()).data

        when: "evicting via HTTP then calling again after delay"
        client.toBlocking().exchange(HttpRequest.GET('/cacheTest/evictBasic'), String)
        Thread.sleep(10) // Ensure timestamp changes
        def afterEvict = client.toBlocking().exchange(
            HttpRequest.GET('/cacheTest/basicData'),
            String
        )

        then: "new data generated after eviction"
        def afterEvictData = new JsonSlurper().parseText(afterEvict.body()).data
        firstData != afterEvictData
    }

    def "different IDs return different cached values via HTTP"() {
        given:
        client.toBlocking().exchange(HttpRequest.GET('/cacheTest/evictAllParam'), String)

        when: "fetching different IDs"
        def response1 = client.toBlocking().exchange(HttpRequest.GET('/cacheTest/dataById?id=100'), String)
        def response2 = client.toBlocking().exchange(HttpRequest.GET('/cacheTest/dataById?id=200'), String)
        def response3 = client.toBlocking().exchange(HttpRequest.GET('/cacheTest/dataById?id=100'), String)

        then: "different IDs have different data, same ID returns same data"
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        def json3 = new JsonSlurper().parseText(response3.body())
        
        json1.data != json2.data
        json1.data == json3.data
    }

    def "complex data endpoint works with caching"() {
        when: "fetching complex data"
        def response1 = client.toBlocking().exchange(
            HttpRequest.GET('/cacheTest/complexData?category=electronics&page=3'),
            String
        )
        def response2 = client.toBlocking().exchange(
            HttpRequest.GET('/cacheTest/complexData?category=electronics&page=3'),
            String
        )

        then: "data is cached"
        def json1 = new JsonSlurper().parseText(response1.body())
        def json2 = new JsonSlurper().parseText(response2.body())
        
        json1.data == json2.data
        json1.data.category == 'electronics'
        json1.data.page == 3
    }

    def "CachePut works via HTTP with key closure"() {
        // Evict keyed cache and get initial value
        client.toBlocking().exchange(HttpRequest.GET('/cacheTest/evictAllKeyed'), String)
        def initial = new JsonSlurper().parseText(
            client.toBlocking().exchange(HttpRequest.GET('/cacheTest/byKey?key=httpkey'), String).body()
        ).data

        when: "updating via HTTP"
        client.toBlocking().exchange(
                HttpRequest.GET('/cacheTest/updateByKey?key=httpkey&value=HTTPUpdated'),
                String
        )
        def afterUpdate = new JsonSlurper().parseText(
            client.toBlocking().exchange(HttpRequest.GET('/cacheTest/byKey?key=httpkey'), String).body()
        ).data

        then: "cache contains updated value"
        afterUpdate == 'HTTPUpdated'
        afterUpdate != initial
    }
}
