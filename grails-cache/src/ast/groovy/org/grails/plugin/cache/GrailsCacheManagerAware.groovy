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
     * Every cache manager in the context, by bean name. Injected as a map rather than as the one
     * cache manager because another one, such as the one the Spring Security plugin caches users with
     * when {@code cacheUsers} is set, made that injection ambiguous and the context failed to start:
     * Spring falls back to matching the field's name, and a trait's field is renamed in the class
     * that implements it.
     */
    @Autowired(required = false)
    private Map<String, CacheManager> cacheManagers

    private CacheManager grailsCacheManager

    /**
     * @return The Grails cache manager, the bean named {@code grailsCacheManager}, or where there is
     * none the only cache manager in the context, or null if there is no such cache manager
     */
    CacheManager getGrailsCacheManager() {
        if (grailsCacheManager == null && cacheManagers) {
            grailsCacheManager = cacheManagers.get('grailsCacheManager') ?:
                    (cacheManagers.size() == 1 ? cacheManagers.values().first() : null)
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
