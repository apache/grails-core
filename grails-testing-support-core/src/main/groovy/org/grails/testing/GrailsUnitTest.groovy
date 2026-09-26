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
import java.lang.reflect.Modifier

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.util.ClassUtils

import grails.compiler.beans.GrailsBeans
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
                    beanRegistrar: beanRegistrar(),
                    configurationClasses: getConfigurationClasses(),
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

    /**
     * Applies a plugin's {@code doWithSpring()} and {@code beanRegistrar()} to the test application
     * context. A plugin's {@code beans} block is not among them: it compiles to an auto-configuration,
     * which is registered before the context refreshes - include the plugin through
     * {@link #getIncludePlugins()} for that.
     */
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

    /**
     * Beans for the test application context in the bean builder DSL.
     *
     * @deprecated since 8.0, as {@code doWithSpring()} is on plugins and applications, in favour of a
     * {@code beans} block, {@link #beanRegistrar()} or a configuration class (see
     * {@link #getConfigurationClasses()}). The DSL keeps working but receives no fixes for new issues.
     */
    @Deprecated(since = '8.0')
    Closure doWithSpring() {
        null
    }

    /**
     * Registers beans in the test application context, the way an application's or a plugin's
     * {@code beanRegistrar()} does at boot and at the same point: after {@link #doWithSpring()}, so a
     * registrar bean wins a name conflict with the deprecated DSL.
     *
     * @return the registrar, or {@code null} (the default) to register nothing
     * @since 8.0
     */
    BeanRegistrar beanRegistrar() {
        null
    }

    /**
     * Configuration classes for the test application context. They are registered ahead of the
     * framework's auto-configurations, as an application's own configuration is, so an
     * auto-configuration's {@code @ConditionalOnMissingBean} backs off from the beans they declare.
     *
     * <p>By default, the static nested classes of the test, and of any test it extends, annotated
     * {@code @Configuration} directly or through another annotation such as {@code @AutoConfiguration}
     * - the convention Spring's own test support follows. A test's {@code beans} block compiles into
     * one of them, {@code BeansConfiguration}. Override to register other classes instead.</p>
     *
     * @since 8.0
     */
    Set<Class<?>> getConfigurationClasses() {
        nestedConfigurationClasses(getClass())
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

    /**
     * Outermost test class first, so a nested class in a subclass is registered later and wins a
     * bean name the two share. Within one class the one its {@code beans} block compiles to comes
     * last, so the block wins a name it shares with a hand-written nested class; the order of the
     * hand-written ones is {@code getDeclaredClasses()}'s, which Java does not specify.
     */
    private Set<Class<?>> nestedConfigurationClasses(Class<?> testClass) {
        List<Class<?>> hierarchy = []
        for (Class<?> type = testClass; type != null && type != Object; type = type.superclass) {
            hierarchy.add(0, type)
        }
        Set<Class<?>> found = new LinkedHashSet<>()
        for (Class<?> type : hierarchy) {
            List<Class<?>> declared = type.declaredClasses.findAll { Class<?> nested ->
                int modifiers = nested.modifiers
                Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers) && !Modifier.isFinal(modifiers) &&
                        AnnotatedElementUtils.hasAnnotation(nested, Configuration)
            }
            // stable, so only the generated class moves
            found.addAll(declared.sort(false) { Class<?> nested ->
                nested.simpleName == GrailsBeans.UNIT_TEST_CONFIGURATION_NAME ? 1 : 0
            })
        }
        found
    }

    @CompileDynamic
    private void cleanupPromiseFactory() {
        ClassLoader classLoader = getClass().classLoader
        if (ClassUtils.isPresent('grails.async.Promises', classLoader)) {
            getClass().classLoader.loadClass('grails.async.Promises')['promiseFactory'] = null
        }
    }
}
