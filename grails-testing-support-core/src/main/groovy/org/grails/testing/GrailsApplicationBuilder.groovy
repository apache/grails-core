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

package org.grails.testing

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import jakarta.servlet.ServletContext

import org.springframework.asm.AnnotationVisitor
import org.springframework.asm.ClassReader
import org.springframework.asm.ClassVisitor
import org.springframework.asm.SpringAsmInfo
import org.springframework.beans.BeansException
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.annotation.ImportCandidates
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigRegistry
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.ConversionServiceFactoryBean
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.util.ClassUtils
import org.springframework.web.context.ConfigurableWebApplicationContext

import grails.boot.config.GrailsApplicationPostProcessor
import grails.config.Settings
import grails.core.GrailsApplication
import grails.core.GrailsApplicationLifeCycle
import grails.core.support.proxy.DefaultProxyHandler
import grails.plugins.GrailsPluginManager
import grails.spring.BeanBuilder
import grails.util.Holders
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginInfo
import org.apache.grails.core.plugins.filters.IncludingPluginFilter
import org.apache.grails.core.plugins.PluginDiscovery
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.grails.spring.context.support.GrailsPlaceholderConfigurer
import org.grails.spring.context.support.MapBasedSmartPropertyOverrideConfigurer
import org.grails.transaction.TransactionManagerPostProcessor

/**
 * Created by jameskleeh on 5/31/17.
 */
@CompileStatic
class GrailsApplicationBuilder {

    public static final boolean isServletApiPresent = ClassUtils.isPresent(
            'jakarta.servlet.ServletContext',
            GrailsApplicationBuilder.classLoader
    )

    static final Set DEFAULT_INCLUDED_PLUGINS = ['core', 'eventBus'] as Set

    static final String POST_PROCESSOR_BEAN_NAME = 'grailsApplicationPostProcessor'
    /** The property source {@link #publishToEnvironment} adds. */
    static final String DO_WITH_CONFIG_PROPERTY_SOURCE = 'doWithConfig'
    static final String GRAILS_PLUGIN_SUFFIX = 'GrailsPlugin'
    static final String AUTO_CONFIGURATION_SUFFIX = 'AutoConfiguration'
    private static final String GRAILS_BEANS_DESCRIPTOR = 'Lgrails/compiler/beans/GrailsBeans;'
    private static final String AUTO_CONFIGURATION_NAME_ATTRIBUTE = 'autoConfigurationName'

    Closure doWithSpring
    BeanRegistrar beanRegistrar
    Set<Class<?>> configurationClasses
    Closure doWithConfig
    Set<String> includePlugins
    boolean loadExternalBeans
    boolean localOverride = false

    GrailsApplication grailsApplication
    Object servletContext

    /** What the included plugins registered, which the harness's own defaults give way to. */
    private Map<String, BeanDefinition> pluginBeanDefinitions = [:]

    GrailsApplicationBuilder build() {

        servletContext = createServletContext()
        def mainContext = createMainContext(servletContext)

        if (isServletApiPresent) {
            // NOTE: The following dynamic class loading hack is temporary so the
            // compile time dependency on the servlet api can be removed from this
            // sub project.  This whole GrailsApplicationTestPlugin class will soon
            // be removed so rather than implement a real solution, this hack will
            // do for now to keep the build healthy.
            try {
                def appDiscoveryStrategyClass = Class.forName(
                        'org.grails.web.context.ServletEnvironmentGrailsApplicationDiscoveryStrategy'
                )
                def appDiscoveryStrategy = appDiscoveryStrategyClass
                        .getDeclaredConstructor(ServletContext)
                        .newInstance(servletContext)
                Holders.addApplicationDiscoveryStrategy(
                        (GrailsApplicationDiscoveryStrategy) appDiscoveryStrategy
                )
            }
            catch (Throwable ignored) {}

            try {
                def gcu = Class.forName('org.grails.web.servlet.context.GrailsConfigUtils')
                def method = gcu.methods.find { it.name == 'configureServletContextAttributes' }
                method?.invoke(
                        null,
                        servletContext,
                        grailsApplication,
                        mainContext.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager),
                        mainContext
                )
            }
            catch (Throwable ignored) {}
        }

        grailsApplication = mainContext.getBean('grailsApplication') as GrailsApplication

        if (!grailsApplication.initialised) {
            grailsApplication.initialise()
        }

