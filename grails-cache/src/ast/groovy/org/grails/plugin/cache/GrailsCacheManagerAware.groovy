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

import org.springframework.beans.factory.ObjectProvider
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
     * The cache manager Spring would inject: the only one in the context, or the one marked primary.
     */
    @Autowired(required = false)
    private ObjectProvider<CacheManager> cacheManagerProvider

    /**
     * Every cache manager in the context, by bean name, for when Spring cannot choose one. Another cache
     * manager, such as the one the Spring Security plugin caches users with when {@code cacheUsers} is
     * set, used to make the injection of a single cache manager ambiguous, and the context failed to
     * start: Spring falls back to matching the field's name, and a trait's field is renamed in the class
     * that implements it.
     */
    @Autowired(required = false)
    private Map<String, CacheManager> cacheManagers

    private CacheManager grailsCacheManager

    /**
     * @return The cache manager Spring would inject, the only one or the one marked primary, or where
     * there are several and none is primary the one named {@code grailsCacheManager}, or null if there
     * is no such cache manager
     */
    CacheManager getGrailsCacheManager() {
        if (grailsCacheManager == null) {
            grailsCacheManager = cacheManagerProvider?.getIfUnique() ?: cacheManagers?.get('grailsCacheManager')
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
