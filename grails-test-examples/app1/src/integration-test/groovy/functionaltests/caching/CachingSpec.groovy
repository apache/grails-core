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

import spock.lang.Narrative
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

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
class CachingSpec extends Specification implements HttpClientSupport {

    @Autowired
    CacheTestService cacheTestService

    def setup() {
        // Evict all caches before each test to ensure clean state
        cacheTestService.evictBasicCache()
        cacheTestService.evictAllFromParamCache()
        cacheTestService.evictAllKeyedCache()
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
        setup: "Evict cache to start fresh"
        http('/cacheTest/evictBasic')

        when: "calling endpoint twice"
        def response1 = http('/cacheTest/basicData')
        def response2 = http('/cacheTest/basicData')

        then: "same data returned (caching works)"
        response1.assertStatus(200)
        response2.assertStatus(200)
        response1.json().data == response2.json().data
    }

    def "parameter cache works via HTTP"() {
        setup: "Evict cache"
        http('/cacheTest/evictAllParam')

        when: "calling with same ID twice"
        def response1 = http('/cacheTest/dataById?id=42')
        def response2 = http('/cacheTest/dataById?id=42')

        then: "cached result returned"
        response1.json().data == response2.json().data
    }

    def "eviction works via HTTP"() {
        setup: "Evict cache and populate it"
        http('/cacheTest/evictBasic')

        when:
        def firstCall = http('/cacheTest/basicData')
        def firstData = firstCall.json().data

        and: "evicting via HTTP then calling again after delay"
        http('/cacheTest/evictBasic')
        Thread.sleep(10) // Ensure timestamp changes
        def afterEvict = http('/cacheTest/basicData')

        then: "new data generated after eviction"
        firstData != afterEvict.json().data
    }

    def "different IDs return different cached values via HTTP"() {
        setup:
        http('/cacheTest/evictAllParam')

        when: "fetching different IDs"
        def response1 = http('/cacheTest/dataById?id=100')
        def response2 = http('/cacheTest/dataById?id=200')
        def response3 = http('/cacheTest/dataById?id=100')

        then: "different IDs have different data, same ID returns same data"
        def json1 = response1.json()
        def json2 = response2.json()
        def json3 = response3.json()
        
        json1.data != json2.data
        json1.data == json3.data
    }

    def "complex data endpoint works with caching"() {
        when: "fetching complex data"
        def response1 = http('/cacheTest/complexData?category=electronics&page=3')
        def response2 = http('/cacheTest/complexData?category=electronics&page=3')

        then: "data is cached"
        def json1 = response1.json()
        def json2 = response2.json()
        
        json1.data == json2.data
        json1.data.category == 'electronics'
        json1.data.page == 3
    }

    def "CachePut works via HTTP with key closure"() {
        setup: "Evict keyed cache and get initial value"
        http('/cacheTest/evictAllKeyed')

        when:
        def initial = http('/cacheTest/byKey?key=httpkey').json().data

        and: "updating via HTTP"
        http('/cacheTest/updateByKey?key=httpkey&value=HTTPUpdated')

        and: "loading the data again"
        def afterUpdate = http('/cacheTest/byKey?key=httpkey').json().data

        then: "cache contains updated value"
        afterUpdate == 'HTTPUpdated'
        afterUpdate != initial
    }
}
