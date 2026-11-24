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
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.core.testing.support.LogCapture
import org.grails.plugin.cache.MissingCacheReporter

/**
 * A parameter of a cached method named like the cache manager or the key generator a cache-aware class has
 * hides that property's name in the method; the code the cache annotations generate reads both through their
 * getters, so it caches with the class's own.
 */
class ShadowedPropertiesSpec extends Specification {

    @Unroll
    void 'a cached method with parameters named like the cache manager and the key generator caches with the class\'s own, compiled #mode'() {
        given: 'the class created as a bean beside its cache manager'
        CacheManager cacheManager = new ConcurrentMapCacheManager('configured')
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean('grailsCacheManager', CacheManager, { -> cacheManager } as Supplier<CacheManager>)
        context.registerBean('service', (Class<Object>) compile(mode))
        context.refresh()
        def service = context.getBean('service')

        and: 'another cache manager, and no key generator, passed to the methods'
        CacheManager passed = new ConcurrentMapCacheManager('configured')

        when: 'the cached method is called twice'
        List<Object> results = [service.cached(1, passed, null), service.cached(1, passed, null)]

        then: 'it ran once, and cached in the class\'s cache manager, not the one passed'
        results == [2, 2]
        service.calls == 1
        nativeCache(cacheManager).size() == 1
        nativeCache(passed).isEmpty()

        when: 'a put and an eviction of every entry, with the same parameters'
        service.put(5, passed, null)
        int afterPut = nativeCache(cacheManager).size()
        service.evictAll(passed, null)

        then: 'both act on the class\'s cache manager'
        afterPut == 2
        nativeCache(cacheManager).isEmpty()
        nativeCache(passed).isEmpty()

        cleanup:
        context?.close()

        where:
        mode << ['statically', 'dynamically']
    }

    @Unroll
    void 'a cache the class\'s cache manager does not have is reported for that cache manager, compiled #mode'() {
        given: 'the class created as a bean beside its cache manager, which has no cache named missing'
        CacheManager cacheManager = new ConcurrentMapCacheManager('configured')
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean('grailsCacheManager', CacheManager, { -> cacheManager } as Supplier<CacheManager>)
        context.registerBean('service', (Class<Object>) compile(mode))
        context.refresh()
        def service = context.getBean('service')
        LogCapture log = new LogCapture(MissingCacheReporter, Level.WARN)

        when: 'the method is called with no cache manager, then with another one'
        List<Object> results = [service.uncached(1, null, null), service.uncached(1, new NoOpCacheManager(), null)]

        then: 'it ran uncached both times, and the class\'s cache manager was reported once'
        results == [2, 2]
        service.calls == 2
        log.events*.formattedMessage == ["ShadowingService does not cache with missing: its cache manager, ${ConcurrentMapCacheManager.name}, ".toString() +
                'has no cache of that name, so the methods run uncached. Configure the cache in that cache manager.']

        cleanup:
        log?.close()
        context?.close()

        where:
        mode << ['statically', 'dynamically']
    }

    private static Map<?, ?> nativeCache(CacheManager cacheManager) {
        (Map<?, ?>) cacheManager.getCache('configured').nativeCache
    }

    private static Class<?> compile(String mode) {
        new GroovyShell().evaluate("""
import grails.plugin.cache.*
import groovy.transform.CompileStatic
import org.springframework.cache.CacheManager

${mode == 'statically' ? '@CompileStatic' : ''}
class ShadowingService {
    int calls

    @Cacheable('configured')
    int cached(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
        x * 2
    }

    @CachePut('configured')
    int put(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
        x * 2
    }

    @Cacheable('missing')
    int uncached(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
        x * 2
    }

    @CacheEvict(value = 'configured', allEntries = true)
    void evictAll(CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
    }
}
return ShadowingService
""") as Class<?>
    }
}
