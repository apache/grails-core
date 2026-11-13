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

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication

import org.grails.plugin.cache.GrailsCacheManager

import spock.lang.Specification
import spock.lang.Unroll

class CacheAutoConfigurationSpec extends Specification {

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

    void 'the plugin infrastructure beans are auto-configured too'() {
        given:
        AnnotationConfigApplicationContext context = buildContext([:])

        expect:
        context.getBean('grailsCacheConfiguration') instanceof CachePluginConfiguration
        context.containsBean('grailsCacheAdminService')

        cleanup:
        context.close()
    }

    void 'no cache beans at all are auto-configured when caching is disabled'() {
        given:
        AnnotationConfigApplicationContext context = buildContext(['grails.cache.enabled': 'false'])

        expect:
        !context.containsBean('grailsCacheConfiguration')
        !context.containsBean('grailsCacheAdminService')

        cleanup:
        context.close()
    }

    @Unroll
    void 'a relaxed boolean disables the beans, and the startup hook backs off with them: grails.cache.enabled=#value'() {
        given: '@ConditionalOnBooleanProperty compares the literal true/false, so these do not enable'
        AnnotationConfigApplicationContext context = buildContext(['grails.cache.enabled': value])

        expect:
        !context.containsBean('grailsCacheConfiguration')

        when: 'the plugin lifecycle hook runs against that context'
        pluginFor(context, ['grails.cache.enabled': value]).doWithApplicationContext()

        then: 'it backs off rather than asking for a bean the condition declined to register - reading ' +
                'the property again here would resolve these as true and fail startup'
        noExceptionThrown()

        cleanup:
        context.close()

        where:
        value << ['yes', 'on', '1']
    }

    void 'the startup hook does its work when the beans are there'() {
        given: 'the enabled path, so the back-off above is not simply always taken'
        AnnotationConfigApplicationContext context = buildContext([:])

        when:
        pluginFor(context, [:]).doWithApplicationContext()

        then: 'the default caches it creates on startup exist'
        GrailsCacheManager cacheManager = context.getBean('grailsCacheManager', GrailsCacheManager)
        cacheManager.cacheExists('grailsBlocksCache')
        cacheManager.cacheExists('grailsTemplatesCache')

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

    /**
     * A plugin wired the way the runtime wires it: with the application context and with a
     * GrailsApplication carrying the same configuration. Both matter - reading the property back out
     * of that configuration is exactly what doWithApplicationContext must not do.
     */
    private static CacheGrailsPlugin pluginFor(AnnotationConfigApplicationContext context, Map<String, Object> properties) {
        GrailsApplication grailsApplication = new DefaultGrailsApplication()
        properties.each { String key, Object value -> grailsApplication.config.put(key, value) }
        new CacheGrailsPlugin().tap {
            it.grailsApplication = grailsApplication
            it.applicationContext = context
        }
    }

    @CompileStatic
    private static AnnotationConfigApplicationContext buildContext(
            Map<String, Object> properties, GrailsCacheManager userCacheManager = null) {
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        if (userCacheManager != null) {
            context.registerBean('grailsCacheManager', GrailsCacheManager, () -> userCacheManager)
        }
        context.register(CacheAutoConfiguration)
        context.refresh()
        return context
    }
}
