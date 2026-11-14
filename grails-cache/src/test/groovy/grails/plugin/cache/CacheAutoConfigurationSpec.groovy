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

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication

import org.grails.plugin.cache.GrailsCacheManager
import org.grails.plugin.cache.GrailsCacheManagerAware

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

    void 'another cache manager in the context, such as the one Spring Security caches users with, is not taken for the Grails one'() {
        given: 'Spring Security defines cacheManager when cacheUsers is set'
        CacheManager other = new ConcurrentMapCacheManager()
        AnnotationConfigApplicationContext context = buildContext([:], null, [cacheManager: other])

        expect: 'the context starts, and what the plugin caches with is its own cache manager'
        context.getBean(GrailsCacheAdminService).grailsCacheManager.is(context.getBean('grailsCacheManager'))
        context.getBean(CacheAware).grailsCacheManager.is(context.getBean('grailsCacheManager'))

        cleanup:
        context.close()
    }

    void 'with caching disabled, the one cache manager the context has is used, as before'() {
        given:
        CacheManager only = new ConcurrentMapCacheManager()
        AnnotationConfigApplicationContext context = buildContext(['grails.cache.enabled': 'false'], null, [cacheManager: only])

        expect:
        context.getBean(CacheAware).grailsCacheManager.is(only)

        cleanup:
        context.close()
    }

    void 'with caching disabled and several cache managers, none named grailsCacheManager, none is used'() {
        given:
        AnnotationConfigApplicationContext context = buildContext(['grails.cache.enabled': 'false'], null,
                [cacheManager: new ConcurrentMapCacheManager(), otherCacheManager: new ConcurrentMapCacheManager()])

        expect: 'the context still starts'
        context.getBean(CacheAware).grailsCacheManager == null

        cleanup:
        context.close()
    }

    void 'with caching disabled and several cache managers, the one marked primary is used, as Spring chose it before'() {
        given:
        CacheManager primary = new ConcurrentMapCacheManager()
        AnnotationConfigApplicationContext context = buildContext(['grails.cache.enabled': 'false'], null,
                [cacheManager: new ConcurrentMapCacheManager(), primaryCacheManager: primary], 'primaryCacheManager')

        expect:
        context.getBean(CacheAware).grailsCacheManager.is(primary)

        cleanup:
        context.close()
    }

    void 'the Grails cache manager is used over another cache manager marked primary'() {
        given: 'an application\'s own cache manager, marked primary'
        AnnotationConfigApplicationContext context = buildContext([:], null,
                [cacheManager: new ConcurrentMapCacheManager()], 'cacheManager')

        expect: 'the one the plugin creates its caches in, and its tag library uses'
        context.getBean(CacheAware).grailsCacheManager.is(context.getBean('grailsCacheManager'))
        context.getBean(GrailsCacheAdminService).grailsCacheManager.is(context.getBean('grailsCacheManager'))

        cleanup:
        context.close()
    }

    void 'a lazy cache manager is not created to find the Grails one'() {
        given: 'a lazy cache manager whose store is down, which the injection this replaced never created'
        AnnotationConfigApplicationContext context = buildContext([:], null, [:], null, [unreachableCacheManager: STORE_DOWN])

        expect: 'the context starts, and the Grails cache manager is used'
        context.getBean(CacheAware).grailsCacheManager.is(context.getBean('grailsCacheManager'))
        context.getBean(GrailsCacheAdminService).grailsCacheManager.is(context.getBean('grailsCacheManager'))

        cleanup:
        context.close()
    }

    void 'a lazy cache manager is not created to find the primary one where there is no Grails one'() {
        given:
        CacheManager primary = new ConcurrentMapCacheManager()
        AnnotationConfigApplicationContext context = buildContext(['grails.cache.enabled': 'false'], null,
                [primaryCacheManager: primary], 'primaryCacheManager', [unreachableCacheManager: STORE_DOWN])

        expect:
        context.getBean(CacheAware).grailsCacheManager.is(primary)

        cleanup:
        context.close()
    }

    private static final Supplier<CacheManager> STORE_DOWN = { ->
        throw new IllegalStateException('the cache store is down')
    } as Supplier<CacheManager>

    /** A class the cache transformations make cache-aware, as they do a service with a cached method. */
    static class CacheAware implements GrailsCacheManagerAware {
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
            Map<String, Object> properties, GrailsCacheManager userCacheManager = null,
            Map<String, CacheManager> otherCacheManagers = [:], String primary = null,
            Map<String, Supplier<CacheManager>> lazyCacheManagers = [:]) {
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        if (userCacheManager != null) {
            context.registerBean('grailsCacheManager', GrailsCacheManager, () -> userCacheManager)
        }
        otherCacheManagers.each { String name, CacheManager cacheManager ->
            context.registerBean(name, CacheManager, () -> cacheManager) { BeanDefinition definition ->
                definition.primary = name == primary
            }
        }
        lazyCacheManagers.each { String name, Supplier<CacheManager> cacheManager ->
            context.registerBean(name, CacheManager, cacheManager) { BeanDefinition definition ->
                definition.lazyInit = true
            }
        }
        context.registerBean(CacheAware)
        context.register(CacheAutoConfiguration)
        context.refresh()
        return context
    }
}
