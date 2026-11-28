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
import grails.core.GrailsApplication
import grails.core.GrailsApplicationLifeCycle
import grails.core.support.GrailsApplicationAware
import grails.spring.BeanBuilder
import grails.util.Environment
import org.grails.spring.context.support.MapBasedSmartPropertyOverrideConfigurer

/**
 * The super class of every Grails plugin. A plugin is a concrete subclass annotated with
 * {@link grails.plugins.metadata.GrailsPluginDescriptor}, which declares its name, ordering and other
 * metadata; the subclass overrides the hooks it needs ({@link #beanRegistrar()},
 * {@link #doWithApplicationContext()}, {@link #onChange(Map)} and the rest), and declares its beans with
 * {@link #beanRegistrar()} or a {@code beans} block.
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
     * Sub classes should override to register beans with the Spring Framework
     * {@link org.springframework.beans.factory.BeanRegistry} using a
     * {@link org.springframework.beans.factory.BeanRegistrar}. With a {@code beans} block, which the
     * compiler turns into an auto-configuration, it is how a plugin contributes beans.
     *
     * <p>The returned registrar is applied before Spring Boot auto-configuration is processed, plugin by
     * plugin in load order, so beans registered here take precedence over Boot's
     * {@code @ConditionalOnMissingBean} defaults, and a plugin loaded later replaces a bean of the same
     * name that an earlier plugin registered. A definition the {@code BeanRegistry} API cannot express,
     * such as an abstract parent, an alias or autowiring by name, is registered by a
     * {@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor} that the
     * registrar registers; implementing {@link org.springframework.core.PriorityOrdered} with
     * {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} runs it at the same point.</p>
     *
     * @return A {@link org.springframework.beans.factory.BeanRegistrar} that registers beans,
     * or {@code null} if none (the default)
     * @since 8.0
     */
    @Override
    BeanRegistrar beanRegistrar() { null }

    /**
     * Invoked once the {@link ApplicationContext} has been refreshed, for each plugin in load order. This is
     * where a plugin adds dynamic methods or properties and works with the beans of the refreshed context.
     * Subclasses should override.
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
