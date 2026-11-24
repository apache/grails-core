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

import java.util.concurrent.CountDownLatch
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

        when: 'every annotation is used twice, each with a missing cache of its own'
        List<Object> results = [service.cached(1), service.cached(1), service.cachedByKey(1), service.cachedByKey(1),
                                service.put(1), service.put(1)]
        service.evict(1)
        service.evict(1)
        service.evictAll()
        service.evictAll()

        then: 'each method ran every time, and answered as it does uncached'
        results == [2, 2, 2, 2, 2, 2]
        service.calls == 10

        and: 'no key was made for a cache that is missing'
        service.getClass().getField('keysMade').get(null) == 0

        and: 'each missing cache is reported once, by each annotation'
        log.events*.formattedMessage == ['missingCached', 'missingKeyed', 'missingPut', 'goneEvict', 'goneAll'].collect { String name ->
            "CachedService does not cache with ${name}: its cache manager, ${ConcurrentMapCacheManager.name}, ".toString() +
                    'has no cache of that name, so the methods run uncached. Configure the cache in that cache manager.'
        }

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

    void 'two cache managers that are equal are each reported for a cache they do not have'() {
        given: 'two distinct cache managers that are equal'
        CacheManager first = new EqualCacheManager()
        CacheManager second = new EqualCacheManager()
        LogCapture log = new LogCapture(MissingCacheReporter, Level.WARN)

        when:
        MissingCacheReporter.report(first, 'missing', MissingCacheSpec)
        MissingCacheReporter.report(second, 'missing', MissingCacheSpec)
        MissingCacheReporter.report(first, 'missing', MissingCacheSpec)

        then: 'once for each of them'
        log.events.size() == 2

        cleanup:
        log?.close()
    }

    void 'a missing cache is reported once when the first calls for it come at once'() {
        given:
        LogCapture log = new LogCapture(MissingCacheReporter, Level.WARN)
        int managers = 300

        when: 'eight threads report each new cache manager\'s missing cache at the same moment'
        managers.times {
            CacheManager cacheManager = new ConcurrentMapCacheManager()
            CountDownLatch start = new CountDownLatch(1)
            List<Thread> threads = (1..8).collect {
                Thread.start {
                    start.await()
                    MissingCacheReporter.report(cacheManager, 'raced', MissingCacheSpec)
                }
            }
            start.countDown()
            threads*.join()
        }

        then: 'each was reported once'
        log.events.size() == managers

        cleanup:
        log?.close()
    }

    /** A cache manager equal to every other of its class. */
    static class EqualCacheManager extends ConcurrentMapCacheManager {

        EqualCacheManager() {
            super('configured')
        }

        @Override
        boolean equals(Object other) {
            other instanceof EqualCacheManager
        }

        @Override
        int hashCode() {
            1
        }
    }

    private static Class<?> compile(String mode) {
        new GroovyShell().evaluate("""
import grails.plugin.cache.*
import groovy.transform.CompileStatic

${mode == 'statically' ? '@CompileStatic' : ''}
class CachedService {
    int calls

    /** Keys made by a key closure. */
    public static int keysMade

    static Object made(Object key) {
        keysMade++
        key
    }

    private void called() {
        calls++
    }

    @Cacheable('missingCached')
    int cached(int x) { called(); x * 2 }

    @Cacheable(value = 'missingKeyed', key = { CachedService.made(x) })
    int cachedByKey(int x) { called(); x * 2 }

    @Cacheable('configured')
    int cachedWhereConfigured(int x) { called(); x * 3 }

    @Cacheable(value = 'configured', key = { x + 100 })
    int cachedByKeyWhereConfigured(int x) { called(); x * 4 }

    @CachePut('missingPut')
    int put(int x) { called(); x * 2 }

    @CacheEvict('goneEvict')
    void evict(int x) { called() }

    @CacheEvict(value = 'goneAll', allEntries = true)
    void evictAll() { called() }
}
return CachedService
""") as Class<?>
    }
}
