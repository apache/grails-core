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
package grails.plugin.springsecurity.rest.token.storage

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import org.springframework.security.core.userdetails.User

import grails.plugin.cache.GrailsConcurrentLinkedMapCacheManager
import grails.plugin.cache.GrailsConcurrentMapCacheManager
import org.grails.plugin.cache.GrailsCacheManager

class GrailsCacheTokenStorageServiceSpec extends Specification {

    private service

    def setup() {
        service = newService(new GrailsConcurrentMapCacheManager())
    }

    void "store a principal for a given token"() {
        given:
        def tokenValue = '12345'
        def principal = new User('foo', 'bar', [])

        when:
        service.storeToken(tokenValue, principal)

        then:
        service.cache.get(tokenValue).get() == principal
    }

    void "load a principal by a given token"() {
        given:
        def tokenValue = '12345'
        def givenPrincipal = new User('foo', 'bar', [])
        service.storeToken(tokenValue, givenPrincipal)

        when:
        def loadedPrincipal = service.loadUserByToken(tokenValue)

        then:
        loadedPrincipal == givenPrincipal
    }

    void "throw token not found exception if cannot load a principal for a given token"() {
        when:
        service.loadUserByToken('token-does-not-exists')

        then:
        thrown(TokenNotFoundException)
    }

    void "remove a principal for a given token"() {
        given:
        def tokenValue = '12345'
        def givenPrincipal = new User('foo', 'bar', [])
        service.storeToken(tokenValue, givenPrincipal)

        when:
        service.removeToken(tokenValue)

        then:
        !service.cache.get(tokenValue)
    }

    void "throw token not found exception if there is no principal to remove for a given token"() {
        when:
        service.removeToken('token-does-not-exists')

        then:
        thrown(TokenNotFoundException)
    }

    @Unroll
    void "a token is removed once from a cache of the #cacheManager.class.simpleName"() {
        given:
        def storage = newService(cacheManager)
        storage.storeToken('12345', new User('foo', 'bar', []))

        when:
        storage.removeToken('12345')

        then:
        !storage.cache.get('12345')

        when:
        storage.removeToken('12345')

        then:
        thrown(TokenNotFoundException)

        where:
        cacheManager << [new GrailsConcurrentMapCacheManager(), new GrailsConcurrentLinkedMapCacheManager()]
    }

    void "a token is evicted without a lookup from a cache that implements evictIfPresent"() {
        given:
        Cache cache = Mock(Cache)
        def storage = newService(cacheManagerOf(cache))

        when:
        storage.removeToken('12345')

        then: 'the cache is asked once, and reports that it did not hold the token'
        1 * cache.evictIfPresent('12345') >> false
        0 * cache._
        thrown(TokenNotFoundException)
    }

    @Unroll
    void "a token is removed once from a cache that #description"() {
        given:
        def storage = newService(cacheManagerOf(cache))
        storage.storeToken('12345', new User('foo', 'bar', []))

        when:
        storage.removeToken('12345')

        then:
        !storage.cache.get('12345')

        when:
        storage.removeToken('12345')

        then:
        thrown(TokenNotFoundException)

        when:
        storage.removeToken('token-does-not-exist')

        then:
        thrown(TokenNotFoundException)

        where:
        description                         | cache
        'implements evictIfPresent'         | new EvictIfPresentMapCache()
        'does not implement evictIfPresent' | new MapCache()
    }

    @Unroll
    void "a cache that #description is #warning"() {
        given:
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        Logger logger = (Logger) LoggerFactory.getLogger(GrailsCacheTokenStorageService)
        logger.addAppender(appender)
        logger.level = Level.WARN

        when: 'the token storage starts, and two tokens are stored and removed'
        def storage = newService(cacheManagerOf(cache))
        ['12345', '67890'].each { String token ->
            storage.storeToken(token, new User('foo', 'bar', []))
            storage.removeToken(token)
        }

        then:
        def warnings = appender.list.findAll { it.level == Level.WARN }*.formattedMessage
        warnings.size() == count
        warnings.every { String message ->
            message.startsWith("The cache 'tokenStorage' (${MapCache.name}) does not implement Cache.evictIfPresent")
        }

        cleanup:
        logger.detachAppender(appender)
        logger.level = null

        where:
        description                         | cache                        || warning             | count
        'implements evictIfPresent'         | new EvictIfPresentMapCache() || 'not warned about'  | 0
        'does not implement evictIfPresent' | new MapCache()               || 'warned about once' | 1
    }

