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
import org.codehaus.groovy.runtime.InvokerHelper

import jakarta.servlet.ServletContext

import org.springframework.beans.BeansException
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
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
import org.springframework.util.ClassUtils

import grails.boot.config.GrailsApplicationPostProcessor
import grails.core.GrailsApplication
import grails.core.GrailsApplicationLifeCycle
import grails.core.support.proxy.DefaultProxyHandler
import grails.plugins.GrailsPluginManager
import grails.spring.BeanBuilder
import grails.util.Holders
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.filters.IncludingPluginFilter
import org.apache.grails.core.plugins.PluginDiscovery
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

    Closure doWithSpring
    Closure doWithConfig
    Set<String> includePlugins
    boolean loadExternalBeans
    boolean localOverride = false

    GrailsApplication grailsApplication
    Object servletContext

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
                InvokerHelper.invokeStaticMethod(
                        gcu,
                        'configureServletContextAttributes',
                        [
                                servletContext,
                                grailsApplication,
                                mainContext.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager),
                                mainContext
                        ] as Object[]
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
            // Spring Boot 4.0/Spring 7.0: Use GenericWebApplicationContext with manual annotation support
            // instead of removed AnnotationConfigServletWebApplicationContext
            context = (ConfigurableApplicationContext) ClassUtils
                    .forName('org.springframework.web.context.support.GenericWebApplicationContext')
                    .getDeclaredConstructor(ServletContext)
                    .newInstance((ServletContext) servletContext)

            // Register annotation config processors manually
            def beanFactory = context.beanFactory
            AnnotationConfigUtils.registerAnnotationConfigProcessors((BeanDefinitionRegistry) beanFactory)
            
            // Register auto-configuration classes
            def classLoader = this.class.classLoader
            ImportCandidates.load(AutoConfiguration, classLoader).asList().findAll {
                it.startsWith('org.grails')
                && !it.contains('UrlMappingsAutoConfiguration') // this currently is causing an issue with tests
            }.each {
                def clazz = ClassUtils.forName(it, classLoader)
                def beanDef = new RootBeanDefinition(clazz)
                ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(it, beanDef)
            }
        } else {
            context = (ConfigurableApplicationContext) ClassUtils
                    .forName('org.springframework.context.annotation.AnnotationConfigApplicationContext')
                    .getDeclaredConstructor()
                    .newInstance()
            
            def classLoader = this.class.classLoader
            ImportCandidates.load(AutoConfiguration, classLoader).asList().findAll {
                it.startsWith('org.grails')
                && !it.contains('UrlMappingsAutoConfiguration') // this currently is causing an issue with tests
            }.each {
                ((AnnotationConfigRegistry) context).register(ClassUtils.forName(it, classLoader))
            }
        }

        def beanFactory = (context.beanFactory as DefaultListableBeanFactory).tap {
            allowBeanDefinitionOverriding = true
            allowCircularReferences = true
        }
        prepareContext(context, beanFactory)
        context.refresh()
        context.registerShutdownHook()
        return context
    }

    protected void prepareContext(ConfigurableApplicationContext applicationContext, ConfigurableBeanFactory beanFactory) {
        def discovery = registerPluginDiscoveryBean(applicationContext, beanFactory)
        registerGrailsAppPostProcessorBean(beanFactory, discovery)
        AnnotationConfigUtils.registerAnnotationConfigProcessors((BeanDefinitionRegistry) beanFactory)
        new ConfigDataApplicationContextInitializer().initialize(applicationContext)
    }

    protected PluginDiscovery registerPluginDiscoveryBean(ConfigurableApplicationContext applicationContext, ConfigurableBeanFactory beanFactory) {
        def discovery = new DefaultPluginDiscovery()
        // we must load the classpath since the plugin manager needs to find the default plugins
        discovery.pluginFilter = new IncludingPluginFilter(includePlugins ?: DEFAULT_INCLUDED_PLUGINS)
        discovery.init(applicationContext.getEnvironment())
        beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
        discovery
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
                doWithConfig.call(grailsApplication.config)
                // reset flatConfig
                grailsApplication.configChanged()
            }
            Holders.config = grailsApplication.config
        }

        def constructorArgumentValues = new ConstructorArgumentValues()
        constructorArgumentValues.addIndexedArgumentValue(0, doWithSpringClosure)
        constructorArgumentValues.addIndexedArgumentValue(1, pluginDiscovery)

        def values = new MutablePropertyValues()
        values.add('localOverride', localOverride)
        values.add('loadExternalBeans', loadExternalBeans)
        values.add('customizeGrailsApplicationClosure', customizeGrailsApplicationClosure)

        def beanDef = new RootBeanDefinition(TestRuntimeGrailsApplicationPostProcessor, constructorArgumentValues, values)
        beanDef.role = BeanDefinition.ROLE_INFRASTRUCTURE
        (beanFactory as BeanDefinitionRegistry).registerBeanDefinition('grailsApplicationPostProcessor', beanDef)
    }

    static class TestRuntimeGrailsApplicationPostProcessor extends GrailsApplicationPostProcessor {

        Closure customizeGrailsApplicationClosure
        boolean localOverride = false

        TestRuntimeGrailsApplicationPostProcessor(Closure doWithSpringClosure, PluginDiscovery pluginDiscovery) {
            super([doWithSpring: { -> doWithSpringClosure }] as GrailsApplicationLifeCycle, null, pluginDiscovery)
            loadExternalBeans = false
            reloadingEnabled = false
        }

        @Override
        protected void customizeGrailsApplication(GrailsApplication grailsApplication) {
            customizeGrailsApplicationClosure?.call(grailsApplication)
        }

        @Override
        void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            super.postProcessBeanDefinitionRegistry(registry)
            PropertySourcesPlaceholderConfigurer propertySourcePlaceholderConfigurer  = (PropertySourcesPlaceholderConfigurer) grailsApplication.mainContext.getBean('grailsPlaceholderConfigurer')
            propertySourcePlaceholderConfigurer.order = Ordered.HIGHEST_PRECEDENCE
            propertySourcePlaceholderConfigurer.localOverride = localOverride
        }
    }
}
