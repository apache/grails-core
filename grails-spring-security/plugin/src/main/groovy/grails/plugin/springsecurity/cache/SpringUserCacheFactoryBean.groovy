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

import javax.cache.configuration.Configuration
import javax.cache.configuration.MutableConfiguration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.cache.jcache.JCacheCache
import org.springframework.cache.jcache.JCacheCacheManager
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.cache.SpringCacheBasedUserCache
import org.springframework.util.Assert

/**
 * The {@code userCache} the plugin defines with {@code cacheUsers}.
 *
 * <p>Users are stored by value, so each lookup is given a copy. The authentication manager erases the credentials of
 * the user it signs in by default, and a user stored by reference was erased in the cache too: the next sign-in, and
 * with Digest authentication the next request, then missed the cache and loaded the user again. With a copy, what is
 * signed in is erased and what is cached keeps what it is checked against.</p>
 *
 * <p>A user is copied by serializing it, as {@code UserDetails} is {@code Serializable}. One that cannot be serialized
 * is not cached, and is loaded on every sign-in. A {@code cacheConfig} set on the bean is used as it is.</p>
 */
@CompileStatic
class SpringUserCacheFactoryBean implements FactoryBean<SpringCacheBasedUserCache>, InitializingBean {

    JCacheCacheManager cacheManager
    String cacheName
    Configuration cacheConfig
    private SpringCacheBasedUserCache springUserCache

    @Override
    SpringCacheBasedUserCache getObject() throws Exception {
        springUserCache
    }

    @Override
    Class<?> getObjectType() {
        SpringCacheBasedUserCache
    }

    @Override
    void afterPropertiesSet() throws Exception {
        Assert.notNull(cacheManager, 'cacheManager mandatory')
        Assert.notNull(cacheName, 'cacheName mandatory')
        if (!cacheConfig) {
            cacheConfig = new MutableConfiguration<String, User>()
                    .setTypes(String, User)
                    .setStoreByValue(true)
        }
        springUserCache = new CopyingUserCache(new JCacheCache(cacheManager.cacheManager.createCache(cacheName, cacheConfig)))
    }

    /** Leaves a user that cannot be stored out of the cache, rather than failing the sign-in that loaded it. */
    @Slf4j
    @CompileStatic
    private static class CopyingUserCache extends SpringCacheBasedUserCache {

        CopyingUserCache(JCacheCache cache) {
            super(cache)
        }

        @Override
        void putUserInCache(UserDetails user) {
            try {
                super.putUserInCache(user)
            }
            catch (RuntimeException e) {
                log.warn('User {} was not cached, as it could not be copied into the user cache: {}', user.username, e.message)
            }
        }
    }
}
