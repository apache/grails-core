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

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.cache.Cache

import grails.plugins.Plugin
import org.grails.plugin.cache.GrailsCacheManager

/**
 * Configures the cache plugin.
 *
 * <p>Every bean is contributed as auto-configuration so that one supplied by the application or
 * another plugin — for example a cache-provider plugin's {@code grailsCacheManager} — makes the
 * default back off cleanly instead of triggering a bean-definition override. The whole set is gated
 * on {@code grails.cache.enabled}, which is compared against the literal {@code true}/{@code false}
 * and defaults to enabled.</p>
 *
 * <p>Having this jar on the classpath is therefore sufficient for the cache beans, the way it is for
 * an ordinary Boot starter. The deleted {@code GrailsCacheAutoConfiguration} additionally carried
 * {@code @ConditionalOnBean(CachePluginConfiguration)}, which kept it in lockstep with the plugin
 * descriptor: the descriptor's registrar contributed that bean, so the auto-configuration backed off
 * when the plugin was not active. That gate cannot be restated here, because
 * {@code grailsCacheConfiguration} is now declared in this same {@code beans} block and the
 * condition would be gating on a bean this class itself contributes. The plugin declares no
 * {@code profiles} or {@code environments}, so it is active wherever its jar is - which is what makes
 * dropping the gate a rename of the mechanism rather than a change in which applications get the
 * beans. Anything that later makes the descriptor conditionally inactive would need a different
 * anchor for it.</p>
 */
@Slf4j
@CompileStatic
@AutoConfiguration
@ConditionalOnBooleanProperty(name = 'grails.cache.enabled', matchIfMissing = true)
class CacheGrailsPlugin extends Plugin {

    def grailsVersion = '8.0.0-SNAPSHOT > *'
    def observe = ['controllers', 'services']
    def loadAfter = ['controllers', 'services']
    def authorEmail = ''
    def description = 'Grails Cache Plugin'

    def pluginExcludes = [
            '**/com/demo/**',
            'grails-app/views/**',
            '**/*.gsp'
    ]

    private static final String CACHE_CONFIGURATION_BEAN = 'grailsCacheConfiguration'

    def beans = {
        field('cacheManagerType', String).value('grails.cache.cacheManager', '')

        bean(GrailsCacheAdminService)

        // CachePluginConfiguration derives cachePluginConfiguration, so the contractual name is stated.
        bean('grailsCacheConfiguration', CachePluginConfiguration)

        bean(CustomCacheKeyGenerator).conditionalOnMissingBeanName()

        bean(GrailsCacheManager).conditionalOnMissingBeanName() { CachePluginConfiguration grailsCacheConfiguration ->
            if (cacheManagerType == 'GrailsConcurrentLinkedMapCacheManager') {
                return new GrailsConcurrentLinkedMapCacheManager(configuration: grailsCacheConfiguration)
            }
            new GrailsConcurrentMapCacheManager(configuration: grailsCacheConfiguration)
        }
    }

    /**
     * Keyed on whether the beans were actually registered, rather than on a second reading of
     * {@code grails.cache.enabled}. The two disagree: {@code @ConditionalOnBooleanProperty} compares
     * the literal strings {@code true}/{@code false}, while {@code config.getProperty(..., Boolean)}
     * also accepts {@code yes}/{@code on}/{@code 1} - so on {@code grails.cache.enabled=yes} this
     * hook believed caching was on and asked for a bean the condition had just declined to register,
     * failing startup. Asking the context what exists cannot drift from the condition that decided it.
     */
    @CompileStatic
    void doWithApplicationContext() {
        if (!applicationContext.containsBean(CACHE_CONFIGURATION_BEAN)) {
            log.warn('Cache plugin is disabled: set grails.cache.enabled to true (or leave it unset) to enable it')
            return
        }

        CachePluginConfiguration pluginConfiguration =
                applicationContext.getBean(CACHE_CONFIGURATION_BEAN, CachePluginConfiguration)
        GrailsCacheManager grailsCacheManager = applicationContext.getBean('grailsCacheManager', GrailsCacheManager)

        if (pluginConfiguration.clearAtStartup) {
            for (String cacheName in grailsCacheManager.cacheNames) {
                log.info('Clearing cache {}', cacheName)
                Cache cache = grailsCacheManager.getCache(cacheName)
                cache.clear()
            }
        }

        List<String> defaultCaches = ['grailsBlocksCache', 'grailsTemplatesCache']
        for (name in defaultCaches) {
            if (!grailsCacheManager.cacheExists(name)) {
                grailsCacheManager.getCache(name)
            }
        }
    }
}
