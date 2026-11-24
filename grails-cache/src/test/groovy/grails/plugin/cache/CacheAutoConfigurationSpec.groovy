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

import jakarta.annotation.Priority

import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.BeanCurrentlyInCreationException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.NestedExceptionUtils
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

    @Unroll
    void 'the startup hook clears every cache of the cache manager only with grails.cache.clearAtStartup: #key=#value'() {
        given: 'a cache with an entry, as a cache that outlives the application has from an earlier run'
        AnnotationConfigApplicationContext context = buildContext([(key): value])
        GrailsCacheManager cacheManager = context.getBean('grailsCacheManager', GrailsCacheManager)
        cacheManager.getCache('kept').put('key', 'value')

        when:
        pluginFor(context, [(key): value]).doWithApplicationContext()

        then: 'cleanAtStartup, which applications generated by Grails 8 and earlier have, is read by nothing'
        (cacheManager.getCache('kept').get('key') == null) == cleared

        cleanup:
        context.close()

        where:
        key                           | value   || cleared
        'grails.cache.clearAtStartup' | 'true'  || true
        'grails.cache.clearAtStartup' | 'false' || false
        'grails.cache.cleanAtStartup' | 'true'  || false
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

    void 'a grailsCacheManager the application defines is used beside another cache manager'() {
        given: 'a cache manager of the application\'s own as grailsCacheManager, and Spring Security\'s beside it'
        GrailsCacheManager mine = Mock(GrailsCacheManager)
        AnnotationConfigApplicationContext context = buildContext([:], mine, [cacheManager: new ConcurrentMapCacheManager()])

        expect: 'where Spring cannot choose, the cache-aware beans get it, so the context starts and caches with it'
        context.getBean(CacheAware).grailsCacheManager.is(mine)
        context.getBean(GrailsCacheAdminService).grailsCacheManager.is(mine)

        cleanup:
        context.close()
    }

    @Unroll
    void 'a grailsCacheManager built from a cache-aware bean starts beside a cache manager #preferred, which that bean gets, as in 8.0.0'() {
        given: 'the application\'s grailsCacheManager, created from a bean that has a cache manager injected itself'
        AnnotationConfigApplicationContext context = builtFromCacheAware(preferred, preferred == 'marked primary')

        when:
        context.refresh()

        then: 'the context starts, and the cache-aware beans get the cache manager Spring prefers'
        context.getBean(CacheAware).grailsCacheManager.is(context.getBean('preferredCacheManager'))
        context.getBean(GrailsCacheAdminService).grailsCacheManager.is(context.getBean('preferredCacheManager'))

        cleanup:
        context.close()

        where:
        preferred << ['with a priority', 'marked primary']
    }

    @Unroll
    void 'the known difference: a grailsCacheManager built from a cache-aware bean does not start beside a cache manager #preferred, unless that one is marked primary'() {
        given: 'a priority only the object has, which Spring sees once it exists and the alias cannot'
        AnnotationConfigApplicationContext unmarked = builtFromCacheAware(preferred, false)
        AnnotationConfigApplicationContext marked = builtFromCacheAware(preferred, true)

        when: 'Spring takes grailsCacheManager by the alias while it is being created'
        unmarked.refresh()

        then:
        BeanCreationException e = thrown()
        NestedExceptionUtils.getMostSpecificCause(e) instanceof BeanCurrentlyInCreationException

        when: 'the cache manager is marked primary, as the upgrade notes say'
        marked.refresh()

        then: 'the context starts, and the cache-aware beans get it'
        marked.getBean(CacheAware).grailsCacheManager.is(marked.getBean('preferredCacheManager'))
        marked.getBean(GrailsCacheAdminService).grailsCacheManager.is(marked.getBean('preferredCacheManager'))

        cleanup:
        unmarked?.close()
        marked?.close()

        where:
        preferred << ['with a priority only on the object its factory returns',
                      'that a factory bean naming only CacheManager makes, with a priority',
                      'with a priority, which the key generator creates as the cache-aware bean is injected']
    }

    /** The application's grailsCacheManager, created from a cache-aware bean, beside the cache manager described, created first. */
    private static AnnotationConfigApplicationContext builtFromCacheAware(String preferred, boolean markedPrimary) {
        GrailsCacheManager mine = new GrailsConcurrentMapCacheManager()
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        BeanDefinitionCustomizer primary = { BeanDefinition definition -> definition.primary = markedPrimary } as BeanDefinitionCustomizer
        if (preferred == 'with a priority') {
            context.registerBean('preferredCacheManager', HighPriorityCacheManager, primary)
        }
        else if (preferred == 'with a priority only on the object its factory returns') {
            context.registerBean('preferredCacheManager', CacheManager, { -> new HighPriorityCacheManager() } as Supplier<CacheManager>, primary)
        }
        else if (preferred == 'that a factory bean naming only CacheManager makes, with a priority') {
            context.registerBean('preferredCacheManager', PriorityProductFactoryBean, primary)
        }
        else if (preferred == 'with a priority, which the key generator creates as the cache-aware bean is injected') {
            BeanDefinitionCustomizer lazy = { BeanDefinition definition -> definition.lazyInit = true } as BeanDefinitionCustomizer
            context.registerBean('preferredCacheManager', CacheManager,
                    { -> new HighPriorityCacheManager() } as Supplier<CacheManager>, lazy, primary)
            context.registerBean('customCacheKeyGenerator', GrailsCacheKeyGenerator, { ->
                context.getBean('preferredCacheManager')
                new CustomCacheKeyGenerator()
            } as Supplier<GrailsCacheKeyGenerator>, lazy)
        }
        else {
            context.registerBean('preferredCacheManager', ConcurrentMapCacheManager, primary)
        }
        context.registerBean('grailsCacheManager', GrailsCacheManager, { ->
            context.getBean(CacheAware)
            mine
        } as Supplier<GrailsCacheManager>)
        context.registerBean(CacheAware)
        context.register(CacheAutoConfiguration)
        context
    }

    @Unroll
    void 'a grailsCacheManager that is cache-aware itself gets the other cache manager, as in 8.0.0, with circular references #circular'() {
        given: 'Spring leaves a bean out of the candidates for its own fields'
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        context.allowCircularReferences = circular == 'allowed'
        context.registerBean('grailsCacheManager', CacheAwareGrailsCacheManager)
        CacheManager other = new ConcurrentMapCacheManager()
        context.registerBean('other', CacheManager, { -> other } as Supplier<CacheManager>)
        context.registerBean(CacheAware)
        context.register(CacheAutoConfiguration)

        when:
        context.refresh()

        then: 'it starts, and grailsCacheManager is not given itself'
        context.getBean('grailsCacheManager', GrailsCacheManagerAware).grailsCacheManager.is(other)

        and: 'a cache-aware bean beside both, where Spring cannot choose, gets grailsCacheManager'
        context.getBean(CacheAware).grailsCacheManager.is(context.getBean('grailsCacheManager'))

        cleanup:
        context.close()

        where:
        circular << ['allowed', 'not allowed']
    }

    void 'a cache-aware bean given grailsCacheManager depends on it, so it stops while that cache manager still runs'() {
        given: 'a grailsCacheManager stopped in a later phase than the cache-aware bean, and another cache manager'
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        context.registerBean('grailsCacheManager', LifecycleGrailsCacheManager)
        context.registerBean('cacheManager', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>)
        context.registerBean('service', LifecycleService)
        context.register(CacheAutoConfiguration)
        context.refresh()
        LifecycleService service = context.getBean('service', LifecycleService)

        expect: 'it is given grailsCacheManager, and registered as depending on it, as an injection registers it'
        service.grailsCacheManager.is(context.getBean('grailsCacheManager'))
        'service' in context.beanFactory.getDependentBeans('grailsCacheManager')

        when:
        context.close()

        then: 'it stops first, while grailsCacheManager still runs'
        service.stoppedWhileCacheManagerRan
    }

    void 'a cache manager the application names grailsCacheManager by an alias is used beside another cache manager'() {
        given: 'a cache provider\'s manager under a name of its own, aliased grailsCacheManager, and Spring Security\'s beside it'
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        GrailsCacheManager provider = Mock(GrailsCacheManager)
        context.registerBean('providerCacheManager', GrailsCacheManager, { -> provider } as Supplier<GrailsCacheManager>)
        context.registerAlias('providerCacheManager', 'grailsCacheManager')
        context.registerBean('cacheManager', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>)
        context.registerBean(CacheAware)
        context.register(CacheAutoConfiguration)

        when:
        context.refresh()

        then: 'the plugin\'s default backs off, and where Spring cannot choose, the cache-aware beans get the aliased one'
        context.getBeanNamesForType(GrailsConcurrentMapCacheManager).length == 0
        context.getBean(CacheAware).grailsCacheManager.is(provider)
        context.getBean(GrailsCacheAdminService).grailsCacheManager.is(provider)
        'cacheAutoConfigurationSpec.CacheAware' in context.beanFactory.getDependentBeans('providerCacheManager')

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

    void 'an autowired method of a cache-aware bean has its cache manager #others, as Spring injects the field before the method'() {
        given: 'a service that uses its cache manager from an autowired method, as when it warms its caches at startup'
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        if (others == 'beside another one') {
            context.registerBean('cacheManager', CacheManager, { -> new ConcurrentMapCacheManager() } as Supplier<CacheManager>)
        }
        context.registerBean(WarmedOnStartup)
        context.register(CacheAutoConfiguration)

        when:
        context.refresh()

        then:
        context.getBean(WarmedOnStartup).warmedWith.is(context.getBean('grailsCacheManager'))

        cleanup:
        context.close()

        where:
        others << ['alone', 'beside another one']
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

    void 'a cache manager marked primary beside the Grails one is still used, as in 8.0.0'() {
        given: 'an application\'s own cache manager, marked primary'
        CacheManager primary = new ConcurrentMapCacheManager()
        AnnotationConfigApplicationContext context = buildContext([:], null, [cacheManager: primary], 'cacheManager')

        expect: 'Spring chooses as it did; the name is only taken where it cannot'
        context.getBean(CacheAware).grailsCacheManager.is(primary)
        context.getBean(GrailsCacheAdminService).grailsCacheManager.is(primary)

        cleanup:
        context.close()
    }

    void 'a lazy cache manager is not created to find the Grails one'() {
        given: 'a lazy cache manager whose store is down, beside the plugin\'s'
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

    /** A cache manager Spring prefers over others by the priority of its class. */
    @Priority(1)
    static class HighPriorityCacheManager extends ConcurrentMapCacheManager {
    }

    /** A factory bean that names only CacheManager as its type, and makes a cache manager with a priority. */
    static class PriorityProductFactoryBean implements FactoryBean<CacheManager> {

        @Override
        CacheManager getObject() {
            new HighPriorityCacheManager()
        }

        @Override
        Class<?> getObjectType() {
            CacheManager
        }
    }

    /** A grailsCacheManager that is cache-aware itself. */
    static class CacheAwareGrailsCacheManager extends GrailsConcurrentMapCacheManager implements GrailsCacheManagerAware {
    }

    /** A grailsCacheManager with a lifecycle, stopped in a later phase than {@link LifecycleService}. */
    static class LifecycleGrailsCacheManager extends GrailsConcurrentMapCacheManager implements SmartLifecycle {

        boolean running

        @Override
        void start() {
            running = true
        }

        @Override
        void stop() {
            running = false
        }

        @Override
        int getPhase() {
            100
        }
    }

    /** A cache-aware bean with a lifecycle, which records whether its cache manager ran when it stopped. */
    static class LifecycleService implements GrailsCacheManagerAware, SmartLifecycle {

        boolean running

        boolean stoppedWhileCacheManagerRan

        @Override
        void start() {
            running = true
        }

        @Override
        void stop() {
            stoppedWhileCacheManagerRan = ((LifecycleGrailsCacheManager) grailsCacheManager).running
            running = false
        }

        @Override
        int getPhase() {
            0
        }
    }

    /** A cache-aware service that uses its cache manager from an autowired method. */
    static class WarmedOnStartup implements GrailsCacheManagerAware {
        CacheManager warmedWith

        @Autowired
        void warmUp(BeanFactory beanFactory) {
            warmedWith = grailsCacheManager
        }
    }

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
        // Spring Boot binds the @ConfigurationProperties beans of an application, such as grailsCacheConfiguration
        ConfigurationPropertiesBindingPostProcessor.register(context)
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
