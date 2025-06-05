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

import grails.plugin.cache.GrailsConcurrentMapCacheManager
import org.grails.plugin.cache.GrailsCacheManager
import org.springframework.security.core.userdetails.User
import spock.lang.Specification

class GrailsCacheTokenStorageServiceSpec extends Specification {

    private service

    def setup() {
        service = new GrailsCacheTokenStorageService(grailsCacheManager: new GrailsConcurrentMapCacheManager(), cacheName: 'tokenStorage')
        service.init()
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
}
