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

import grails.converters.JSON

import org.springframework.beans.factory.annotation.Autowired

/**
 * Controller exposing advanced caching scenarios via HTTP endpoints.
 */
class AdvancedCachingController {

    @Autowired
    AdvancedCachingService advancedCachingService

    // ========== null value endpoints ==========

    def dataOrNull() {
        try {
            def data = advancedCachingService.getDataOrNull(params.input)
            render([data: data] as JSON)
        } catch (Exception e) {
            response.status = 500
            render([error: e.message] as JSON)
        }
    }

    // ========== exception handling endpoints ==========

    def dataOrThrow() {
        try {
            def data = advancedCachingService.getDataOrThrow(params.input)
            render([data: data] as JSON)
        } catch (Exception e) {
            response.status = 500
            render([error: e.message] as JSON)
        }
    }

    // ========== collection endpoints ==========

    def listData() {
        try {
            def data = advancedCachingService.getListData(params.category)
            render([data: data] as JSON)
        } catch (Exception e) {
            response.status = 500
            render([error: e.message] as JSON)
        }
    }

    def mapData() {
        try {
            def data = advancedCachingService.getMapData(params.key)
            render([data: data] as JSON)
        } catch (Exception e) {
            response.status = 500
            render([error: e.message] as JSON)
        }
    }

    // ========== custom key caching endpoints ==========

    def getDataByKey() {
        try {
            def data = advancedCachingService.getDataByKey(params.key)
            render([data: data] as JSON)
        } catch (Exception e) {
            response.status = 500
            render([error: e.message] as JSON)
        }
    }

    // ========== eviction endpoints ==========

    def evictNullCache() {
        advancedCachingService.evictNullCache()
        render([status: 'evicted'] as JSON)
    }

    def evictExceptionCache() {
        advancedCachingService.evictExceptionCache()
        render([status: 'evicted'] as JSON)
    }

    def evictListCache() {
        advancedCachingService.evictListCache()
        render([status: 'evicted'] as JSON)
    }

    def evictMapCache() {
        advancedCachingService.evictMapCache()
        render([status: 'evicted'] as JSON)
    }

    def evictByKey() {
        advancedCachingService.evictByKey(params.key)
        render([status: 'evicted'] as JSON)
    }

    def evictAllKeyCache() {
        advancedCachingService.evictAllKeyCache()
        render([status: 'evicted'] as JSON)
    }
}
