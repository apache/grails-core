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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import jakarta.annotation.PostConstruct

import org.springframework.cache.Cache
import org.springframework.security.core.userdetails.UserDetails

import org.grails.plugin.cache.GrailsCacheManager

/**
 * Uses <a href="http://grails.org/plugin/cache">Grails Cache plugin</a> to store and retrieve tokens.
 */
@Slf4j
@CompileStatic
class GrailsCacheTokenStorageService implements TokenStorageService {

    GrailsCacheManager grailsCacheManager
    String cacheName

    Cache cache

    private boolean cacheReportsEviction

    private final Object lookUpAndEvictLock = new Object()

    /**
     * Sets the cache that holds the tokens. A cache that does not implement {@link Cache#evictIfPresent}, whose
     * default evicts the key and reports that the cache did not hold it, is asked for the token before the token is
     * removed, one removal at a time, and a warning says so.
     */
    void setCache(Cache cache) {
        this.cache = cache
        cacheReportsEviction = cache == null || reportsEviction(cache)
        if (!cacheReportsEviction) {
            log.warn("The cache '${cache.name}' (${cache.getClass().name}) does not implement Cache.evictIfPresent, " +
                    'so a token is looked up before it is removed, one removal at a time in this application. ' +
                    'Two instances of the application that share the cache can both succeed in logging out the ' +
                    'same token at the same time')
        }
    }

    @Override
    void storeToken(String tokenValue, UserDetails principal) {
        cache.put(tokenValue, principal)
        log.debug "Stored principal $principal for token ${tokenValue}"
    }

    @Override
    UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException {
        def principal = cache.get(tokenValue)?.get()
        if (principal) {
            log.debug "Got principal $principal for token ${tokenValue}"
            return principal as UserDetails
        }
        def tokenNotFoundMsg = "No principal found for token $tokenValue"
        log.debug tokenNotFoundMsg
        throw new TokenNotFoundException(tokenNotFoundMsg)
    }

    /**
     * Removes the token in one step with {@link Cache#evictIfPresent}, so of two removals of the same token only the
     * one that removed it returns, and the other throws {@link TokenNotFoundException}. Both caches of the Grails
     * cache plugin implement it. With a cache that does not, the token is looked up and then evicted, one removal at
     * a time, so the same holds for removals through this service. Two instances of the application that share such
     * a cache do not wait for each other, so removals of the same token at the same time in each of them can both
     * return.
     */
    @Override
    void removeToken(String tokenValue) throws TokenNotFoundException {
        boolean removed = cacheReportsEviction ? cache.evictIfPresent(tokenValue) : lookUpAndEvict(tokenValue)
        if (!removed) {
            def tokenNotFoundMsg = "No principal found for token $tokenValue"
            log.debug tokenNotFoundMsg
            throw new TokenNotFoundException(tokenNotFoundMsg)
        }
        log.debug "Removed principal for token ${tokenValue}"
    }

    private boolean lookUpAndEvict(String tokenValue) {
        synchronized (lookUpAndEvictLock) {
            if (cache.get(tokenValue) == null) {
                return false
            }
            cache.evict(tokenValue)
            return true
        }
    }

    private static boolean reportsEviction(Cache cache) {
        cache.getClass().getMethod('evictIfPresent', Object).declaringClass != Cache
    }

    @PostConstruct
    void init() {
        if (!grailsCacheManager) {
            throw new IllegalStateException('GrailsCacheManager was not injected. ' +
                    'Install cache plugin to use this implementation of TokenStorageService')
        }
        if (!cacheName) {
            throw new IllegalStateException('Cache name for TokenStorageService was not injected. ' +
                    'Use grails.plugin.springsecurity.rest.token.storage.grailsCacheName to specify a cache name')
        }
        setCache(grailsCacheManager.getCache(cacheName))
        if (!cache) {
            throw new IllegalStateException("Could not retrieve a cache for name $cacheName. " +
                    "Did you specify a cache '$cacheName' in the cache configuration?")
        }
        log.debug "${this.class.simpleName} initialized successfully"
    }
}
