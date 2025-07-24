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

package grails.plugin.cache

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.cache.Cache

import grails.plugins.Plugin
import org.grails.plugin.cache.GrailsCacheManager

@Slf4j
class CacheGrailsPlugin extends Plugin {

    def grailsVersion = "7.0.0 > *"
    def observe = ['controllers', 'services']
    def loadAfter = ['controllers', 'services']
    def authorEmail = 'brownj@objectcomputing.com'
    def description = 'Grails Cache Plugin'

    def pluginExcludes = [
            '**/com/demo/**',
            'grails-app/views/**',
            '**/*.gsp'
    ]

    private boolean isCachingEnabled() {
        config.getProperty('grails.cache.enabled', Boolean, true)
    }

    Closure doWithSpring() {
        { ->
            if (!cachingEnabled) {
                log.warn 'Cache plugin is disabled'
                return
            }

            customCacheKeyGenerator(CustomCacheKeyGenerator)


            Class<? extends GrailsCacheManager> cacheClazz = GrailsConcurrentMapCacheManager
            // Selects cache manager from config
            if (config.getProperty("grails.cache.cacheManager", String, null) == "GrailsConcurrentLinkedMapCacheManager") {
                cacheClazz = GrailsConcurrentLinkedMapCacheManager
            }

            grailsCacheManager(cacheClazz) {
                configuration = ref('grailsCacheConfiguration')
            }
            grailsCacheAdminService(GrailsCacheAdminService)
            grailsCacheConfiguration(CachePluginConfiguration)
        }
    }

    @CompileStatic
    void doWithApplicationContext() {
        if (cachingEnabled) {
            CachePluginConfiguration pluginConfiguration = applicationContext.getBean('grailsCacheConfiguration', CachePluginConfiguration)
            GrailsCacheManager grailsCacheManager = applicationContext.getBean('grailsCacheManager', GrailsCacheManager)

            if (pluginConfiguration.clearAtStartup) {
                for (String cacheName in grailsCacheManager.cacheNames) {
                    log.info "Clearing cache $cacheName"
                    Cache cache = grailsCacheManager.getCache(cacheName)
                    cache.clear()
                }
            }

            List<String> defaultCaches = ['grailsBlocksCache', 'grailsTemplatesCache']
            for(name in defaultCaches) {
                if (!grailsCacheManager.cacheExists(name)) {
                    grailsCacheManager.getCache(name)
                }
            }
        }
    }
}
