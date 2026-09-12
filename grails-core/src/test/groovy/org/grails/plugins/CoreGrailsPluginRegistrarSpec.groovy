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

import java.beans.PropertyEditor

import org.springframework.aop.config.AopConfigUtils
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.CustomEditorConfigurer
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.util.BuildSettings

import org.grails.beans.support.PropertiesEditor
import org.grails.core.io.DefaultResourceLocator
import org.grails.core.support.ClassEditor
import org.grails.spring.aop.autoproxy.GroovyAwareAspectJAwareAdvisorAutoProxyCreator
import org.grails.spring.aop.autoproxy.GroovyAwareInfrastructureAdvisorAutoProxyCreator
import org.grails.spring.beans.AbstractResourceLocatorPostProcessor

import spock.lang.Specification

/**
 * Covers the beans {@code CoreGrailsPlugin} contributes through {@code beanRegistrar()}. The beans
 * it contributes through its {@code beans} DSL are covered by {@link CoreAutoConfigurationSpec},
 * and the {@code grails.spring.bean.packages} scan by {@link SpringBeanPackagesSpec}.
 */
class CoreGrailsPluginRegistrarSpec extends Specification {

    void 'the auto proxy creator is the AspectJ aware variant when AspectJ is available'() {
        given:
        GenericApplicationContext context = buildContext(new DefaultGrailsApplication())

        expect:
        context.getBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME) instanceof GroovyAwareAspectJAwareAdvisorAutoProxyCreator

        cleanup:
        context.close()
    }

    void 'AspectJ auto-weaving can be turned off, leaving the infrastructure advisor'() {
        given:
        GrailsApplication application = new DefaultGrailsApplication()
        application.config.put(Settings.SPRING_DISABLE_ASPECTJ, true)

        when:
        GenericApplicationContext context = buildContext(application)

        then:
        context.getBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME) instanceof GroovyAwareInfrastructureAdvisorAutoProxyCreator

        cleanup:
        context.close()
    }

    void 'the infrastructure advisor is used when AspectJ is off the application class path'() {
        given:
        GrailsApplication application = new DefaultGrailsApplication()
        application.classLoader = new AspectJHidingClassLoader(getClass().classLoader)

        when:
        GenericApplicationContext context = buildContext(application)

        then:
        context.getBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME) instanceof GroovyAwareInfrastructureAdvisorAutoProxyCreator

        cleanup:
        context.close()
    }

    void "Spring Boot's AOP auto-configuration starts on top of the registered auto proxy creator"() {
        given: 'a plain Spring Boot context - nothing here loads GrailsAutoConfiguration'
        GrailsApplication application = new DefaultGrailsApplication()

        expect: 'the auto-configuration accepts the registered creator instead of rejecting it as unknown'
        new ApplicationContextRunner()
                .withInitializer(applyRegistrar(application))
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration))
                .run { context ->
                    assert context.getBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME) instanceof
                            GroovyAwareAspectJAwareAdvisorAutoProxyCreator
                }
    }

    void 'the Class and Properties editors are registered'() {
        given:
        GenericApplicationContext context = buildContext(new DefaultGrailsApplication())

        when:
        Map<Class<?>, Class<? extends PropertyEditor>> editors =
                context.getBean('customEditors', CustomEditorConfigurer).customEditors

        then:
        editors[Class] == ClassEditor
        editors[Properties] == PropertiesEditor

        cleanup:
        context.close()
    }

    void 'a proxy handler is available'() {
        given:
        GenericApplicationContext context = buildContext(new DefaultGrailsApplication())

        expect:
        context.getBean(ProxyHandler) instanceof DefaultProxyHandler

        cleanup:
        context.close()
    }

    void 'the abstract resource locator parent is registered and can be inherited from'() {
        given: 'a definition naming the parent the way a third-party plugin does'
        GenericApplicationContext context = buildContext(new DefaultGrailsApplication()) {
            GenericBeanDefinition child = new GenericBeanDefinition()
            child.beanClass = DefaultResourceLocator
            child.parentName = AbstractResourceLocatorPostProcessor.BEAN_NAME
            it.registerBeanDefinition('inheritingLocator', child)
        }

        when:
        BeanDefinition merged = context.beanFactory.getMergedBeanDefinition('inheritingLocator')

        then: 'the parent exists, is abstract, and its property reaches the child'
        context.getBeanDefinition(AbstractResourceLocatorPostProcessor.BEAN_NAME).abstract
        merged.propertyValues.getPropertyValue('searchLocations').value == [BuildSettings.BASE_DIR.absolutePath]

        and: 'so the child is instantiable'
        context.getBean('inheritingLocator') instanceof DefaultResourceLocator

        cleanup:
        context.close()
    }

    void 'a GrailsApplicationAware bean is handed the application'() {
        given:
        GrailsApplication application = new DefaultGrailsApplication()

        when:
        GenericApplicationContext context = buildContext(application) {
            it.registerBeanDefinition('aware', new RootBeanDefinition(AwareProbe))
        }

        then:
        context.getBean(AwareProbe).grailsApplication.is(application)

        cleanup:
        context.close()
    }

    void 'bean properties are overridden from the beans config block'() {
        given:
        GrailsApplication application = new DefaultGrailsApplication()
        application.config.put('beans', [probe: [value: 'overridden']])

        when:
        GenericApplicationContext context = buildContext(application) {
            it.registerBeanDefinition('probe', new RootBeanDefinition(ValueProbe))
        }

        then:
        context.getBean('probe', ValueProbe).value == 'overridden'

        cleanup:
        context.close()
    }

    private static GenericApplicationContext buildContext(GrailsApplication application,
                                                          Closure<?> customizer = null) {
        GenericApplicationContext context = new GenericApplicationContext()
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        customizer?.call(context)
        registerBeans(application, context)
        context.refresh()
        context
    }

    private static ApplicationContextInitializer<ConfigurableApplicationContext> applyRegistrar(GrailsApplication application) {
        return { ConfigurableApplicationContext context ->
            context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
            registerBeans(application, (GenericApplicationContext) context)
        } as ApplicationContextInitializer<ConfigurableApplicationContext>
    }

    private static void registerBeans(GrailsApplication application, GenericApplicationContext context) {
        CoreGrailsPlugin plugin = new CoreGrailsPlugin()
        plugin.grailsApplication = application

        BeanRegistrar registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(context, context.beanFactory, context.environment, registrar.getClass())
                .register(registrar)
    }

    /**
     * Stands in for a deployment without AspectJ, which the plugin detects by asking the
     * application's class loader for {@code org.aspectj.lang.annotation.Around}.
     */
    static class AspectJHidingClassLoader extends ClassLoader {

        AspectJHidingClassLoader(ClassLoader parent) {
            super(parent)
        }

        @Override
        Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith('org.aspectj.')) {
                throw new ClassNotFoundException(name)
            }
            super.loadClass(name)
        }

    }

    static class AwareProbe implements GrailsApplicationAware {

        GrailsApplication grailsApplication

    }

    static class ValueProbe {

        String value

    }

}
