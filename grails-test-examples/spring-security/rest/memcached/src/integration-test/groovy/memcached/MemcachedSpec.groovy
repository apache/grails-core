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

package memcached

import net.spy.memcached.MemcachedClient
import rest.AbstractRestSpec
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails

import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.plugin.springsecurity.rest.token.storage.memcached.MemcachedTokenStorageService
import grails.testing.mixin.integration.Integration

/**
 * The memcached client and token storage of the application, against a real memcached server.
 */
@Integration
@Requires({ MemcachedContainerExtension.dockerAvailable })
class MemcachedSpec extends AbstractRestSpec {

    @Autowired
    MemcachedClient memcachedClient

    @Autowired
    MemcachedTokenStorageService tokenStorageService

    Integer configuredExpiration

    void setup() {
        configuredExpiration = tokenStorageService.expiration
    }

    void cleanup() {
        tokenStorageService.expiration = configuredExpiration
    }

    void "a token that is too long to be a key of memcached is refused"() {
        expect:
        get('/api/validate', ['X-Auth-Token': 'a' * 251]).statusCode() == 401
    }

    @Unroll
    void "Memcached connection works for storing #key's"() {
        when:
        memcachedClient.set(key, 3600, object).get()

        then:
        memcachedClient.get(key) == object

        where:
        key      | object
        'String' | 'My cool string value'
        'Date'   | new Date()
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/86')
    void "Objects stored expire after the expiration time"() {
        given: 'a token stored for one second'
        tokenStorageService.expiration = 1
        UserDetails principal = new User('username', 'password', [])
        String token = 'abcd' + System.currentTimeMillis()
        tokenStorageService.storeToken(token, principal)

        expect: 'it is not found once that second has passed'
        // A read that still finds the token keeps it for another second, so the reads are three seconds apart; a busy
        // machine only makes them further apart. The minute bounds a token that never expires.
        new PollingConditions(timeout: 60, initialDelay: 3, delay: 3).eventually {
            !isFound(token)
        }
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/86')
    void "Objects are refreshed when accessed"() {
        given: 'a token stored for 30 seconds'
        tokenStorageService.expiration = 30
        UserDetails principal = new User('username', 'password', [])
        String token = 'efgh' + System.currentTimeMillis()
        tokenStorageService.storeToken(token, principal)

        when: 'it is read at once, while the expiration is an hour'
        tokenStorageService.expiration = 3600
        UserDetails details = tokenStorageService.loadUserByToken(token)

        then: 'it is found'
        details

        when: 'it is read again 40 seconds later, when the 30 seconds it was stored for are over'
        // The token was stored before the read above reached memcached, whose client sends the commands in order, so
        // without that read it would be gone now. A busy machine only makes this read later, well within the hour.
        sleep(40_000)
        tokenStorageService.loadUserByToken(token)

        then: 'it is still found: the first read keeps it for an hour from then'
        notThrown(TokenNotFoundException)
    }

    private boolean isFound(String token) {
        try {
            tokenStorageService.loadUserByToken(token)
            return true
        } catch (TokenNotFoundException ignored) {
            return false
        }
    }
}
