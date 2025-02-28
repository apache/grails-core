/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.testing

import grails.config.Config
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.spring.BeanBuilder
import grails.util.Holders
import grails.validation.DeferredBindingActions
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.core.lifecycle.ShutdownOperations
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.MessageSource
import org.springframework.util.ClassUtils

import java.lang.reflect.Method

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
        applicationContext.getBean("messageSource", MessageSource)
    }

    void defineBeans(Closure closure) {
        def binding = new Binding()
        def bb = new BeanBuilder(null, null, grailsApplication.getClassLoader())
        binding.setVariable "application", grailsApplication
        bb.setBinding binding
        bb.beans(closure)
        bb.registerBeans((BeanDefinitionRegistry)applicationContext)
        applicationContext.beanFactory.preInstantiateSingletons()
    }

    void defineBeans(Object plugin) {
        Class clazz = plugin.getClass()
        try {
            Method doWithSpringMethod = clazz.getMethod('doWithSpring')
            Closure config = (Closure)doWithSpringMethod.invoke(plugin)
            if (config != null) {
                defineBeans(config)
                return
            }
        } catch (NoSuchMethodException e) {}

        try {
            Method doWithSpringField = clazz.getMethod('getDoWithSpring')
            defineBeans((Closure)doWithSpringField.invoke(plugin))
        } catch (NoSuchFieldException e) {}
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
                ((DefaultGrailsApplication)_grailsApplication).clear()
            }

            ApplicationContext applicationContext = grailsApplication.getParentContext()

            if (applicationContext instanceof ConfigurableApplicationContext) {
                if (((ConfigurableApplicationContext) applicationContext).isActive()) {
                    if(grailsApplication.mainContext instanceof Closeable) {
                        ((Closeable)grailsApplication.mainContext).close()
                    }
                    if (applicationContext instanceof Closeable) {
                        ((Closeable)applicationContext).close()
                    }
                }
            }

            ShutdownOperations.runOperations()
            DeferredBindingActions.clear()

            this._grailsApplication = null
            cleanupPromiseFactory()
            Holders.clear()
        }
    }

    @CompileDynamic
    private void cleanupPromiseFactory() {
        ClassLoader classLoader = getClass().classLoader
        if (ClassUtils.isPresent("grails.async.Promises", classLoader)) {
            getClass().classLoader.loadClass("grails.async.Promises")['promiseFactory'] = null
        }
    }
}