        return this
    }

    protected Object createServletContext() {

        def context = null

        if (isServletApiPresent) {
            context = ClassUtils.forName('org.springframework.mock.web.MockServletContext').getDeclaredConstructor().newInstance()
            Holders.servletContext = context
        }

        return context
    }

    protected ConfigurableApplicationContext createMainContext(Object servletContext) {
        ConfigurableApplicationContext context
        if (isServletApiPresent && servletContext != null) {
            // Spring Boot 4.0: AnnotationConfigServletWebApplicationContext relocated from
            // org.springframework.boot.web.servlet.context to org.springframework.boot.web.context.servlet
            context = (ConfigurableApplicationContext) ClassUtils
                    .forName('org.springframework.boot.web.context.servlet.AnnotationConfigServletWebApplicationContext')
                    .getDeclaredConstructor()
                    .newInstance()
            ((ConfigurableWebApplicationContext) context).setServletContext((ServletContext) servletContext)
        } else {
            context = (ConfigurableApplicationContext) ClassUtils
                    .forName('org.springframework.context.annotation.AnnotationConfigApplicationContext')
                    .getDeclaredConstructor()
                    .newInstance()
        }

        def beanFactory = context.beanFactory as DefaultListableBeanFactory
        // Initialize the environment before any bean definitions are registered so that the spring.main.*
        // bean definition overriding and circular reference settings govern the whole context lifecycle
        // rather than being applied after beans have already been defined. Both default to true (the
        // historical Grails behavior) and can be turned off via the standard spring.main.* properties.
        new ConfigDataApplicationContextInitializer().initialize(context)
        def environment = context.environment
        beanFactory.allowBeanDefinitionOverriding = environment.getProperty(Settings.SPRING_MAIN_ALLOW_BEAN_DEFINITION_OVERRIDING, Boolean, Boolean.TRUE)
        beanFactory.allowCircularReferences = environment.getProperty(Settings.SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES, Boolean, Boolean.TRUE)

        def classLoader = this.class.classLoader
        // The test's own configuration first, as an application's comes before auto-configuration:
        // parsed ahead of them, its beans are what an auto-configuration's @ConditionalOnMissingBean sees.
        configurationClasses?.each { Class<?> configurationClass ->
            ((AnnotationConfigRegistry) context).register(configurationClass)
        }

        Set<String> includedPluginAutoConfigurations = generatedAutoConfigurationNames(
                registerPluginDiscoveryBean(context, beanFactory))
        ImportCandidates.load(AutoConfiguration, classLoader).asList().findAll {
            (it.startsWith('org.grails')
                    && !it.contains('UrlMappingsAutoConfiguration')) // this currently is causing an issue with tests
            || includedPluginAutoConfigurations.contains(it)
        }.each {
            ((AnnotationConfigRegistry) context).register(ClassUtils.forName(it, classLoader))
        }

        prepareContext(context, beanFactory)
        // Added by hand, so it runs before the configuration classes are read, as an application's
        // early phase does
        context.addBeanFactoryPostProcessor(new IncludedPluginBeansPostProcessor(this, context))
        context.refresh()
        context.registerShutdownHook()
        return context
    }

    protected void prepareContext(ConfigurableApplicationContext applicationContext, ConfigurableBeanFactory beanFactory) {
        def discovery = registerPluginDiscoveryBean(applicationContext, beanFactory)
        registerGrailsAppPostProcessorBean(beanFactory, discovery)
        AnnotationConfigUtils.registerAnnotationConfigProcessors((BeanDefinitionRegistry) beanFactory)
    }

    protected PluginDiscovery registerPluginDiscoveryBean(ConfigurableApplicationContext applicationContext, ConfigurableBeanFactory beanFactory) {
        // Registered while the auto-configurations are chosen, which is before prepareContext asks
        if (beanFactory.containsSingleton(PluginDiscovery.BEAN_NAME)) {
            return (PluginDiscovery) beanFactory.getSingleton(PluginDiscovery.BEAN_NAME)
        }
        def discovery = new DefaultPluginDiscovery()
        // we must load the classpath since the plugin manager needs to find the default plugins
        discovery.pluginFilter = new IncludingPluginFilter(includePlugins ?: DEFAULT_INCLUDED_PLUGINS)
        discovery.init(applicationContext.getEnvironment())
        beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
        discovery
    }

    /**
     * The class each included plugin's {@code beans} block compiles to. Only classes the build listed
     * as auto-configurations are registered, so a name that merely matches is never picked up. The
     * framework's own are registered regardless, with every {@code org.grails} auto-configuration.
     */
    protected static Set<String> generatedAutoConfigurationNames(PluginDiscovery discovery) {
        Set<String> names = new LinkedHashSet<>()
        for (PluginInfo plugin : discovery.pluginsInLoadOrder) {
            if (plugin.pluginClass != null) {
                names << generatedAutoConfigurationName(plugin.pluginClass)
            }
        }
        names
    }

    /**
     * The class a plugin's {@code beans} block compiles to, named as {@code @GrailsBeans} names it:
     * the plugin's {@code autoConfigurationName}, in the plugin's package unless it names one; else
     * {@code FooGrailsPlugin} gives {@code FooAutoConfiguration}, and any other name has
     * {@code AutoConfiguration} appended, in the plugin's package.
     */
    protected static String generatedAutoConfigurationName(Class<?> pluginClass) {
        String packageName = pluginClass.packageName
        String simpleName = pluginClass.simpleName
        String name = declaredAutoConfigurationName(pluginClass)
        if (name == null) {
            String base = simpleName.endsWith(GRAILS_PLUGIN_SUFFIX) && simpleName.length() > GRAILS_PLUGIN_SUFFIX.length()
                    ? simpleName.substring(0, simpleName.length() - GRAILS_PLUGIN_SUFFIX.length())
                    : simpleName
            name = base + AUTO_CONFIGURATION_SUFFIX
        }
        name.contains('.') || !packageName ? name : packageName + '.' + name
    }

    /**
     * {@code @GrailsBeans(autoConfigurationName = ...)} as the plugin wrote it, or {@code null}. The
     * annotation has CLASS retention, so it is read off the plugin's class file rather than through
     * reflection; a plugin can only rename the class by writing it out, so one without it uses the
     * default name.
     */
    private static String declaredAutoConfigurationName(Class<?> pluginClass) {
        InputStream input = pluginClass.classLoader?.getResourceAsStream(pluginClass.name.replace('.', '/') + '.class')
        if (input == null) {
            return null
        }
        String[] declared = new String[1]
        try {
            new ClassReader(input).accept(new ClassVisitor(SpringAsmInfo.ASM_VERSION) {
                @Override
                AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (descriptor != GRAILS_BEANS_DESCRIPTOR) {
                        return null
                    }
                    new AnnotationVisitor(SpringAsmInfo.ASM_VERSION) {
                        @Override
                        void visit(String attribute, Object value) {
                            if (attribute == AUTO_CONFIGURATION_NAME_ATTRIBUTE && value instanceof String) {
                                declared[0] = (String) value
                            }
                        }
                    }
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        }
        catch (IOException ignored) {
            // unreadable here: the default name still finds a plugin that did not rename the class
        }
        finally {
            input.close()
        }
        declared[0]
    }

    void executeDoWithSpringCallback(GrailsApplication grailsApplication) {
        if (!doWithSpring) return
        defineBeans(grailsApplication, doWithSpring)
    }

    void defineBeans(Closure callable) {
        defineBeans(grailsApplication, callable)
    }

    void defineBeans(GrailsApplication grailsApplication, Closure callable) {
        def binding = new Binding()
        def bb = new BeanBuilder(null, null, grailsApplication.classLoader)
        binding.setVariable('application', grailsApplication)
        bb.binding = binding
        bb.beans(callable)
        bb.registerBeans((BeanDefinitionRegistry) grailsApplication.mainContext)
    }

    @CompileDynamic
    void registerBeans(GrailsApplication grailsApplication) {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) grailsApplication.mainContext
        // These stand in for framework beans that give way to an application's own configuration (an
        // auto-configuration's @ConditionalOnMissingBean backs off from it) and to a plugin's beans,
        // so what the test's configuration and the included plugins registered under one of their
        // names is kept.
        Map<String, BeanDefinition> keep = [:]
        pluginBeanDefinitions.each { String name, BeanDefinition definition ->
            if (registry.containsBeanDefinition(name) && registry.getBeanDefinition(name).is(definition)) {
                keep[name] = definition
            }
        }
        keep.putAll(beanDefinitionsDeclaredBy(configurationClasses, registry))

        defineBeans(grailsApplication) { ->

            conversionService(ConversionServiceFactoryBean)

            xmlns(context: 'http://www.springframework.org/schema/context')
            // adds AutowiredAnnotationBeanPostProcessor, CommonAnnotationBeanPostProcessor and others
            // see org.springframework.context.annotation.AnnotationConfigUtils.registerAnnotationConfigProcessors method
            context.'annotation-config'()

            proxyHandler(DefaultProxyHandler)
            messageSource(StaticMessageSource)
            transactionManagerAwarePostProcessor(TransactionManagerPostProcessor)
            grailsPlaceholderConfigurer(GrailsPlaceholderConfigurer, '${', grailsApplication.config.toProperties())
            mapBasedSmartPropertyOverrideConfigurer(MapBasedSmartPropertyOverrideConfigurer) {
                setGrailsApplication(grailsApplication)
            }
        }

        keep.each { String name, BeanDefinition definition ->
            if (!registry.getBeanDefinition(name).is(definition)) {
                registry.registerBeanDefinition(name, definition)
            }
        }
    }

    /**
     * The bean definitions the test's configuration classes declared, a {@code group(...)} nested in
     * one included, read off the {@code @Bean} method each came from.
     */
    protected static Map<String, BeanDefinition> beanDefinitionsDeclaredBy(Collection<Class<?>> configurationClasses,
                                                                         BeanDefinitionRegistry registry) {
        Map<String, BeanDefinition> declared = [:]
        if (!configurationClasses) {
            return declared
        }
        Set<String> owners = configurationClasses*.name as Set<String>
        for (String name : registry.beanDefinitionNames) {
            BeanDefinition definition = registry.getBeanDefinition(name)
            if (!(definition instanceof AnnotatedBeanDefinition)) {
                continue
            }
            String declaringClass = ((AnnotatedBeanDefinition) definition).factoryMethodMetadata?.declaringClassName
            if (declaringClass && owners.any { String owner -> declaringClass == owner || declaringClass.startsWith(owner + '$') }) {
                declared[name] = definition
            }
        }
        declared
    }

    /**
     * Puts what the test's {@code doWithConfig} changed into the Spring {@code Environment} too, ahead
     * of every other property source. {@code grailsApplication.config} alone reaches placeholders but
     * not conditions: {@code @ConditionalOnProperty} and a {@code beans} block's
     * {@code .conditionalOnProperty(...)} read the environment. This runs before the configuration
     * classes are read, so their conditions see it.
     */
    protected static void publishToEnvironment(Properties before, GrailsApplication grailsApplication) {
        def environment = grailsApplication.mainContext?.environment
        if (!(environment instanceof ConfigurableEnvironment)) {
            return
        }
        Map<String, Object> changed = [:]
        grailsApplication.config.toProperties().each { Object key, Object value ->
            if (before.get(key) != value) {
                changed[key as String] = value
            }
        }
        if (changed) {
            ((ConfigurableEnvironment) environment).propertySources
                    .addFirst(new MapPropertySource(DO_WITH_CONFIG_PROPERTY_SOURCE, changed))
        }
    }

    protected void registerGrailsAppPostProcessorBean(ConfigurableBeanFactory beanFactory, PluginDiscovery pluginDiscovery) {

        GrailsApplication grailsApp

        Closure doWithSpringClosure = {
            registerBeans(grailsApp)
            executeDoWithSpringCallback(grailsApp)
        }

        Closure customizeGrailsApplicationClosure = { GrailsApplication grailsApplication ->
            grailsApp = grailsApplication
            if (doWithConfig) {
                Properties before = grailsApplication.config.toProperties()
                doWithConfig.call(grailsApplication.config)
                // reset flatConfig
                grailsApplication.configChanged()
                publishToEnvironment(before, grailsApplication)
            }
            Holders.config = grailsApplication.config
        }

        def constructorArgumentValues = new ConstructorArgumentValues()
        constructorArgumentValues.addIndexedArgumentValue(0, doWithSpringClosure)
        constructorArgumentValues.addIndexedArgumentValue(1, pluginDiscovery)

        def values = new MutablePropertyValues()
        values.add('localOverride', localOverride)
        values.add('beanRegistrar', beanRegistrar)
        values.add('loadExternalBeans', loadExternalBeans)
        values.add('customizeGrailsApplicationClosure', customizeGrailsApplicationClosure)

        def beanDef = new RootBeanDefinition(TestRuntimeGrailsApplicationPostProcessor, constructorArgumentValues, values)
        beanDef.role = BeanDefinition.ROLE_INFRASTRUCTURE
        (beanFactory as BeanDefinitionRegistry).registerBeanDefinition(POST_PROCESSOR_BEAN_NAME, beanDef)
    }

    /**
     * Registers the included plugins' {@code doWithSpring} and {@code beanRegistrar} beans before the
     * configuration classes are read, where an application's early phase registers them, so a
     * {@code @ConditionalOnMissingBean} bean - the framework's, a plugin's own or the test's - backs
     * off from them. The plugins are those the test's {@link TestRuntimeGrailsApplicationPostProcessor}
     * loads, with the test's {@code doWithConfig} already applied.
     */
    @CompileStatic
    static class IncludedPluginBeansPostProcessor implements BeanDefinitionRegistryPostProcessor {

        private final GrailsApplicationBuilder builder
        private final ConfigurableApplicationContext context

        IncludedPluginBeansPostProcessor(GrailsApplicationBuilder builder, ConfigurableApplicationContext context) {
            this.builder = builder
            this.context = context
        }

        @Override
        void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            // A subclass whose registerGrailsAppPostProcessorBean registers another post-processor, or
            // none, is left to register plugin beans the way that post-processor does
            ConfigurableListableBeanFactory beanFactory = context.beanFactory
            if (!beanFactory.containsBean(POST_PROCESSOR_BEAN_NAME) ||
                    !beanFactory.isTypeMatch(POST_PROCESSOR_BEAN_NAME, TestRuntimeGrailsApplicationPostProcessor)) {
                return
            }
            def processor = beanFactory.getBean(POST_PROCESSOR_BEAN_NAME, TestRuntimeGrailsApplicationPostProcessor)
            Map<String, BeanDefinition> before = [:]
            for (String name : registry.beanDefinitionNames) {
                before[name] = registry.getBeanDefinition(name)
            }
            processor.registerPluginBeans(registry)
            for (String name : registry.beanDefinitionNames) {
                BeanDefinition definition = registry.getBeanDefinition(name)
                if (!before[name].is(definition)) {
                    builder.pluginBeanDefinitions[name] = definition
                }
            }
        }

        @Override
        void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        }
    }

    static class TestRuntimeGrailsApplicationPostProcessor extends GrailsApplicationPostProcessor {

        Closure customizeGrailsApplicationClosure
        boolean localOverride = false
        BeanRegistrar beanRegistrar
        private boolean pluginBeansRegistered

        TestRuntimeGrailsApplicationPostProcessor(Closure doWithSpringClosure, PluginDiscovery pluginDiscovery) {
            super([doWithSpring: { -> doWithSpringClosure }] as GrailsApplicationLifeCycle, null, pluginDiscovery)
            loadExternalBeans = false
            reloadingEnabled = false
        }

        /**
         * The plugins' {@code doWithSpring} and {@code beanRegistrar} beans, as
         * {@link #postProcessBeanDefinitionRegistry} would have registered them after the
         * configuration classes; see {@link IncludedPluginBeansPostProcessor}.
         */
        void registerPluginBeans(BeanDefinitionRegistry registry) {
            Holders.setGrailsApplication(grailsApplication)
            def springConfig = new DefaultRuntimeSpringConfiguration()
            pluginManager.doRuntimeConfiguration(springConfig)
            springConfig.registerBeansWithRegistry(registry)
            applyPluginBeanRegistrars(registry)
            pluginBeansRegistered = true
        }

        @Override
        protected boolean isPluginBeanRegistrationDone() {
            pluginBeansRegistered || super.isPluginBeanRegistrationDone()
        }

        @Override
        protected void customizeGrailsApplication(GrailsApplication grailsApplication) {
            customizeGrailsApplicationClosure?.call(grailsApplication)
        }

        @Override
        void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            super.postProcessBeanDefinitionRegistry(registry)
            // Where an application's registrar drains: after the DSL, so it wins a name conflict with doWithSpring
            if (beanRegistrar != null) {
                new BeanRegistryAdapter(registry, applicationContext, applicationContext.environment, beanRegistrar.getClass())
                        .register(beanRegistrar)
            }
            PropertySourcesPlaceholderConfigurer propertySourcePlaceholderConfigurer  = (PropertySourcesPlaceholderConfigurer) grailsApplication.mainContext.getBean('grailsPlaceholderConfigurer')
            propertySourcePlaceholderConfigurer.order = Ordered.HIGHEST_PRECEDENCE
            propertySourcePlaceholderConfigurer.localOverride = localOverride
        }
    }
}
