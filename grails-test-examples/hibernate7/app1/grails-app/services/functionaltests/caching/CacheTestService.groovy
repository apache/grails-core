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

import groovy.transform.CompileStatic

import grails.plugin.cache.CacheEvict
import grails.plugin.cache.CachePut
import grails.plugin.cache.Cacheable

/**
 * Service demonstrating Grails caching features.
 * 
 * Methods return data with timestamps/UUIDs so tests can verify 
 * caching behavior by comparing results.
 */
@CompileStatic
class CacheTestService {

    /**
     * Basic cached method. Returns data with timestamp.
     */
    @Cacheable('basicCache')
    String getBasicData() {
        "Basic data: ${System.currentTimeMillis()}"
    }

    /**
     * Cached method with parameter-based key.
     * Returns data with UUID so each call (without cache) produces unique result.
     */
    @Cacheable('paramCache')
    String getDataById(Long id) {
        "Data for ID ${id}: ${UUID.randomUUID()}"
    }

    /**
     * Cached method with multiple parameters forming the cache key.
     */
    @Cacheable('complexCache')
    Map<String, Object> getComplexData(String category, int page) {
        [
            category: category,
            page: page,
            timestamp: System.currentTimeMillis(),
            items: (1..5).collect { "Item ${it}" }
        ]
    }

    /**
     * Cacheable method that returns different data based on input.
     */
    @Cacheable('conditionalCache')
    List<String> getConditionalData(boolean returnEmpty) {
        if (returnEmpty) {
            return []
        }
        ['item1', 'item2', 'item3']
    }

    /**
     * Cached method using a custom key closure for testing.
     */
    @Cacheable(value = 'keyedCache', key = { key })
    String getByKey(String key) {
        "Value for ${key}: ${UUID.randomUUID()}"
    }

    /**
     * CachePut with key closure - updates the keyed cache.
     */
    @CachePut(value = 'keyedCache', key = { key })
    String updateByKey(String key, String value) {
        value
    }

    /**
     * Evict from basic cache.
     */
    @CacheEvict(value = 'basicCache', allEntries = true)
    void evictBasicCache() {
        // Cache is evicted
    }

    /**
     * Evict specific entry from param cache.
     */
    @CacheEvict(value = 'paramCache')
    void evictById(Long id) {
        // Evicts the entry for the given ID
    }

    /**
     * Evict all entries from param cache.
     */
    @CacheEvict(value = 'paramCache', allEntries = true)
    void evictAllFromParamCache() {
        // Evicts all entries
    }

    /**
     * Evict from keyed cache using key closure.
     */
    @CacheEvict(value = 'keyedCache', key = { key })
    void evictByKey(String key) {
        // Evicts the specific key
    }

    /**
     * Evict all from keyed cache.
     */
    @CacheEvict(value = 'keyedCache', allEntries = true)
    void evictAllKeyedCache() {
        // Evicts all entries
    }
}
