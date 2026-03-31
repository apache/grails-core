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

class UrlMappings {

    static mappings = {
        // Advanced caching endpoints
        "/advancedCaching/unlessSkip"(controller: 'advancedCaching', action: 'unlessSkip')
        "/advancedCaching/unlessNull"(controller: 'advancedCaching', action: 'unlessNull')
        "/advancedCaching/dataOrNull"(controller: 'advancedCaching', action: 'dataOrNull')
        "/advancedCaching/dataOrThrow"(controller: 'advancedCaching', action: 'dataOrThrow')
        "/advancedCaching/listData"(controller: 'advancedCaching', action: 'listData')
        "/advancedCaching/mapData"(controller: 'advancedCaching', action: 'mapData')
        "/advancedCaching/multiCacheData"(controller: 'advancedCaching', action: 'multiCacheData')
        
        // Eviction endpoints
        "/advancedCaching/evictUnlessCache"(controller: 'advancedCaching', action: 'evictUnlessCache')
        "/advancedCaching/evictUnlessNullCache"(controller: 'advancedCaching', action: 'evictUnlessNullCache')
        "/advancedCaching/evictNullCache"(controller: 'advancedCaching', action: 'evictNullCache')
        "/advancedCaching/evictExceptionCache"(controller: 'advancedCaching', action: 'evictExceptionCache')
        "/advancedCaching/evictListCache"(controller: 'advancedCaching', action: 'evictListCache')
        "/advancedCaching/evictMapCache"(controller: 'advancedCaching', action: 'evictMapCache')
        "/advancedCaching/evictMultipleCaches"(controller: 'advancedCaching', action: 'evictMultipleCaches')

        // Default mappings
        "/$controller/$action?/$id?(.$format)?" {
            constraints {
                // apply constraints here
            }
        }

        "/"(view: "/index")
        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}
