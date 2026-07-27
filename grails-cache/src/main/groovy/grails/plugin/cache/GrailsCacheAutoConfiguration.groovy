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

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

import org.grails.plugin.cache.GrailsCacheManager

/**
 * Auto-configures the cache plugin's default cache manager and key generator. Registered here
 * rather than by the plugin descriptor so that a bean contributed by the application or another
 * plugin — for example a cache-provider plugin's {@code grailsCacheManager} — makes the default
 * back off cleanly instead of triggering a bean-definition override.
 *
 * <p>Gated on the {@code CachePluginConfiguration} definition contributed by the cache plugin
 * descriptor's registrar (which runs before auto-configuration conditions are evaluated), so the
 * auto-configuration backs off entirely when the plugin is not active — e.g. the jar is on the
 * classpath but the plugin is excluded — keeping it in lockstep with the descriptor.</p>
 *
 * @since 8.0
 */
@AutoConfiguration
@ConditionalOnBooleanProperty(name = 'grails.cache.enabled', matchIfMissing = true)
@ConditionalOnBean(CachePluginConfiguration)
@CompileStatic
class GrailsCacheAutoConfiguration {

    @Value('${grails.cache.cacheManager:}')
    String cacheManagerType

    @Bean
    @ConditionalOnMissingBean(name = 'customCacheKeyGenerator')
    CustomCacheKeyGenerator customCacheKeyGenerator() {
        new CustomCacheKeyGenerator()
    }

    @Bean
    @ConditionalOnMissingBean(name = 'grailsCacheManager')
    GrailsCacheManager grailsCacheManager(CachePluginConfiguration grailsCacheConfiguration) {
        if (cacheManagerType == 'GrailsConcurrentLinkedMapCacheManager') {
            return new GrailsConcurrentLinkedMapCacheManager(configuration: grailsCacheConfiguration)
        }
        new GrailsConcurrentMapCacheManager(configuration: grailsCacheConfiguration)
    }

}
