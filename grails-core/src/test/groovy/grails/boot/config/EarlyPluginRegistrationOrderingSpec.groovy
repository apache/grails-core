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
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionOverrideException
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.util.Environment
import grails.util.Holders
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import spock.lang.Specification

/**
 * Proves the architectural linchpin of {@link GrailsEarlyPluginRegistrationPostProcessor}: a plugin
 * bean registered by its {@code beanRegistrar()}, or by a post-processor that registrar registers,
 * lands in the registry <em>before</em> {@code ConfigurationClassPostProcessor} evaluates
 * {@code @ConditionalOnMissingBean}, so the matching Boot auto-config bean backs off — instead of the
 * plugin having to override or remove it afterwards — and a plugin loaded later replaces a bean of the
 * same name that an earlier plugin registered.
 */
class EarlyPluginRegistrationOrderingSpec extends Specification {

    void 'a bean a plugin registers from a post-processor makes a @ConditionalOnMissingBean auto-config bean defer'() {
        given: 'a context whose configuration would, by itself, register a conditional myResolver'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)

        and: 'a plugin discovery and an application class, exactly as a Grails application has'
            registerDiscovery(ctx, earlyOrderingPluginClass)

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

    void 'an application GrailsApp launched gets the plugin lifecycle whatever its sources are'() {
        given: 'the sources GrailsApp stashes, none of them a Grails application class'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)
            promoteDiscovery(ctx, earlyOrderingPluginClass)
            ctx.beanFactory.registerSingleton(GrailsEarlyPluginRegistrationPostProcessor.APPLICATION_SOURCE_CLASSES_BEAN_NAME,
                    [EarlyOrderingAutoConfigLikeConfig] as Class<?>[])
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the plugin bean is registered, so the conditional default backed off'
            ctx.getBean('myResolver') instanceof EarlyOrderingPluginResolver

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a context that registers the application class among its sources gets the plugin lifecycle'() {
        given: 'the sources of a @SpringBootTest that names the application class alongside its own config'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig, EarlyOrderingApplication)
            promoteDiscovery(ctx, earlyOrderingPluginClass)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the application class is recovered from the registry, so the lifecycle runs as for any Grails application'
            ctx.getBean('myResolver') instanceof EarlyOrderingPluginResolver
            ctx.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a context that is not a Grails application does not get the plugin lifecycle'() {
        given: 'plugin discovery promoted to a context with no Grails application class in it'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)
            promoteDiscovery(ctx, earlyOrderingPluginClass)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'no plugin contributed a bean, so the conditional default is the one created'
            ctx.getBean('myResolver') instanceof EarlyOrderingBootDefaultResolver

        and: 'and nothing of the Grails lifecycle was built for an application that did not ask for it'
            !ctx.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
            !ctx.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)
            !Environment.isInitializing()

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a plain child beneath a Grails application does not run the lifecycle again'() {
        given: 'a parent context standing for a running Grails application'
            def parent = new AnnotationConfigApplicationContext()
            parent.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
            parent.refresh()

        and: 'a child beneath it with plugin discovery promoted but no Grails application class of its own'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.parent = parent
            ctx.register(EarlyOrderingAutoConfigLikeConfig)
            promoteDiscovery(ctx, earlyOrderingPluginClass)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the conditional default is created, as the plugin contributed nothing here'
            ctx.getBean('myResolver') instanceof EarlyOrderingBootDefaultResolver
            !ctx.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
            !ctx.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)

        and: 'the Grails application it sees is the one of the parent'
            ctx.getBean(GrailsApplication.APPLICATION_ID).is(parent.getBean(GrailsApplication.APPLICATION_ID))

        cleanup:
            ctx.close()
            parent.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a context whose parent already holds a Grails application refuses to start as a second one'() {
        given: 'a parent context standing for a running Grails application'
            def parent = new AnnotationConfigApplicationContext()
            parent.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
            parent.refresh()

        and: 'a child beneath it that is a Grails application in its own right'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.parent = parent
            registerDiscovery(ctx, earlyOrderingPluginClass)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the lifecycle keeps JVM-wide state, so one hierarchy cannot hold two Grails applications'
            IllegalStateException e = thrown()
            e.message.contains('Only one context in a hierarchy can be the Grails application')

        and: 'nothing of the lifecycle was started for it'
            !ctx.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
            !Environment.isInitializing()

        cleanup:
            ctx.close()
            parent.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a plugin beanRegistrar bean registered early makes a @ConditionalOnMissingBean auto-config bean defer'() {
        given: 'a context whose configuration would, by itself, register a conditional myResolver'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)

        and: 'a plugin exposing a BeanRegistrar promoted through plugin discovery'
            registerDiscovery(ctx, registrarPluginClass)
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

    void 'a registrar registers beans directly and through a post-processor at once'() {
        given:
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, dualPluginClass)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'both the post-processor bean and the registrar bean are present'
            ctx.getBean('dualPostProcessorBean') instanceof EarlyOrderingPluginResolver
            ctx.getBean('dualRegistrarBean') instanceof EarlyOrderingPluginResolver

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a plugin beanRegistrar defined as a coerced closure registers its bean'() {
        given: 'a plugin whose beanRegistrar() is a closure coerced to BeanRegistrar (no separate class)'
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, closureRegistrarPluginClass)
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

    void 'a plugin loaded later replaces the bean of the same name an earlier plugin registers from a post-processor: #description'() {
        given: 'two plugins whose post-processors register sharedProbe, discovered in the order given'
            def ctx = new AnnotationConfigApplicationContext()
            def plugins = sharedProbePluginClasses(secondLoadsAfterFirst)
            promoteDiscovery(ctx, discoveryOrder.collect { plugins[it] } as Class<?>[])
            ctx.beanFactory.registerSingleton(GrailsEarlyPluginRegistrationPostProcessor.APPLICATION_SOURCE_CLASSES_BEAN_NAME,
                    [EarlyOrderingApplication] as Class<?>[])
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the bean is the one of the plugin that loads last'
            ctx.getBean('sharedProbe').class.simpleName == expected

        and: 'the load order is what decided it'
            ctx.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager).allPlugins*.name.findAll { it.startsWith('earlyOrderingShared') } ==
                    (secondLoadsAfterFirst ? ['earlyOrderingSharedFirst', 'earlyOrderingSharedSecond'] : ['earlyOrderingSharedSecond', 'earlyOrderingSharedFirst'])

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)

        where:
            description                                      | secondLoadsAfterFirst | discoveryOrder        || expected
            'second loads after first, discovered first'     | true                  | ['second', 'first']   || 'EarlyOrderingSecondResolver'
            'second loads after first, discovered second'    | true                  | ['first', 'second']   || 'EarlyOrderingSecondResolver'
            'first loads after second, discovered first'     | false                 | ['first', 'second']   || 'EarlyOrderingFirstResolver'
            'first loads after second, discovered second'    | false                 | ['second', 'first']   || 'EarlyOrderingFirstResolver'
    }

    void 'an application doWithSpring bean still overrides a plugin bean of the same name under the default settings'() {
        given: 'a plugin registering overrideProbe and an application registering a different bean under the same name'
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, overridePluginClass)
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
            registerDiscovery(ctx, overridePluginClass)
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
            EarlyOrderingCounter.INSTANCE_COUNT.set(0)
            def baselineDiscovery = new DefaultPluginDiscovery([countingPluginClass] as Class<?>[])
            baselineDiscovery.loadPluginsFromClasspath = false
            baselineDiscovery.init(new StandardEnvironment())
            new DefaultGrailsPluginManager(new DefaultGrailsApplication(), baselineDiscovery).loadPlugins()
            int singleLoadCount = EarlyOrderingCounter.INSTANCE_COUNT.get()

        and: 'the early phase booted through the real initializer'
            EarlyOrderingCounter.INSTANCE_COUNT.set(0)
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, countingPluginClass)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the early phase instantiates the plugin no more than a single manager load — no throwaway second pass'
            singleLoadCount > 0
            EarlyOrderingCounter.INSTANCE_COUNT.get() == singleLoadCount

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'the initializing flag is reset when the early phase throws'() {
        given: 'a plugin whose beanRegistrar throws while the early phase applies it'
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, throwingPluginClass)
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

    /**
     * Promotes plugin discovery the way the bootstrap registry does, and stashes an application
     * class the way {@code GrailsApp} does. Both are what makes a context a Grails application, and
     * the early phase runs for nothing else.
     */
    private static void registerDiscovery(AnnotationConfigApplicationContext ctx, Class<?> pluginClass) {
        promoteDiscovery(ctx, pluginClass)
        ctx.beanFactory.registerSingleton(GrailsEarlyPluginRegistrationPostProcessor.APPLICATION_SOURCE_CLASSES_BEAN_NAME,
                [EarlyOrderingApplication] as Class<?>[])
    }

    private static void promoteDiscovery(AnnotationConfigApplicationContext ctx, Class<?>... pluginClasses) {
        def discovery = new DefaultPluginDiscovery(pluginClasses)
        discovery.loadPluginsFromClasspath = false
        discovery.init(ctx.environment)
        ctx.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
    }

    private static Class<?> getEarlyOrderingPluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
