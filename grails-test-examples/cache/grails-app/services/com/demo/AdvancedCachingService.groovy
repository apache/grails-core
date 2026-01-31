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

import grails.plugin.cache.CacheEvict
import grails.plugin.cache.Cacheable

/**
 * Service demonstrating additional Grails caching scenarios.
 *
 * Covers advanced @Cacheable features like:
 * - null value caching
 * - exception handling
 * - collection caching (List and Map)
 */
class AdvancedCachingService {

    private int nullInvocationCounter = 0
    private int exceptionInvocationCounter = 0
    private int collectionInvocationCounter = 0
    private int keyInvocationCounter = 0

    // ========== null value caching tests ==========

    /**
     * Cacheable method that may return null.
     * Tests whether null values are cached by default.
     */
    @Cacheable('nullCache')
    String getDataOrNull(String input) {
        nullInvocationCounter++
        if (input == 'null') {
            return null
        }
        "Data for ${input}"
    }

    // ========== exception handling tests ==========

    /**
     * Cacheable method that throws exception for certain inputs.
     * Tests whether exceptions prevent caching.
     */
    @Cacheable('exceptionCache')
    String getDataOrThrow(String input) {
        exceptionInvocationCounter++
        if (input == 'error') {
            throw new RuntimeException("Simulated error for input: ${input}")
        }
        "Data for ${input}: ${UUID.randomUUID()}"
    }

    // ========== collection caching tests ==========

    /**
     * Cacheable method returning a List.
     * Includes a UUID to verify cache eviction works (data changes after eviction).
     */
    @Cacheable('listCache')
    List<String> getListData(String category) {
        collectionInvocationCounter++
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        ["Item 1 for ${category} (${uuid})", "Item 2 for ${category} (${uuid})", "Item 3 for ${category} (${uuid})"]
    }

    /**
     * Cacheable method returning a Map.
     */
    @Cacheable('mapCache')
    Map<String, Object> getMapData(String key) {
        collectionInvocationCounter++
        [
            key: key,
            value: "Value for ${key}",
            timestamp: System.currentTimeMillis(),
            nested: [a: 1, b: 2]
        ]
    }

    // ========== custom key caching tests ==========

    /**
     * Cacheable method with custom key using closure.
     */
    @Cacheable(value = 'keyCache', key = { key })
    String getDataByKey(String key) {
        keyInvocationCounter++
        "Value for ${key}: ${UUID.randomUUID()}"
    }

    // ========== evict methods ==========

    @CacheEvict(value = 'nullCache', allEntries = true)
    void evictNullCache() {
    }

    @CacheEvict(value = 'exceptionCache', allEntries = true)
    void evictExceptionCache() {
    }

    @CacheEvict(value = 'listCache', allEntries = true)
    void evictListCache() {
    }

    @CacheEvict(value = 'mapCache', allEntries = true)
    void evictMapCache() {
    }

    @CacheEvict(value = 'keyCache', key = { key })
    void evictByKey(String key) {
    }

    @CacheEvict(value = 'keyCache', allEntries = true)
    void evictAllKeyCache() {
    }

    // ========== getter methods for counters ==========

    int getNullInvocationCounter() {
        nullInvocationCounter
    }

    int getExceptionInvocationCounter() {
        exceptionInvocationCounter
    }

    int getCollectionInvocationCounter() {
        collectionInvocationCounter
    }

    int getKeyInvocationCounter() {
        keyInvocationCounter
    }

    void resetCounters() {
        nullInvocationCounter = 0
        exceptionInvocationCounter = 0
        collectionInvocationCounter = 0
        keyInvocationCounter = 0
    }
}
