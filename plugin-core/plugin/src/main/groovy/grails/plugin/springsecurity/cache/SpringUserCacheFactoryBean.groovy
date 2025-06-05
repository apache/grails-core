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

import groovy.transform.CompileStatic
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.cache.jcache.JCacheCache
import org.springframework.cache.jcache.JCacheCacheManager
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.cache.SpringCacheBasedUserCache
import org.springframework.util.Assert

import javax.cache.configuration.Configuration
import javax.cache.configuration.MutableConfiguration

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
        Assert.notNull(cacheManager, "cacheManager mandatory")
        Assert.notNull(cacheName, "cacheName mandatory")
        if (!cacheConfig) {
            cacheConfig = new MutableConfiguration<String, User>()
                    .setTypes(String, User)
                    .setStoreByValue(false)
        }
        springUserCache = new SpringCacheBasedUserCache(new JCacheCache(cacheManager.cacheManager.createCache(cacheName, cacheConfig)))
    }
}
