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

import grails.converters.JSON
import org.springframework.beans.factory.annotation.Autowired

/**
 * Controller for testing caching features via HTTP.
 */
class CacheTestController {

    static responseFormats = ['json']

    @Autowired
    CacheTestService cacheTestService

    /**
     * Get basic cached data.
     */
    def basicData() {
        def data = cacheTestService.getBasicData()
        render([data: data] as JSON)
    }

    /**
     * Get data by ID.
     */
    def dataById() {
        Long id = params.long('id', 1L)
        def data = cacheTestService.getDataById(id)
        render([id: id, data: data] as JSON)
    }

    /**
     * Get complex data.
     */
    def complexData() {
        String category = params.category ?: 'default'
        int page = params.int('page', 1)
        def data = cacheTestService.getComplexData(category, page)
        render([data: data] as JSON)
    }

    /**
     * Get conditional data.
     */
    def conditionalData() {
        boolean returnEmpty = params.boolean('empty', false)
        def data = cacheTestService.getConditionalData(returnEmpty)
        render([data: data] as JSON)
    }

    /**
     * Get data by key (uses custom key closure).
     */
    def byKey() {
        String key = params.key ?: 'default'
        def data = cacheTestService.getByKey(key)
        render([key: key, data: data] as JSON)
    }

    /**
     * Update cached value by key.
     */
    def updateByKey() {
        String key = params.key ?: 'default'
        String value = params.value ?: 'updated'
        def result = cacheTestService.updateByKey(key, value)
        render([key: key, value: result] as JSON)
    }

    /**
     * Evict basic cache.
     */
    def evictBasic() {
        cacheTestService.evictBasicCache()
        render([evicted: true, cache: 'basicCache'] as JSON)
    }

    /**
     * Evict by ID.
     */
    def evictById() {
        Long id = params.long('id', 1L)
        cacheTestService.evictById(id)
        render([evicted: true, id: id] as JSON)
    }

    /**
     * Evict all from param cache.
     */
    def evictAllParam() {
        cacheTestService.evictAllFromParamCache()
        render([evicted: true, allEntries: true] as JSON)
    }

    /**
     * Evict by key from keyed cache.
     */
    def evictByKey() {
        String key = params.key ?: 'default'
        cacheTestService.evictByKey(key)
        render([evicted: true, key: key] as JSON)
    }

    /**
     * Evict all from keyed cache.
     */
    def evictAllKeyed() {
        cacheTestService.evictAllKeyedCache()
        render([evicted: true, allEntries: true, cache: 'keyedCache'] as JSON)
    }
}