    @Unroll
    void "of concurrent removals of one token from a cache of the #cacheManager.class.simpleName, one removes it"() {
        given:
        def storage = newService(cacheManager)
        int removals = 4
        ExecutorService executor = Executors.newFixedThreadPool(removals)

        when: 'each of several tokens is removed by concurrent removals'
        List<Integer> removedCounts = (1..20).collect { int round ->
            String token = "token-${round}"
            storage.storeToken(token, new User('foo', 'bar', []))
            CountDownLatch start = new CountDownLatch(1)
            List<Future<Boolean>> results = (1..removals).collect {
                executor.submit({
                    start.await()
                    removal(storage, token).call()
                } as Callable<Boolean>)
            }
            start.countDown()
            results.count { Future<Boolean> result -> result.get() } as Integer
        }

        then: 'exactly one removal of each token removed it, and the others did not find it'
        removedCounts.every { it == 1 }

        cleanup:
        executor?.shutdownNow()

        where:
        cacheManager << [new GrailsConcurrentMapCacheManager(), new GrailsConcurrentLinkedMapCacheManager()]
    }

    void "of two concurrent removals of one token from a cache that does not implement evictIfPresent, one removes it"() {
        given: 'a cache that holds its first look-up of the token, after the look-up, until it is released'
        def cache = new HoldingMapCache()
        def storage = newService(cacheManagerOf(cache))
        storage.storeToken('12345', new User('foo', 'bar', []))
        FutureTask<Boolean> first = new FutureTask<>(removal(storage, '12345'))
        FutureTask<Boolean> second = new FutureTask<>(removal(storage, '12345'))
        Thread secondThread = new Thread(second)

        when: 'the first removal starts'
        new Thread(first).start()

        then: 'it has looked the token up'
        cache.firstLookUp.await(30, TimeUnit.SECONDS)

        when: 'the second removal starts'
        secondThread.start()

        then: 'it looks the token up as well, or waits to'
        new PollingConditions(timeout: 30).eventually {
            assert cache.lookUps.get() == 2 || secondThread.state in [Thread.State.BLOCKED, Thread.State.WAITING]
        }

        when: 'the first look-up is released'
        cache.release.countDown()

        then: 'the first removal removed the token, and the second did not find it'
        first.get(30, TimeUnit.SECONDS)
        !second.get(30, TimeUnit.SECONDS)

        cleanup:
        cache.release.countDown()
    }

    void "throw illegal state exeption if grails cache manager was not injected"() {
        given:
        service.grailsCacheManager = null

        when:
        service.init()

        then:
        thrown(IllegalStateException)
    }

    void "throw illegal state exeption if cache name was not injected"() {
        given:
        service.cacheName = null

        when:
        service.init()

        then:
        thrown(IllegalStateException)
    }

    void "throw illegal state exeption if cache was not retrieved"() {
        given:
        service.grailsCacheManager = Mock(GrailsCacheManager)
        1 * service.grailsCacheManager.getCache(service.cacheName) >> null

        when:
        service.init()

        then:
        thrown(IllegalStateException)
    }

    private static GrailsCacheTokenStorageService newService(GrailsCacheManager cacheManager) {
        def storage = new GrailsCacheTokenStorageService(grailsCacheManager: cacheManager, cacheName: 'tokenStorage')
        storage.init()
        storage
    }

    private static Callable<Boolean> removal(GrailsCacheTokenStorageService storage, String token) {
        { ->
            try {
                storage.removeToken(token)
                return true
            } catch (TokenNotFoundException ignored) {
                return false
            }
        } as Callable<Boolean>
    }

    private GrailsCacheManager cacheManagerOf(Cache cache) {
        Stub(GrailsCacheManager) {
            getCache('tokenStorage') >> cache
        }
    }

    /**
     * A cache that leaves {@link Cache#evictIfPresent} to its default, which evicts the key and reports that the
     * cache did not hold it.
     */
    private static class MapCache implements Cache {

        protected final Map<Object, Object> store = new ConcurrentHashMap<>()

        @Override
        String getName() {
            'tokenStorage'
        }

        @Override
        Object getNativeCache() {
            store
        }

        @Override
        Cache.ValueWrapper get(Object key) {
            Object value = store.get(key)
            value == null ? null : new SimpleValueWrapper(value)
        }

        @Override
        <T> T get(Object key, Class<T> type) {
            type.cast(store.get(key))
        }

        @Override
        <T> T get(Object key, Callable<T> valueLoader) {
            (T) store.computeIfAbsent(key) { valueLoader.call() }
        }

        @Override
        void put(Object key, Object value) {
            store.put(key, value)
        }

        @Override
        void evict(Object key) {
            store.remove(key)
        }

        @Override
        void clear() {
            store.clear()
        }
    }

    private static class EvictIfPresentMapCache extends MapCache {

        @Override
        boolean evictIfPresent(Object key) {
            store.remove(key) != null
        }
    }

    /**
     * A cache that leaves {@link Cache#evictIfPresent} to its default, and holds its first look-up, after the key has
     * been looked up, until it is released.
     */
    private static class HoldingMapCache extends MapCache {

        final AtomicInteger lookUps = new AtomicInteger()
        final CountDownLatch firstLookUp = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        @Override
        Cache.ValueWrapper get(Object key) {
            Cache.ValueWrapper value = super.get(key)
            if (lookUps.incrementAndGet() == 1) {
                firstLookUp.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
            value
        }
    }
}
