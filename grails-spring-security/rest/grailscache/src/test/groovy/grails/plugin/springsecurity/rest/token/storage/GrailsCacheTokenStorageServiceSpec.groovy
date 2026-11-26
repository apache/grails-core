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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

import spock.lang.Specification
import spock.lang.Unroll

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

    void "throw token not found exception if another removal evicted the token first"() {
        given: 'a cache that holds the token when it is looked up, but no longer when it is evicted'
        Cache cache = Stub(Cache) {
            get('12345') >> new SimpleValueWrapper(new User('foo', 'bar', []))
            evictIfPresent('12345') >> false
        }
        def storage = newService(Stub(GrailsCacheManager) {
            getCache('tokenStorage') >> cache
        })

        when:
        storage.removeToken('12345')

        then:
        thrown(TokenNotFoundException)
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
                    try {
                        storage.removeToken(token)
                        return true
                    } catch (TokenNotFoundException ignored) {
                        return false
                    }
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
}
