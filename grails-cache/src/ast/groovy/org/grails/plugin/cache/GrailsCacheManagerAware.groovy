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
 * A trait for classes that are cache aware.
 *
 * <p>The cache manager such a class caches with is the bean named {@code grailsCacheManager}, or the bean
 * with that alias, which the plugin defines and a cache provider plugin or the application replaces. Spring
 * injects it by that name, as the field is qualified with {@link GrailsCacheManagerQualifier}, so another cache
 * manager in the context, marked primary or with a {@code @Priority} or not, and declaring Spring's
 * {@code @Qualifier('grailsCacheManager')} or not, is never taken for it. Where there is no such bean, as with
 * {@code grails.cache.enabled: false}, the class has no cache manager and its cached methods run
 * uncached.</p>
 *
 * <p>A class compiled against Grails 8 has the field without the qualifier until it is compiled again;
 * {@code GrailsCacheManagerAliasRegistrar} gives it {@code grailsCacheManager} where Spring could not
 * otherwise choose, as in Grails 8.0.1.</p>
 *
 * @since 4.0
 * @author Graeme Rocher
 */
@CompileStatic
trait GrailsCacheManagerAware {

    @Autowired(required = false)
    private GrailsCacheKeyGenerator customCacheKeyGenerator = new CustomCacheKeyGenerator()

    /** The bean named {@code grailsCacheManager}, as the cache tags and the plugin use it. */
    @Autowired(required = false)
    @GrailsCacheManagerQualifier
    private CacheManager grailsCacheManager

    /**
     * @return The Grails cache manager or null if it isn't present
     */
    CacheManager getGrailsCacheManager() {
        return grailsCacheManager
    }

    /**
     * @return The custom key generator, or null if it isn't present
     */
    GrailsCacheKeyGenerator getCustomCacheKeyGenerator() {
        return customCacheKeyGenerator
    }
}
