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
import org.grails.plugin.cache.GrailsCacheManagerFallback
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
        context.addBeanFactoryPostProcessor(new GrailsCacheManagerFallback())
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

    @Unroll
    void 'a method reads its cache manager once a call, compiled #mode'() {
        given: 'a class whose cache manager is there at the first read of each call only'
        def service = compileReadCounting(mode).getDeclaredConstructor().newInstance()
        service.manager = new ConcurrentMapCacheManager('configured')
        LogCapture log = new LogCapture(MissingCacheReporter, Level.WARN)

        when: 'each annotation is used once'
        service.cached(1)
        service.cachedByKey(1)
        service.put(1)
        service.evict(1)
        service.evictAll()

        then: 'each ran, with one read of the cache manager, which the missing caches are reported for'
        service.calls == 5
        service.reads == 5
        log.events.size() == 5

        cleanup:
        log?.close()

        where:
        mode << ['statically', 'dynamically']
    }

    void 'two cache managers that are equal are each reported for a cache they do not have'() {
        given: 'two instances of the class, with distinct cache managers that are equal'
        Class<?> type = compileReadCounting('statically')
        def first = type.getDeclaredConstructor().newInstance()
        def second = type.getDeclaredConstructor().newInstance()
        first.manager = new EqualCacheManager()
        second.manager = new EqualCacheManager()
        LogCapture log = new LogCapture(MissingCacheReporter, Level.WARN)

        when:
        first.cached(1)
        second.cached(1)
        first.cached(1)

        then: 'once for each of them'
        log.events.size() == 2

        cleanup:
        log?.close()
    }

    @Unroll
    void 'a method whose parameters are named like what the generated code declares or reads still caches, compiled #mode'() {
        given: 'parameters named like each local the generated code declares, and like the cache manager and key generator'
        CacheManager cacheManager = new ConcurrentMapCacheManager('configured')
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.addBeanFactoryPostProcessor(new GrailsCacheManagerFallback())
        context.registerBean('grailsCacheManager', CacheManager, { -> cacheManager } as Supplier<CacheManager>)
        context.registerBean('service', (Class<Object>) compileNamedLikeTheGeneratedCode(mode))
        context.refresh()
        def service = context.getBean('service')
        CacheManager passed = new ConcurrentMapCacheManager('configured')

        when: 'each method is called twice'
        List<Object> results = [service.cached(1, 2, 3, 4, 5, 6, 7), service.cached(1, 2, 3, 4, 5, 6, 7),
                                service.shadowing(1, passed, null), service.shadowing(1, passed, null)]
        service.put(1, 2, 3, 4, 5)
        service.evict(1, 2, 3, 4, 5)

        then: 'they cached in the bean\'s cache manager, with the bean\'s key generator, and not the parameters\''
        results == [28, 28, 1, 1]
        service.calls == 4
        ((Map) passed.getCache('configured').nativeCache).isEmpty()

        cleanup:
        context?.close()

        where:
        mode << ['statically', 'dynamically']
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

    /** Parameters named like each local the generated code declares, and like the properties it reads. */
    private static Class<?> compileNamedLikeTheGeneratedCode(String mode) {
        new GroovyShell().evaluate("""
import grails.plugin.cache.*
import groovy.transform.CompileStatic
import org.springframework.cache.CacheManager

${mode == 'statically' ? '@CompileStatic' : ''}
class NamedLikeTheGeneratedCode {
    int calls

    @Cacheable('configured')
    int cached(int \$_cache_cacheManager, int \$_cache_cacheVariable, int \$_cache_cacheKey, int \$_method_parameter_map,
               int \$_cache_valueWrapper, int \$_cache_originalMethodReturnValue, int \$_cache_cacheManager_) {
        calls++
        \$_cache_cacheManager + \$_cache_cacheVariable + \$_cache_cacheKey + \$_method_parameter_map +
                \$_cache_valueWrapper + \$_cache_originalMethodReturnValue + \$_cache_cacheManager_
    }

    @Cacheable('configured')
    int shadowing(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
        x
    }

    @CachePut('configured')
    int put(int \$_cache_cacheManager, int \$_cache_cacheVariable, int \$_cache_cacheKey, int \$_method_parameter_map,
            int \$_cache_originalMethodReturnValue) {
        calls++
        0
    }

    @CacheEvict('configured')
    void evict(int \$_cache_cacheManager, int \$_cache_cacheVariable, int \$_cache_cacheKey, int \$_method_parameter_map,
               int \$_cache_originalMethodReturnValue) {
        calls++
    }
}
return NamedLikeTheGeneratedCode
""") as Class<?>
    }

    /** A class whose cache manager is the one set, at the first read of each call only, which counts its reads. */
    private static Class<?> compileReadCounting(String mode) {
        new GroovyShell().evaluate("""
import grails.plugin.cache.*
import groovy.transform.CompileStatic
import org.springframework.cache.CacheManager

${mode == 'statically' ? '@CompileStatic' : ''}
class ReadCountingService {
    int calls
    int reads
    CacheManager manager
    private int readsThisCall

    @Override
    CacheManager getGrailsCacheManager() {
        reads++
        readsThisCall++
        readsThisCall == 1 ? manager : null
    }

    private void called() {
        calls++
        readsThisCall = 0
    }

    @Cacheable('missingCached')
    int cached(int x) { called(); x * 2 }

    @Cacheable(value = 'missingKeyed', key = { x })
    int cachedByKey(int x) { called(); x * 2 }

    @CachePut('missingPut')
    int put(int x) { called(); x * 2 }

    @CacheEvict('goneEvict')
    void evict(int x) { called() }

    @CacheEvict(value = 'goneAll', allEntries = true)
    void evictAll() { called() }
}
return ReadCountingService
""") as Class<?>
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
