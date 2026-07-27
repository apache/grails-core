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

import java.lang.reflect.Method

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.MessageSource
import org.springframework.util.ClassUtils

import grails.config.Config
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.spring.BeanBuilder
import grails.util.Holders
import grails.validation.DeferredBindingActions
import org.grails.core.lifecycle.ShutdownOperations

@CompileStatic
trait GrailsUnitTest {

    private static GrailsApplication _grailsApplication
    private static Object _servletContext

    boolean getLocalOverride() {
        false
    }

    /**
     * @return the servlet context
     */
    Object getOptionalServletContext() {
        _servletContext
    }

    /**
     *
     * @return grailsApplication.mainContext
     */
    ConfigurableApplicationContext getApplicationContext() {
        (ConfigurableApplicationContext) grailsApplication.mainContext
    }

    /**
     *
     * @return The GrailsApplication instance
     */
    GrailsApplication getGrailsApplication() {
        if (_grailsApplication == null) {
            def builder = new GrailsApplicationBuilder(
                    doWithSpring: doWithSpring(),
                    doWithConfig: doWithConfig(),
                    includePlugins: getIncludePlugins(),
                    loadExternalBeans: loadExternalBeans(),
                    localOverride: localOverride
            ).build()
            _grailsApplication = builder.grailsApplication
            _servletContext = builder.servletContext
        }
        _grailsApplication
    }

    /**
     *
     * @return grailsApplication.config
     */
    Config getConfig() {
        grailsApplication.config
    }

    /**
     *
     * @return the MessageSource bean from the application context
     */
    MessageSource getMessageSource() {
        applicationContext.getBean('messageSource', MessageSource)
    }

    void defineBeans(Closure closure) {
        def binding = new Binding()
        def bb = new BeanBuilder(null, null, grailsApplication.getClassLoader())
        binding.setVariable('application', grailsApplication)
        bb.setBinding(binding)
        bb.beans(closure)
        bb.registerBeans((BeanDefinitionRegistry) applicationContext)
        applicationContext.beanFactory.preInstantiateSingletons()
    }

    /**
     * Applies a {@link BeanRegistrar} to the test application context, the same way a plugin's
     * {@code beanRegistrar()} hook is applied by the framework at boot time.
     */
    void defineBeans(BeanRegistrar registrar) {
        ConfigurableApplicationContext context = applicationContext
        new BeanRegistryAdapter((BeanDefinitionRegistry) context, context.beanFactory,
                context.environment, registrar.getClass()).register(registrar)
        context.beanFactory.preInstantiateSingletons()
    }

    void defineBeans(Object plugin) {
        Class clazz = plugin.getClass()
        // Mirror the boot order: the doWithSpring() DSL is applied first and the beanRegistrar()
        // second, so a plugin defining both hooks (e.g. one that is mid-migration) gets both sets
        // of beans and registrar beans win any name conflicts with the deprecated DSL
        Closure dsl = null
        try {
            dsl = (Closure) clazz.getMethod('doWithSpring').invoke(plugin)
        } catch (NoSuchMethodException ignored) {}
        if (dsl == null) {
            try {
                dsl = (Closure) clazz.getMethod('getDoWithSpring').invoke(plugin)
            } catch (NoSuchMethodException ignored) {}
        }
        if (dsl != null) {
            defineBeans(dsl)
        }

        try {
            Method beanRegistrarMethod = clazz.getMethod('beanRegistrar')
            BeanRegistrar registrar = (BeanRegistrar) beanRegistrarMethod.invoke(plugin)
            if (registrar != null) {
                defineBeans(registrar)
            }
        } catch (NoSuchMethodException ignored) {}
    }

    Closure doWithSpring() {
        null
    }

    Closure doWithConfig() {
        null
    }

    Set<String> getIncludePlugins() {
        new HashSet<String>()
    }

    boolean loadExternalBeans() {
        false
    }

    void cleanupGrailsApplication() {
        if (_grailsApplication != null) {
            if (_grailsApplication instanceof DefaultGrailsApplication) {
                ((DefaultGrailsApplication) _grailsApplication).clear()
            }

            ApplicationContext applicationContext = _grailsApplication.getParentContext()
            if (applicationContext instanceof ConfigurableApplicationContext) {
                if (((ConfigurableApplicationContext) applicationContext).isActive()) {
                    if (_grailsApplication.mainContext instanceof Closeable) {
                        ((Closeable) _grailsApplication.mainContext).close()
                    }
                    if (applicationContext instanceof Closeable) {
                        ((Closeable) applicationContext).close()
                    }
                }
            }

            ShutdownOperations.runOperations()
            DeferredBindingActions.clear()

            this._grailsApplication = null
            this._servletContext = null

            cleanupPromiseFactory()
            Holders.clear()
        }
    }

    @CompileDynamic
    private void cleanupPromiseFactory() {
        ClassLoader classLoader = getClass().classLoader
        if (ClassUtils.isPresent('grails.async.Promises', classLoader)) {
            getClass().classLoader.loadClass('grails.async.Promises')['promiseFactory'] = null
        }
    }
}
