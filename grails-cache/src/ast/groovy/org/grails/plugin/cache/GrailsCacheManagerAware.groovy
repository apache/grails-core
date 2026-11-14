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

package org.grails.plugin.cache

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager

import grails.plugin.cache.CustomCacheKeyGenerator
import grails.plugin.cache.GrailsCacheKeyGenerator

/**
 * A trait for classes that are cache aware
 *
 * @since 4.0
 * @author Graeme Rocher
 */
@CompileStatic
trait GrailsCacheManagerAware {

    @Autowired(required = false)
    private GrailsCacheKeyGenerator customCacheKeyGenerator = new CustomCacheKeyGenerator()

    /**
     * Where the cache manager is looked up, the first time it is asked for, rather than injected: another
     * cache manager, such as the one the Spring Security plugin caches users with when {@code cacheUsers}
     * is set, made the injection of a single cache manager ambiguous, and the context failed to start,
     * as Spring falls back to matching the field's name and a trait's field is renamed in the class that
     * implements it.
     */
    @Autowired(required = false)
    private BeanFactory cacheManagerSource

    private CacheManager grailsCacheManager

    private boolean grailsCacheManagerLookedUp

    /**
     * @return The cache manager named {@code grailsCacheManager}, or where there is none the one Spring
     * would inject into a {@code CacheManager} field, or null if there is none or Spring cannot choose
     * one. See {@link GrailsCacheManagerLookup}.
     */
    CacheManager getGrailsCacheManager() {
        if (grailsCacheManager == null && !grailsCacheManagerLookedUp && cacheManagerSource != null) {
            grailsCacheManager = GrailsCacheManagerLookup.find(cacheManagerSource, getClass())
            grailsCacheManagerLookedUp = true
        }
        return grailsCacheManager
    }

    /**
     * @return The custom key generator, or null if it isn't present
     */
    GrailsCacheKeyGenerator getCustomCacheKeyGenerator() {
        return customCacheKeyGenerator
    }
}
