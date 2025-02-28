/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins

import grails.config.Settings
import grails.plugins.Plugin
import grails.util.BuildSettings
import grails.util.Environment
import grails.util.GrailsUtil
import groovy.transform.CompileStatic
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.grails.spring.RuntimeSpringConfiguration
import org.grails.spring.aop.autoproxy.GroovyAwareAspectJAwareAdvisorAutoProxyCreator
import org.grails.spring.aop.autoproxy.GroovyAwareInfrastructureAdvisorAutoProxyCreator
import org.grails.spring.context.support.MapBasedSmartPropertyOverrideConfigurer
import org.grails.spring.RuntimeSpringConfigUtilities
import org.grails.core.io.DefaultResourceLocator
import org.grails.spring.beans.GrailsApplicationAwareBeanPostProcessor
import org.grails.spring.beans.PluginManagerAwareBeanPostProcessor
import org.grails.core.support.ClassEditor
import org.grails.dev.support.DevelopmentShutdownHook
import org.grails.beans.support.PropertiesEditor
import grails.core.support.proxy.DefaultProxyHandler
import org.springframework.beans.factory.config.CustomEditorConfigurer
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader
import org.springframework.context.annotation.ConfigurationClassPostProcessor
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.Resource
import org.springframework.util.ClassUtils

/**
 * Configures the core shared beans within the Grails application context.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
class CoreGrailsPlugin extends Plugin {

    def version = GrailsUtil.getGrailsVersion()
    def watchedResources = [    "file:./grails-app/conf/spring/resources.xml",
                                "file:./grails-app/conf/spring/resources.groovy",
                                "file:./grails-app/conf/application.groovy",
                                "file:./grails-app/conf/application.yml"]

    private static final SPRING_PROXY_TARGET_CLASS_CONFIG = "spring.aop.proxy-target-class"

    @Override
    Closure doWithSpring() { {->

        def application = grailsApplication

        // Grails config as properties
        def config = application.config

        // enable post-processing of @Configuration beans defined by plugins
        grailsConfigurationClassPostProcessor ConfigurationClassPostProcessor
        grailsBeanOverrideConfigurer(MapBasedSmartPropertyOverrideConfigurer) {
            delegate.grailsApplication = application
        }

        Class proxyCreatorClazz = null
        // replace AutoProxy advisor with Groovy aware one
        if (ClassUtils.isPresent('org.aspectj.lang.annotation.Around', application.classLoader) && !config.getProperty(Settings.SPRING_DISABLE_ASPECTJ, Boolean)) {
            proxyCreatorClazz = GroovyAwareAspectJAwareAdvisorAutoProxyCreator
        } else {
            proxyCreatorClazz = GroovyAwareInfrastructureAdvisorAutoProxyCreator
        }

        Boolean isProxyTargetClass = config.getProperty(SPRING_PROXY_TARGET_CLASS_CONFIG, Boolean)
        "org.springframework.aop.config.internalAutoProxyCreator"(proxyCreatorClazz) {
            if (isProxyTargetClass != null) {
                proxyTargetClass = isProxyTargetClass
            }
        }

        def packagesToScan = []

        def beanPackages = config.getProperty(Settings.SPRING_BEAN_PACKAGES, List)
        if (beanPackages) {
            packagesToScan += beanPackages
        }


        if (packagesToScan) {
            xmlns grailsContext:"http://grails.org/schema/context"
            grailsContext.'component-scan'('base-package':packagesToScan.join(','))
        }

        grailsApplicationAwarePostProcessor(GrailsApplicationAwareBeanPostProcessor, ref("grailsApplication"))
        pluginManagerPostProcessor(PluginManagerAwareBeanPostProcessor)

        // add shutdown hook if not running in war deployed mode
        final warDeployed = Environment.isWarDeployed()
        final devMode = !warDeployed && environment == Environment.DEVELOPMENT
        if (devMode && ClassUtils.isPresent('jline.Terminal', application.classLoader)) {
            shutdownHook(DevelopmentShutdownHook)
        }
        abstractGrailsResourceLocator {
            searchLocations = [BuildSettings.BASE_DIR.absolutePath]
        }
        grailsResourceLocator(DefaultResourceLocator) { bean ->
            bean.parent = "abstractGrailsResourceLocator"
        }

        customEditors(CustomEditorConfigurer) {
            customEditors = [(Class): ClassEditor,
                             (Properties): PropertiesEditor]
        }

        proxyHandler(DefaultProxyHandler)
    }}

    @Override
    @CompileStatic
    void onChange(Map<String, Object> event) {
        GenericApplicationContext applicationContext = (GenericApplicationContext)this.applicationContext
        if (event.source instanceof Resource) {
            Resource res = (Resource)event.source
            if(res.filename.endsWith('.xml')) {
                def xmlBeans = new DefaultListableBeanFactory()
                new XmlBeanDefinitionReader(xmlBeans).loadBeanDefinitions(res)
                for(String beanName in xmlBeans.beanDefinitionNames) {
                    applicationContext.registerBeanDefinition(beanName, xmlBeans.getBeanDefinition(beanName))
                }
            }
        }
        else if (event.source instanceof Class) {
            def clazz = (Class) event.source
            if(Script.isAssignableFrom(clazz)) {
                RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration(applicationContext)
                RuntimeSpringConfigUtilities.reloadSpringResourcesConfig(springConfig, grailsApplication, clazz)
                springConfig.registerBeansWithContext(applicationContext)
            }
        }
    }

}
