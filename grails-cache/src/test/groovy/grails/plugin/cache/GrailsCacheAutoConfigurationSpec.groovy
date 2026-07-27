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

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

import org.grails.plugin.cache.GrailsCacheManager

import spock.lang.Specification

class GrailsCacheAutoConfigurationSpec extends Specification {

    void 'the default cache manager and key generator are auto-configured'() {
        given:
        AnnotationConfigApplicationContext context = buildContext([:])

        expect:
        context.getBean('grailsCacheManager') instanceof GrailsConcurrentMapCacheManager
        context.getBean('customCacheKeyGenerator') instanceof CustomCacheKeyGenerator

        cleanup:
        context.close()
    }

    void 'the cache manager implementation is selected from configuration'() {
        given:
        AnnotationConfigApplicationContext context = buildContext(
                ['grails.cache.cacheManager': 'GrailsConcurrentLinkedMapCacheManager'])

        expect:
        context.getBean('grailsCacheManager') instanceof GrailsConcurrentLinkedMapCacheManager

        cleanup:
        context.close()
    }

    void 'no cache beans are auto-configured when caching is disabled'() {
        given:
        AnnotationConfigApplicationContext context = buildContext(['grails.cache.enabled': 'false'])

        expect:
        !context.containsBean('grailsCacheManager')
        !context.containsBean('customCacheKeyGenerator')

        cleanup:
        context.close()
    }

    void 'the auto-configuration backs off entirely when the cache plugin is not active'() {
        given: 'a context without the plugin-contributed grailsCacheConfiguration definition'
        AnnotationConfigApplicationContext context = buildContext([:], null, false)

        expect:
        !context.containsBean('grailsCacheManager')
        !context.containsBean('customCacheKeyGenerator')

        cleanup:
        context.close()
    }

    void 'a user-defined grailsCacheManager bean makes the auto-configured default back off'() {
        given: 'a user-defined cache manager under the auto-configured bean name'
        def userCacheManager = Mock(GrailsCacheManager)
        AnnotationConfigApplicationContext context = buildContext([:], userCacheManager)

        expect: 'the user bean wins and the framework default is never registered'
        context.getBean('grailsCacheManager').is(userCacheManager)
        context.getBeanNamesForType(GrailsConcurrentMapCacheManager).length == 0

        cleanup:
        context.close()
    }

    @CompileStatic
    private static AnnotationConfigApplicationContext buildContext(
            Map<String, Object> properties, GrailsCacheManager userCacheManager = null, boolean pluginActive = true) {
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        if (pluginActive) {
            context.registerBean('grailsCacheConfiguration', CachePluginConfiguration, () -> new CachePluginConfiguration())
        }
        if (userCacheManager != null) {
            context.registerBean('grailsCacheManager', GrailsCacheManager, () -> userCacheManager)
        }
        context.register(GrailsCacheAutoConfiguration)
        context.refresh()
        return context
    }
}
