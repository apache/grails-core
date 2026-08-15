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
package org.grails.plugins

import groovy.transform.CompileStatic

import org.springframework.aop.config.AopConfigUtils
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.config.CustomEditorConfigurer
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.context.annotation.ConfigurationClassPostProcessor
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.Ordered
import org.springframework.core.env.Environment
import org.springframework.core.io.Resource
import org.springframework.util.ClassUtils

import java.beans.PropertyEditor

import grails.compiler.beans.GrailsBeans
import grails.config.Config
import grails.config.ConfigProperties
import grails.config.Settings
import grails.core.GrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.util.BuildSettings
import grails.util.Environment as GrailsEnvironment
import grails.util.GrailsUtil
import org.grails.beans.support.PropertiesEditor
import org.grails.core.io.DefaultResourceLocator
import org.grails.core.support.ClassEditor
import org.grails.dev.support.DevelopmentShutdownHook
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.grails.spring.RuntimeSpringConfigUtilities
import org.grails.spring.RuntimeSpringConfiguration
import org.grails.spring.aop.autoproxy.GroovyAwareAspectJAwareAdvisorAutoProxyCreator
import org.grails.spring.aop.autoproxy.GroovyAwareInfrastructureAdvisorAutoProxyCreator
import org.grails.spring.beans.AbstractResourceLocatorPostProcessor
import org.grails.spring.beans.GrailsApplicationAwareBeanPostProcessor
import org.grails.spring.beans.PluginManagerAwareBeanPostProcessor
import org.grails.spring.context.annotation.GrailsComponentScanPostProcessor
import org.grails.spring.context.support.GrailsPlaceholderConfigurer
import org.grails.spring.context.support.MapBasedSmartPropertyOverrideConfigurer