@grails.plugins.metadata.GrailsPluginDescriptor
class EarlyOrderingGrailsPlugin extends grails.plugins.Plugin {
    org.springframework.beans.factory.BeanRegistrar beanRegistrar() {
        new grails.boot.config.EarlyOrderingPostProcessorRegistrar('earlyOrderingPostProcessor', 'myResolver',
                grails.boot.config.EarlyOrderingPluginResolver)
    }
}
''')
    }

    private static Class<?> getRegistrarPluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
import grails.boot.config.EarlyOrderingPluginResolver
import grails.boot.config.EarlyOrderingResolverRegistrar
import grails.boot.config.EarlyOrderingDualRegistrar
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
@grails.plugins.metadata.GrailsPluginDescriptor
class EarlyOrderingRegistrarGrailsPlugin extends grails.plugins.Plugin {

    @Override
    BeanRegistrar beanRegistrar() {
        new EarlyOrderingResolverRegistrar()
    }
}
''')
    }

    private static Class<?> getDualPluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
import grails.boot.config.EarlyOrderingPluginResolver
import grails.boot.config.EarlyOrderingResolverRegistrar
import grails.boot.config.EarlyOrderingDualRegistrar
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
@grails.plugins.metadata.GrailsPluginDescriptor
class EarlyOrderingDualGrailsPlugin extends grails.plugins.Plugin {

