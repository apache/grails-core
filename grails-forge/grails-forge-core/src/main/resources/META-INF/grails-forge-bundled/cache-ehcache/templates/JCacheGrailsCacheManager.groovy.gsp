%{--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
--}%
package ${project.packageName}

import groovy.transform.CompileStatic

import javax.cache.CacheException
import javax.cache.CacheManager
import javax.cache.configuration.MutableConfiguration

import org.springframework.cache.Cache
import org.springframework.cache.jcache.JCacheCache
import org.springframework.cache.jcache.JCacheCacheManager

import org.grails.plugin.cache.GrailsCacheManager

/**
 * The cache manager of the Grails Cache plugin, grailsCacheManager in grails-app/conf/spring/resources.groovy:
 * the caches of a JCache (JSR-107) cache manager, which is Ehcache's with the caches of grails-app/conf/ehcache.xml.
 *
 * A cache it does not have is created when it is first asked for, as the cache manager the plugin has by default
 * creates one, so a name in an annotation needs no configuration: Ehcache gives it the template that
 * jsr107:defaults names in ehcache.xml. Its values are kept as they are, as in the caches of ehcache.xml, and not
 * copied by serialization, which the caches JCache creates otherwise do.
 */
@CompileStatic
class JCacheGrailsCacheManager extends JCacheCacheManager implements GrailsCacheManager {

    JCacheGrailsCacheManager(CacheManager cacheManager) {
        super(cacheManager)
    }

    @Override
    boolean cacheExists(String name) {
        cacheManager.getCache(name) != null
    }

    @Override
    boolean destroyCache(String name) {
        boolean exists = cacheExists(name)
        cacheManager.destroyCache(name)
        // forgets the cache that was destroyed, which is created again when it is next asked for
        initializeCaches()
        exists
    }

    @Override
    protected Cache getMissingCache(String name) {
        javax.cache.Cache<Object, Object> cache = cacheManager.getCache(name)
        if (cache == null) {
            try {
                cache = cacheManager.createCache(name, new MutableConfiguration<Object, Object>().setStoreByValue(false))
            } catch (CacheException ignored) {
                // another thread created it first
                cache = cacheManager.getCache(name)
            }
        }
        new JCacheCache(cache, allowNullValues)
    }
}
