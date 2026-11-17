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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.cache.CacheManager

/**
 * Reports a cache the cache manager does not have, for the code the cache annotations generate.
 *
 * <p>A cache manager answers null for a name it has no cache of, as {@code JCacheCacheManager} does
 * for a cache it was not configured with. The annotated method then runs as it does without a cache
 * manager, and this says so once for each cache manager and name.</p>
 *
 * @since 8.0.1
 */
@Slf4j
@CompileStatic
final class MissingCacheReporter {

    /** The names of the caches reported missing, for each cache manager. */
    private static final Map<CacheManager, Set<String>> REPORTED = Collections.synchronizedMap(
            new WeakHashMap<CacheManager, Set<String>>())

    private MissingCacheReporter() {
    }

    /**
     * @param cacheManager the cache manager without the cache
     * @param cacheName the name the annotation gives
     * @param owner the class of the annotated method
     */
    static void report(CacheManager cacheManager, String cacheName, Class<?> owner) {
        Set<String> reported = REPORTED.computeIfAbsent(cacheManager) { CacheManager ignored ->
            ConcurrentHashMap.<String> newKeySet()
        }
        if (reported.add(cacheName)) {
            log.warn('{} does not cache with {}: its cache manager, {}, has no cache of that name, so the methods ' +
                    'run uncached. Configure the cache in that cache manager.', owner.name, cacheName, cacheManager.getClass().name)
        }
    }
}