    @Override
    BeanRegistrar beanRegistrar() {
        new EarlyOrderingDualRegistrar()
    }
}
''')
    }

    private static Class<?> getClosureRegistrarPluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
import grails.boot.config.EarlyOrderingPluginResolver
import grails.boot.config.EarlyOrderingResolverRegistrar
import grails.boot.config.EarlyOrderingDualRegistrar
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
@grails.plugins.metadata.GrailsPluginDescriptor
class EarlyOrderingClosureRegistrarGrailsPlugin extends grails.plugins.Plugin {

    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, org.springframework.core.env.Environment environment ->
            registry.registerBean('closureRegistrarBean', EarlyOrderingPluginResolver)
        } as BeanRegistrar
    }
}
''')
    }

    private static Class<?> getCountingPluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
import grails.boot.config.EarlyOrderingPluginResolver
import grails.boot.config.EarlyOrderingResolverRegistrar
import grails.boot.config.EarlyOrderingDualRegistrar
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
@grails.plugins.metadata.GrailsPluginDescriptor
class EarlyOrderingCountingGrailsPlugin extends grails.plugins.Plugin {

    EarlyOrderingCountingGrailsPlugin() {
        grails.boot.config.EarlyOrderingCounter.INSTANCE_COUNT.incrementAndGet()
    }
}
''')
    }

    private static Class<?> getThrowingPluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
import grails.boot.config.EarlyOrderingPluginResolver
import grails.boot.config.EarlyOrderingResolverRegistrar
import grails.boot.config.EarlyOrderingDualRegistrar
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
@grails.plugins.metadata.GrailsPluginDescriptor
class EarlyOrderingThrowingGrailsPlugin extends grails.plugins.Plugin {

    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, org.springframework.core.env.Environment environment ->
            throw new IllegalStateException('boom from beanRegistrar')
        } as BeanRegistrar
    }
}
''')
    }

    private static Class<?> getOverridePluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
import grails.boot.config.EarlyOrderingPluginResolver
import grails.boot.config.EarlyOrderingResolverRegistrar
import grails.boot.config.EarlyOrderingDualRegistrar
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
@grails.plugins.metadata.GrailsPluginDescriptor
class EarlyOrderingOverrideGrailsPlugin extends grails.plugins.Plugin {

