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
package grails.plugin.springsecurity.cache

import javax.cache.CacheManager
import javax.cache.Caching

import org.springframework.cache.jcache.JCacheCacheManager
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserCache
import spock.lang.Specification

import grails.plugin.springsecurity.userdetails.GrailsUser

class SpringUserCacheFactoryBeanSpec extends Specification {

    CacheManager jcache = Caching.cachingProvider.cacheManager

    String cacheName = "userCache-${System.nanoTime()}"

    UserCache cache = new SpringUserCacheFactoryBean(cacheManager: new JCacheCacheManager(jcache), cacheName: cacheName)
            .tap { afterPropertiesSet() }
            .object

    void cleanup() {
        jcache.destroyCache(cacheName)
    }

    void 'erasing the user a lookup returns leaves the cached one with its password'() {
        given:
            cache.putUserInCache(new GrailsUser('user', 'stored', true, true, true, true,
                    [new SimpleGrantedAuthority('ROLE_USER')], 42L))

        when: 'what the authentication manager does to the user it signs in'
            User signedIn = (User) cache.getUserFromCache('user')
            signedIn.eraseCredentials()

        then:
            signedIn.password == null
            cache.getUserFromCache('user').password == 'stored'

        and: 'the copy is of the class that was cached, with what it carries'
            GrailsUser copy = (GrailsUser) cache.getUserFromCache('user')
            copy.id == 42L
            copy.authorities*.authority == ['ROLE_USER']
            !copy.is(cache.getUserFromCache('user'))
    }

    void 'a user that cannot be copied is left out of the cache rather than failing the sign-in'() {
        when:
            cache.putUserInCache(new HoldsSomethingUnserializable('user', 'stored'))

        then:
            noExceptionThrown()
            cache.getUserFromCache('user') == null
    }

    void 'a user that is removed is no longer cached'() {
        given:
            cache.putUserInCache(new User('user', 'stored', []))

        when:
            cache.removeUserFromCache('user')

        then:
            cache.getUserFromCache('user') == null
    }

    static class HoldsSomethingUnserializable extends User {

        final Object held = new Object()

        HoldsSomethingUnserializable(String username, String password) {
            super(username, password, [])
        }
    }
}
