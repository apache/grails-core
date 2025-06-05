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
import org.grails.plugin.cache.GrailsCacheManager
import org.springframework.cache.Cache
import org.springframework.security.core.userdetails.UserDetails

import jakarta.annotation.PostConstruct

/**
 * Uses <a href="http://grails.org/plugin/cache">Grails Cache plugin</a> to store and retrieve tokens.
 */
@Slf4j
@CompileStatic
class GrailsCacheTokenStorageService implements TokenStorageService {

    GrailsCacheManager grailsCacheManager
    String cacheName

    Cache cache

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

    @Override
    void removeToken(String tokenValue) throws TokenNotFoundException {
        cache.evict(tokenValue)
        log.debug "Removed principal for token ${tokenValue}"
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
        cache = grailsCacheManager.getCache(cacheName)
        if (!cache) {
            throw new IllegalStateException("Could not retrieve a cache for name $cacheName. " +
                "Did you specify a cache '$cacheName' in the cache configuration?")
        }
        log.debug "${this.class.simpleName} initialized successfully"
    }
}