    @Override
    BeanRegistrar beanRegistrar() {
        new grails.boot.config.EarlyOrderingPostProcessorRegistrar('earlyOrderingOverridePostProcessor', 'overrideProbe',
                EarlyOrderingPluginResolver)
    }
}
''')
    }

    /**
     * Two plugins, compiled here, whose post-processors both register {@code sharedProbe}; the one the
     * flag names loads after the other.
     */
    private static Map<String, Class<?>> sharedProbePluginClasses(boolean secondLoadsAfterFirst) {
        def loader = new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader)
        String template = '''
@grails.plugins.metadata.GrailsPluginDescriptor(loadAfter = LOAD_AFTER)
class EarlyOrderingSharedNAMEGrailsPlugin extends grails.plugins.Plugin {
    org.springframework.beans.factory.BeanRegistrar beanRegistrar() {
        new grails.boot.config.EarlyOrderingPostProcessorRegistrar('earlyOrderingSharedNAMEPostProcessor', 'sharedProbe',
                grails.boot.config.EarlyOrderingNAMEResolver)
    }
}
'''
        [
                first : loader.parseClass(template.replace('NAME', 'First')
                        .replace('LOAD_AFTER', secondLoadsAfterFirst ? '[]' : "['earlyOrderingSharedSecond']")),
                second: loader.parseClass(template.replace('NAME', 'Second')
                        .replace('LOAD_AFTER', secondLoadsAfterFirst ? "['earlyOrderingSharedFirst']" : '[]'))
        ]
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

/** Stands in for the application class of a Grails application, which is what GrailsApp stashes. */
class EarlyOrderingApplication extends GrailsAutoConfiguration {
}

class EarlyOrderingBootDefaultResolver {
}

class EarlyOrderingPluginResolver {
}

class EarlyOrderingResolverRegistrar implements BeanRegistrar {

    @Override
    void register(BeanRegistry registry, org.springframework.core.env.Environment environment) {
        registry.registerBean('myResolver', EarlyOrderingPluginResolver)
    }
}

class EarlyOrderingDualRegistrar implements BeanRegistrar {

    @Override
    void register(BeanRegistry registry, org.springframework.core.env.Environment environment) {
        registry.registerBean('dualRegistrarBean', EarlyOrderingPluginResolver)
        new EarlyOrderingPostProcessorRegistrar('earlyOrderingDualPostProcessor', 'dualPostProcessorBean',
                EarlyOrderingPluginResolver).register(registry, environment)
    }
}

class EarlyOrderingFirstResolver {
}

class EarlyOrderingSecondResolver {
}

/**
 * Registers a bean definition the way a plugin registers one the {@code BeanRegistry} API cannot
 * express: as a highest-precedence {@link PriorityOrdered} registry post-processor.
 */
class EarlyOrderingDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    final String beanName
    final Class<?> beanClass

    EarlyOrderingDefinitionPostProcessor(String beanName, Class<?> beanClass) {
        this.beanName = beanName
        this.beanClass = beanClass
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        registry.registerBeanDefinition(beanName, new RootBeanDefinition(beanClass))
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}

/** The registrar of a plugin that registers one bean through {@link EarlyOrderingDefinitionPostProcessor}. */
class EarlyOrderingPostProcessorRegistrar implements BeanRegistrar {

    final String postProcessorName
    final String beanName
    final Class<?> beanClass

    EarlyOrderingPostProcessorRegistrar(String postProcessorName, String beanName, Class<?> beanClass) {
        this.postProcessorName = postProcessorName
        this.beanName = beanName
        this.beanClass = beanClass
    }

    @Override
    void register(BeanRegistry registry, org.springframework.core.env.Environment environment) {
        registry.registerBean(postProcessorName, EarlyOrderingDefinitionPostProcessor) {
            it.infrastructure().supplier { new EarlyOrderingDefinitionPostProcessor(beanName, beanClass) }
        }
    }
}

class EarlyOrderingCounter {
    static final AtomicInteger INSTANCE_COUNT = new AtomicInteger()
}

class EarlyOrderingAppResolver {
}

class EarlyOrderingOverrideApplication extends GrailsAutoConfiguration {

    @Override
    Closure doWithSpring() {
        { ->
            overrideProbe(EarlyOrderingAppResolver)
        }
    }
}
