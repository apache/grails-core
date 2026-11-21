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

package redis

import rest.AbstractRestSpec

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails

import grails.plugin.springsecurity.rest.token.storage.RedisTokenStorageService
import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.testing.mixin.integration.Integration

/**
 * The Redis token storage of the application, against a real Redis server.
 */
@Integration
class RedisSpec extends AbstractRestSpec {

    @Autowired
    RedisTokenStorageService tokenStorageService

    Integer configuredExpiration

    void setup() {
        configuredExpiration = tokenStorageService.expiration
    }

    void cleanup() {
        tokenStorageService.expiration = configuredExpiration
    }

    void "Objects stored expire after the expiration time"() {
        given:
        tokenStorageService.expiration = 1
        UserDetails principal = new User('username', 'password', [])
        String token = 'abcd' + System.currentTimeMillis()
        tokenStorageService.storeToken(token, principal)
        sleep(2500)

        when:
        tokenStorageService.loadUserByToken(token)

        then:
        thrown(TokenNotFoundException)
    }

    void "Objects are refreshed when accessed"() {
        given:
        tokenStorageService.expiration = 3
        UserDetails principal = new User('username', 'password', [])
        String token = 'efgh' + System.currentTimeMillis()
        tokenStorageService.storeToken(token, principal)
        sleep(2000)

        when: 'it is accessed within the expiration time'
        UserDetails details = tokenStorageService.loadUserByToken(token)

        then: 'it is found, and its expiration time is three seconds again'
        details

        when: 'it is accessed two seconds later, four seconds after it was stored'
        sleep(2000)
        tokenStorageService.loadUserByToken(token)

        then: 'it is still found'
        notThrown(TokenNotFoundException)
    }
}
