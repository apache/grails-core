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
package grails.plugin.springsecurity.rest.token.storage.memcached

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import net.spy.memcached.CASValue
import net.spy.memcached.MemcachedClient
import net.spy.memcached.util.StringUtils

import org.springframework.security.core.userdetails.UserDetails

import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService

/**
 * Stores and retrieves tokens in a memcached server. This implementation stores the whole {@link UserDetails} object
 * in memcached, leveraging it is serializable.
 */
@Slf4j
@CompileStatic
class MemcachedTokenStorageService implements TokenStorageService {

    MemcachedClient memcachedClient

    /** Expiration in seconds */
    Integer expiration = 3600

    UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Finding token ${tokenValue} in memcached"
        def userDetails = findExistingUserDetails(tokenValue)
        if (userDetails) {
            return userDetails
        } else {
            throw new TokenNotFoundException("Token ${tokenValue} not found")
        }
    }

    void storeToken(String tokenValue, UserDetails principal) {
        log.debug "Storing principal for token: ${tokenValue} with expiration of ${expiration} seconds"
        log.debug "Principal: ${principal}"

        memcachedClient.set tokenValue, expiration, principal
    }

    void removeToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Removing token ${tokenValue} from memcached"
        def userDetails = findExistingUserDetails(tokenValue)
        if (userDetails) {
            memcachedClient.delete tokenValue
        } else {
            throw new TokenNotFoundException("Token ${tokenValue} not found")
        }
    }

    @SuppressWarnings('GroovyVariableNotAssigned')
    private UserDetails findExistingUserDetails(String tokenValue) {
        log.debug "Searching in Memcached for UserDetails of token ${tokenValue}"
        if (!isKey(tokenValue)) {
            log.debug 'The token cannot be a key of memcached, so no UserDetails are stored for it'
            return null
        }
        CASValue<Object> result = memcachedClient.getAndTouch(tokenValue, expiration)
        UserDetails userDetails
        if (result) {
            userDetails = result.getValue() as UserDetails
            log.debug "UserDetails found: ${userDetails}"
        } else {
            log.debug 'UserDetails not found'
        }
        return userDetails
    }

    /**
     * Whether memcached accepts the token as a key. The token of a request is whatever the caller sent, and the client
     * throws for a key that is empty or longer than 250 bytes; no principal was ever stored under such a token. The
     * keys are checked by the rules of the binary protocol, which the plugin configures the client with: a space or a
     * line break is a valid key there, so a token that holds one can be stored and is found.
     */
    private static boolean isKey(String tokenValue) {
        try {
            StringUtils.validateKey(tokenValue, true)
            return true
        } catch (IllegalArgumentException ignored) {
            return false
        }
    }
}
