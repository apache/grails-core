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
package grails.boot.config

import java.util.concurrent.atomic.AtomicInteger

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.support.BeanDefinitionOverrideException
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.util.Environment
import grails.util.Holders
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import spock.lang.Specification

/**
 * Proves the architectural linchpin of {@link GrailsEarlyPluginRegistrationPostProcessor}: a plugin
 * bean registered via {@code doWithSpring} lands in the registry <em>before</em>
 * {@code ConfigurationClassPostProcessor} evaluates {@code @ConditionalOnMissingBean}, so the
 * matching Boot auto-config bean backs off — instead of the plugin having to override or remove
 * it afterwards.
 */
class EarlyPluginRegistrationOrderingSpec extends Specification {

    void 'a plugin doWithSpring bean registered early makes a @ConditionalOnMissingBean auto-config bean defer'() {
        given: 'a context whose configuration would, by itself, register a conditional myResolver'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)

        and: 'a plugin discovery promoted to the context, exactly as the bootstrap registry does'
            def discovery = new DefaultPluginDiscovery([earlyOrderingPluginClass] as Class<?>[])
            discovery.loadPluginsFromClasspath = false
            discovery.init(ctx.environment)
            ctx.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)

        and: 'the early registration phase installed through its real entry point'
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the early (plugin) bean is the one named myResolver...'
            ctx.getBean('myResolver') instanceof EarlyOrderingPluginResolver

        and: '...and the conditional default was never created'
            ctx.getBeansOfType(EarlyOrderingBootDefaultResolver).isEmpty()

        and: 'the one true grailsApplication and pluginManager singletons were promoted'
            ctx.getBean(GrailsApplication.APPLICATION_ID) instanceof GrailsApplication
            ctx.getBean(GrailsPluginManager.BEAN_NAME) instanceof GrailsPluginManager
            ctx.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager).getGrailsPlugin('earlyOrdering') != null

        and: 'the environment initializing flag was reset on refresh'
            !Environment.isInitializing()

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'without a promoted plugin discovery the early phase is a no-op and the conditional bean is created (control)'() {
        given:
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the conditional default wins when nothing registered the bean first'
            ctx.getBean('myResolver') instanceof EarlyOrderingBootDefaultResolver

        and: 'no Grails singletons were promoted'
            !ctx.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
            !ctx.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)

        cleanup:
            ctx.close()
    }

    void 'a plugin beanRegistrar bean registered early makes a @ConditionalOnMissingBean auto-config bean defer'() {
        given: 'a context whose configuration would, by itself, register a conditional myResolver'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)

        and: 'a plugin exposing a BeanRegistrar promoted through plugin discovery'
            registerDiscovery(ctx, EarlyOrderingRegistrarGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the registrar (plugin) bean is the one named myResolver and the conditional default backed off'
            ctx.getBean('myResolver') instanceof EarlyOrderingPluginResolver
            ctx.getBeansOfType(EarlyOrderingBootDefaultResolver).isEmpty()

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'plugin beanRegistrar and doWithSpring can coexist on one plugin'() {
        given:
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingDualGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'both the DSL bean and the registrar bean are present'
            ctx.getBean('dualDslBean') instanceof EarlyOrderingPluginResolver
            ctx.getBean('dualRegistrarBean') instanceof EarlyOrderingPluginResolver

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a plugin beanRegistrar defined as a coerced closure registers its bean'() {
        given: 'a plugin whose beanRegistrar() is a closure coerced to BeanRegistrar (no separate class)'
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingClosureRegistrarGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the bean registered by the closure-coerced registrar is present'
            ctx.getBean('closureRegistrarBean') instanceof EarlyOrderingPluginResolver

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'an application doWithSpring bean still overrides a plugin bean of the same name under the default settings'() {
        given: 'a plugin registering overrideProbe and an application registering a different bean under the same name'
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingOverrideGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)
            ctx.register(EarlyOrderingOverrideApplication)

        when:
            ctx.refresh()

        then: 'the application bean wins, replacing the plugin bean registered in the early phase'
            ctx.getBean('overrideProbe') instanceof EarlyOrderingAppResolver

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'with bean-definition overriding disabled an application bean shadowing a plugin bean fails'() {
        given: 'overriding disabled, a plugin registering overrideProbe and an app registering the same name'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.allowBeanDefinitionOverriding = false
            registerDiscovery(ctx, EarlyOrderingOverrideGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)
            ctx.register(EarlyOrderingOverrideApplication)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the colliding application bean is rejected rather than silently merging (see the upgrade notes)'
            thrown(BeanDefinitionOverrideException)

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'the early phase loads plugins with a single manager pass, not a throwaway extra one'() {
        given: 'the plugin construction count from one bare plugin-manager load, as a baseline'
            // Grails wraps each plugin in a GrailsClass (a reference instance) and then creates the
            // real instance, so a single load constructs the plugin more than once; what the retimed
            // lifecycle guarantees is a single manager pass rather than a throwaway pass plus the real
            // one (the rejected earlier approach), which would multiply this count.
            EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.set(0)
            def baselineDiscovery = new DefaultPluginDiscovery([EarlyOrderingCountingGrailsPlugin] as Class<?>[])
            baselineDiscovery.loadPluginsFromClasspath = false
            baselineDiscovery.init(new StandardEnvironment())
            new DefaultGrailsPluginManager(new DefaultGrailsApplication(), baselineDiscovery).loadPlugins()
            int singleLoadCount = EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.get()

        and: 'the early phase booted through the real initializer'
            EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.set(0)
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingCountingGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the early phase instantiates the plugin no more than a single manager load — no throwaway second pass'
            singleLoadCount > 0
            EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.get() == singleLoadCount

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'the initializing flag is reset when the early phase throws'() {
        given: 'a plugin whose doWithSpring throws while the early phase drains it'
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingThrowingGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the refresh fails...'
            thrown(Exception)

        and: '...but the initializing flag (a system property) was reset, not leaked to later contexts'
            !Environment.isInitializing()

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    private static void registerDiscovery(AnnotationConfigApplicationContext ctx, Class<?> pluginClass) {
        def discovery = new DefaultPluginDiscovery([pluginClass] as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        discovery.init(ctx.environment)
        ctx.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
    }

    private static Class<?> getEarlyOrderingPluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
class EarlyOrderingGrailsPlugin {
    def version = '1.0'
    def doWithSpring = {
        myResolver(grails.boot.config.EarlyOrderingPluginResolver)
    }
}
''')
    }

    /** Stands in for a Spring Boot auto-configuration: a name-guarded conditional bean. */
    @Configuration
    static class EarlyOrderingAutoConfigLikeConfig {

        @Bean
        @ConditionalOnMissingBean(name = 'myResolver')
        EarlyOrderingBootDefaultResolver myResolver() {
            new EarlyOrderingBootDefaultResolver()
        }
    }
}

class EarlyOrderingBootDefaultResolver {
}

class EarlyOrderingPluginResolver {
}

class EarlyOrderingRegistrarGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    BeanRegistrar beanRegistrar() {
        new EarlyOrderingResolverRegistrar()
    }
}

class EarlyOrderingResolverRegistrar implements BeanRegistrar {

    @Override
    void register(BeanRegistry registry, org.springframework.core.env.Environment environment) {
        registry.registerBean('myResolver', EarlyOrderingPluginResolver)
    }
}

class EarlyOrderingDualGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            dualDslBean(EarlyOrderingPluginResolver)
        }
    }

    @Override
    BeanRegistrar beanRegistrar() {
        new EarlyOrderingDualRegistrar()
    }
}

class EarlyOrderingDualRegistrar implements BeanRegistrar {

    @Override
    void register(BeanRegistry registry, org.springframework.core.env.Environment environment) {
        registry.registerBean('dualRegistrarBean', EarlyOrderingPluginResolver)
    }
}

class EarlyOrderingClosureRegistrarGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, org.springframework.core.env.Environment environment ->
            registry.registerBean('closureRegistrarBean', EarlyOrderingPluginResolver)
        } as BeanRegistrar
    }
}

class EarlyOrderingCountingGrailsPlugin extends Plugin {

    static final AtomicInteger INSTANCE_COUNT = new AtomicInteger()

    def version = '1.0'

    EarlyOrderingCountingGrailsPlugin() {
        INSTANCE_COUNT.incrementAndGet()
    }
}

class EarlyOrderingThrowingGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { -> throw new IllegalStateException('boom from doWithSpring') }
    }
}

class EarlyOrderingAppResolver {
}

class EarlyOrderingOverrideGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            overrideProbe(EarlyOrderingPluginResolver)
        }
    }
}

class EarlyOrderingOverrideApplication extends GrailsAutoConfiguration {

    @Override
    Closure doWithSpring() {
        { ->
            overrideProbe(EarlyOrderingAppResolver)
        }
    }
}
