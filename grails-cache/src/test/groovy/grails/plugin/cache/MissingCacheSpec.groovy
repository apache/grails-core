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

import java.util.function.Supplier

import ch.qos.logback.classic.Level
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.core.testing.support.LogCapture
import org.grails.plugin.cache.GrailsCacheManagerAliasRegistrar
import org.grails.plugin.cache.MissingCacheReporter

/**
 * A cache manager answers null for a cache name it has no cache of, as {@code JCacheCacheManager}
 * does for a cache it was not configured with. The annotated methods then run uncached, rather than
 * failing on the missing cache, and that is reported once for each cache manager and name.
 */
class MissingCacheSpec extends Specification {

    @Unroll
    void 'a method whose cache the cache manager does not have runs uncached, compiled #mode'() {
        given: 'the annotated class created as a bean, beside a second cache manager, with grailsCacheManager having only the cache named configured'
        CacheManager cacheManager = new ConcurrentMapCacheManager('configured')
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.addBeanFactoryPostProcessor(new GrailsCacheManagerAliasRegistrar())
        context.registerBean('grailsCacheManager', CacheManager, { -> cacheManager } as Supplier<CacheManager>)
        context.registerBean('cacheManager', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>)
        context.registerBean('service', (Class<Object>) compile(mode))
        context.refresh()
        def service = context.getBean('service')
        LogCapture log = new LogCapture(MissingCacheReporter, Level.WARN)

        expect: 'it is injected with grailsCacheManager'
        service.grailsCacheManager.is(cacheManager)

        when: 'every annotation is used twice with a cache that is missing'
        List<Object> results = [service.cached(1), service.cached(1), service.cachedByKey(1), service.cachedByKey(1),
                                service.put(1), service.put(1)]
        service.evict(1)
        service.evict(1)
        service.evictAll()
        service.evictAll()

        then: 'each method ran every time, and answered as it does uncached'
        results == [2, 2, 2, 2, 2, 2]
        service.calls == 10

        and: 'each missing cache is reported once'
        log.events*.formattedMessage == [
                "CachedService does not cache with missing: its cache manager, ${ConcurrentMapCacheManager.name}, " +
                        'has no cache of that name, so the methods run uncached. Configure the cache in that cache manager.',
                "CachedService does not cache with gone: its cache manager, ${ConcurrentMapCacheManager.name}, " +
                        'has no cache of that name, so the methods run uncached. Configure the cache in that cache manager.',
        ] as List<String>

        when: 'caches the cache manager does have, with the default key and with a key closure'
        service.calls = 0
        List<Object> cached = [service.cachedWhereConfigured(1), service.cachedWhereConfigured(1),
                               service.cachedByKeyWhereConfigured(1), service.cachedByKeyWhereConfigured(1)]

        then: 'still cache'
        cached == [3, 3, 4, 4]
        service.calls == 2

        cleanup:
        log?.close()
        context?.close()

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

    @Cacheable(value = 'missing', key = { x })
    int cachedByKey(int x) { calls++; x * 2 }

    @Cacheable('configured')
    int cachedWhereConfigured(int x) { calls++; x * 3 }

    @Cacheable(value = 'configured', key = { x + 100 })
    int cachedByKeyWhereConfigured(int x) { calls++; x * 4 }

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
