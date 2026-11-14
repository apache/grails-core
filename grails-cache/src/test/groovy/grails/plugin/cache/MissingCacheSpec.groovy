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
package grails.plugin.cache

import ch.qos.logback.classic.Level
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.core.testing.support.LogCapture
import org.grails.plugin.cache.GrailsCacheManagerLookup

/**
 * A cache manager answers null for a cache name it has no cache of, as {@code JCacheCacheManager}
 * does for a cache it was not configured with, and as the Spring Security plugin's cache manager does
 * when caching is off and the annotations use it. The annotated methods then run uncached, rather than
 * failing on the missing cache, and that is reported once for each cache manager and name.
 */
class MissingCacheSpec extends Specification {

    @Unroll
    void 'a method whose cache the cache manager does not have runs uncached, compiled #mode'() {
        given: 'a cache manager with only the cache named configured'
        def service = compile(mode).getDeclaredConstructor().newInstance()
        CacheManager cacheManager = new ConcurrentMapCacheManager('configured')
        service.@"org_grails_plugin_cache_GrailsCacheManagerAware__grailsCacheManager" = cacheManager
        LogCapture log = new LogCapture(GrailsCacheManagerLookup, Level.WARN)

        when: 'every annotation is used twice with a cache that is missing'
        List<Object> results = [service.cached(1), service.cached(1), service.put(1), service.put(1)]
        service.evict(1)
        service.evict(1)
        service.evictAll()
        service.evictAll()

        then: 'each method ran every time, and answered as it does uncached'
        results == [2, 2, 2, 2]
        service.calls == 8

        and: 'each missing cache is reported once'
        log.events*.formattedMessage == [
                "CachedService does not cache with missing: its cache manager, ${ConcurrentMapCacheManager.name}, " +
                        'has no cache of that name, so the methods run uncached. Configure the cache in that cache manager, ' +
                        'or define the one to cache with as grailsCacheManager.',
                "CachedService does not cache with gone: its cache manager, ${ConcurrentMapCacheManager.name}, " +
                        'has no cache of that name, so the methods run uncached. Configure the cache in that cache manager, ' +
                        'or define the one to cache with as grailsCacheManager.',
        ] as List<String>

        when: 'a cache the cache manager does have'
        service.calls = 0
        List<Object> cached = [service.cachedWhereConfigured(1), service.cachedWhereConfigured(1)]

        then: 'still caches'
        cached == [3, 3]
        service.calls == 1

        cleanup:
        log?.close()

        where:
        mode << ['statically', 'dynamically']
    }

    private static Class<?> compile(String mode) {
        new GroovyShell().evaluate("""
import grails.plugin.cache.*
import groovy.transform.CompileStatic

${mode == 'statically' ? '@CompileStatic' : ''}
class CachedService {
    int calls

    @Cacheable('missing')
    int cached(int x) { calls++; x * 2 }

    @Cacheable('configured')
    int cachedWhereConfigured(int x) { calls++; x * 3 }

    @CachePut('missing')
    int put(int x) { calls++; x * 2 }

    @CacheEvict('gone')
    void evict(int x) { calls++ }

    @CacheEvict(value = 'gone', allEntries = true)
    void evictAll() { calls++ }
}
return CachedService
""") as Class<?>
    }
}
