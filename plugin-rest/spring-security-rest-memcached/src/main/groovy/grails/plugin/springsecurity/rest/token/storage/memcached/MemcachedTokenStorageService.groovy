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

import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import net.spy.memcached.CASValue
import net.spy.memcached.MemcachedClient
import org.springframework.security.core.userdetails.UserDetails

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

    @SuppressWarnings("GroovyVariableNotAssigned")
    private UserDetails findExistingUserDetails(String tokenValue) {
        log.debug "Searching in Memcached for UserDetails of token ${tokenValue}"
        CASValue<Object> result = memcachedClient.getAndTouch(tokenValue, expiration)
        UserDetails userDetails
        if (result) {
            userDetails = result.getValue() as UserDetails
            log.debug "UserDetails found: ${userDetails}"
        } else {
            log.debug "UserDetails not found"
        }
        return userDetails
    }
}
