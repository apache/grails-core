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
package grails.plugins

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext

import grails.config.Config
import grails.core.ArtefactHandler
import grails.core.GrailsApplication
import grails.core.GrailsApplicationLifeCycle
import grails.core.support.GrailsApplicationAware
import grails.spring.BeanBuilder
import grails.util.Environment
import org.grails.spring.context.support.MapBasedSmartPropertyOverrideConfigurer

/**
 * Super class for plugins to implement. Plugin implementations should define the various plugin hooks
 * (doWithSpring, doWithApplicationContext, doWithDynamicMethods etc.)
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
abstract class Plugin implements GrailsApplicationLifeCycle, GrailsApplicationAware, ApplicationContextAware, PluginManagerAware {

    /**
     * The {@link GrailsApplication} instance
     */
    GrailsApplication grailsApplication
    /**
     * The {@link grails.plugins.GrailsPlugin} definition for this plugin
     */
    GrailsPlugin plugin
    /**
     * The {@link GrailsPluginManager} instance
     */
    GrailsPluginManager pluginManager

    /**
     * The current Grails {@link Environment}
     */
    Environment environment = Environment.current

    /**
     * @return The {@link Config} instance for this plugin
     */
    Config getConfig() { grailsApplication.config }
    /**
     * The {@link GrailsPluginManager} instance
     */
    GrailsPluginManager getManager() { pluginManager }

    /**
     * Whether the plugin is enabled
     */
    public boolean enabled = true

    /**
     * List of {@link ArtefactHandler} instances provided by this plugin
     */
    final List<ArtefactHandler> artefacts = []

    /**
     * The {@link ApplicationContext} instance
     */
    private ApplicationContext applicationContext

    /**
     * @return The ApplicationContext
     */
    ConfigurableApplicationContext getApplicationContext() {
        if (applicationContext instanceof ConfigurableApplicationContext) {
            return (ConfigurableApplicationContext) applicationContext
        }
        return null
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext
    }
/**
     * Sub classes should override to provide implementations
     *
     * @return A closure that defines beans to be executed by Spring
     * @deprecated since 8.0 in favour of {@link #beanRegistrar()}. The underlying bean builder DSL
     * remains available but is no longer actively supported and will not receive fixes for new
     * issues — you are strongly urged to migrate to {@link #beanRegistrar()}.
     */
    @Deprecated(since = '8.0')
    @Override
    Closure doWithSpring() { null }

    /**
     * Sub classes should override to register beans with the Spring Framework
     * {@link org.springframework.beans.factory.BeanRegistry} using a
     * {@link org.springframework.beans.factory.BeanRegistrar}. This is the modern, Spring-native
     * replacement for the {@link #doWithSpring()} bean builder DSL.
     *
     * <p>The returned registrar is applied before Spring Boot auto-configuration is processed, so
     * beans registered here take precedence over Boot's {@code @ConditionalOnMissingBean} defaults.</p>
     *
     * @return A {@link org.springframework.beans.factory.BeanRegistrar} that registers beans,
     * or {@code null} if none (the default)
     * @since 8.0
     */
    @Override
    BeanRegistrar beanRegistrar() { null }

    /**
     * Registers Spring beans directly against the supplied {@link BeanBuilder}. This is the statically-compilable
     * alternative to {@link #doWithSpring()}: instead of returning a closure whose delegate the container wires up,
     * an implementation registers beans against the builder passed as an argument. Subclasses should override.
     *
     * A plugin should override either this method or {@link #doWithSpring()}, but not both: the two forms are
     * alternatives, and defining both causes the plugin to fail to load.
     *
     * @param beans The {@link BeanBuilder} to register beans against
     */
    void doWithSpring(BeanBuilder beans) {
        // no-op
    }

    /**
     * Invoked in a phase where plugins can add dynamic methods. Subclasses should override
     */
    @Override
    void doWithDynamicMethods() {
        // no-op
    }

    /**
     * Invokes once the {@link ApplicationContext} has been refreshed and after {#doWithDynamicMethods()} is invoked. Subclasses should override
     */
    @Override
    void doWithApplicationContext() {
        // no-op
    }

    /**
     * Invoked when a object this plugin is watching changes
     *
     * @param event The event
     */
    void onChange(Map<String, Object> event) {
        // no-op
    }

    /**
     * Invoked when the application configuration changes
     *
     * @param event The event
     */
    @Override
    void onConfigChange(Map<String, Object> event) {
        // no-op
    }

    @Override
    void onStartup(Map<String, Object> event) {
        // no-op
    }
    /**
     * Invoked when the {@link ApplicationContext} is closed
     *
     * @param event The event
     */
    @Override
    void onShutdown(Map<String, Object> event) {
        // no-op
    }

    /**
     * Allows a plugin to define beans at runtime. Used primarily for reloading in development mode
     *
     * @param beanDefinitions The bean definitions
     * @return The BeanBuilder instance
     */
    void beans(Closure beanDefinitions) {
        def bb = new BeanBuilder(null, grailsApplication.classLoader)
        bb.beans(beanDefinitions)
        bb.registerBeans((BeanDefinitionRegistry) applicationContext)
        new MapBasedSmartPropertyOverrideConfigurer(grailsApplication: grailsApplication).postProcessBeanFactory(((ConfigurableApplicationContext) applicationContext).beanFactory)
    }
}