/**
 * Configures the core shared beans within the Grails application context.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@CompileStatic
@GrailsBeans
@AutoConfiguration(before = [PropertyPlaceholderAutoConfiguration])
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
class CoreGrailsPlugin extends Plugin {

    def version = GrailsUtil.getGrailsVersion()
    def watchedResources = [    'file:./grails-app/conf/spring/resources.xml',
                                'file:./grails-app/conf/spring/resources.groovy',
                                'file:./grails-app/conf/application.groovy',
                                'file:./grails-app/conf/application.yml']

    private static final String SPRING_PROXY_TARGET_CLASS_CONFIG = 'spring.aop.proxy-target-class'

    def beans = {
        bean(ClassLoader).primary() { GrailsApplication grailsApplication ->
            grailsApplication.classLoader
        }

        bean('grailsConfigProperties', ConfigProperties).primary() { GrailsApplication grailsApplication ->
            new ConfigProperties(grailsApplication.config)
        }

        // GroovyPagesGrailsPlugin registers its own caching, GSP-aware locator under this name
        // from doWithSpring, which runs earlier, so this backs off when GSP is present.
        bean('grailsResourceLocator', DefaultResourceLocator).conditionalOnMissingBeanName() {
            new DefaultResourceLocator().tap {
                searchLocations = [BuildSettings.BASE_DIR.absolutePath]
            }
        }

        // A static factory method reading the Environment, rather than an instance method reading an
        // injected @Value field as the hand-written class had it: this bean is a
        // BeanFactoryPostProcessor, so it is created before @Value injection is active and a field
        // would always still be null, leaving the configured prefix with no effect. Static is also
        // Spring's documented shape for a BFPP bean, since it needs no enclosing instance.
        bean(PropertySourcesPlaceholderConfigurer).primary().staticMethod() { Environment environment ->
            def configurer = new GrailsPlaceholderConfigurer()
            String prefix = environment.getProperty(Settings.SPRING_PLACEHOLDER_PREFIX)
            if (prefix != null) {
                configurer.placeholderPrefix = prefix
            }
            configurer
        }
    }

    /**
     * Scans the packages named by {@code grails.spring.bean.packages}. Contributed here rather
     * than through {@code doWithSpring}'s {@code grailsContext:component-scan} element, which
     * needs the XML namespace handler and therefore the bean builder DSL.
     */
    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment springEnvironment ->
            GrailsApplication application = grailsApplication
            Config config = application.config

            // enable post-processing of @Configuration beans defined by plugins
            registry.registerBean('grailsConfigurationClassPostProcessor', ConfigurationClassPostProcessor)

            registry.registerBean('grailsBeanOverrideConfigurer', MapBasedSmartPropertyOverrideConfigurer) {
                it.supplier {
                    MapBasedSmartPropertyOverrideConfigurer configurer = new MapBasedSmartPropertyOverrideConfigurer()
                    configurer.grailsApplication = application
                    configurer
                }
            }

            // replace the AutoProxy advisor with a Groovy aware one; the two variants share
            // Spring's own internalAutoProxyCreator name, so at most one is registered
            Boolean isProxyTargetClass = config.getProperty(SPRING_PROXY_TARGET_CLASS_CONFIG, Boolean)
            if (ClassUtils.isPresent('org.aspectj.lang.annotation.Around', application.classLoader) &&
                    !config.getProperty(Settings.SPRING_DISABLE_ASPECTJ, Boolean)) {
                registry.registerBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME, GroovyAwareAspectJAwareAdvisorAutoProxyCreator) {
                    it.supplier {
                        GroovyAwareAspectJAwareAdvisorAutoProxyCreator creator = new GroovyAwareAspectJAwareAdvisorAutoProxyCreator()
                        if (isProxyTargetClass != null) {
                            creator.proxyTargetClass = isProxyTargetClass
                        }
                        creator
                    }
                }
            }
            else {
                registry.registerBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME, GroovyAwareInfrastructureAdvisorAutoProxyCreator) {
                    it.supplier {
                        GroovyAwareInfrastructureAdvisorAutoProxyCreator creator = new GroovyAwareInfrastructureAdvisorAutoProxyCreator()
                        if (isProxyTargetClass != null) {
                            creator.proxyTargetClass = isProxyTargetClass
                        }
                        creator
                    }
                }
            }

            registry.registerBean('grailsApplicationAwarePostProcessor', GrailsApplicationAwareBeanPostProcessor) {
                it.supplier { new GrailsApplicationAwareBeanPostProcessor(application) }
            }
            registry.registerBean('pluginManagerPostProcessor', PluginManagerAwareBeanPostProcessor)

            // a shutdown hook only outside war deployment, in development, with jline present
            if (!GrailsEnvironment.isWarDeployed() && environment == GrailsEnvironment.DEVELOPMENT &&
                    ClassUtils.isPresent('jline.Terminal', application.classLoader)) {
                registry.registerBean('shutdownHook', DevelopmentShutdownHook)
            }

            registry.registerBean('customEditors', CustomEditorConfigurer) {
                it.supplier {
                    CustomEditorConfigurer configurer = new CustomEditorConfigurer()
                    Map<Class<?>, Class<? extends PropertyEditor>> editors = [:]
                    editors.put(Class, ClassEditor)
                    editors.put(Properties, PropertiesEditor)
                    configurer.customEditors = editors
                    configurer
                }
            }

            registry.registerBean('proxyHandler', DefaultProxyHandler)

            // an abstract parent definition, which registerBean cannot express since it always
            // takes a class; third-party plugins inherit their search locations from it
            registry.registerBean('grailsAbstractResourceLocatorPostProcessor', AbstractResourceLocatorPostProcessor) {
                it.infrastructure().supplier {
                    new AbstractResourceLocatorPostProcessor([BuildSettings.BASE_DIR.absolutePath])
                }
            }

            List<String> packagesToScan = (List<String>) grailsApplication.config
                    .getProperty(Settings.SPRING_BEAN_PACKAGES, List) ?: []
            if (!packagesToScan) {
                return
            }
            GrailsPluginManager pluginManager = manager
            registry.registerBean('grailsComponentScanPostProcessor', GrailsComponentScanPostProcessor) {
                it.infrastructure().supplier {
                    new GrailsComponentScanPostProcessor(packagesToScan, pluginManager)
                }
            }
        }
    }

    @Override
    @CompileStatic
    void onChange(Map<String, Object> event) {
        GenericApplicationContext applicationContext = (GenericApplicationContext) this.applicationContext
        if (event.source instanceof Resource) {
            Resource res = (Resource) event.source
            if (res.filename.endsWith('.xml')) {
                def xmlBeans = new DefaultListableBeanFactory()
                new XmlBeanDefinitionReader(xmlBeans).loadBeanDefinitions(res)
                for (String beanName in xmlBeans.beanDefinitionNames) {
                    applicationContext.registerBeanDefinition(beanName, xmlBeans.getBeanDefinition(beanName))
                }
            }
        }
        else if (event.source instanceof Class) {
            def clazz = (Class) event.source
            if (Script.isAssignableFrom(clazz)) {
                RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration(applicationContext)
                RuntimeSpringConfigUtilities.reloadSpringResourcesConfig(springConfig, grailsApplication, clazz)
                springConfig.registerBeansWithContext(applicationContext)
            }
        }
    }

}
