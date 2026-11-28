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
package grails.plugin.springsecurity

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.core.GrailsApplication
import grails.plugins.Plugin
import org.grails.core.io.CachingPathMatchingResourcePatternResolver
import org.grails.spring.BeanBuilderRegistryPostProcessor

/**
 * The registrar the Spring Security plugins register their beans with. Each plugin declares its beans
 * in the bean builder DSL, read from the security configuration; the registrar registers a
 * {@link BeanBuilderRegistryPostProcessor} that runs the plugin's closure when Spring runs it, before
 * the configuration classes are read. The post-processors run in plugin load order, so a plugin loaded
 * later, such as one that replaces a bean of the core plugin, registers its definition over the earlier
 * one, and the configuration each plugin loads and the providers and filters it registers are seen by
 * the plugins after it.
 *
 * <p>The closure finds the variables the plugin bean builder bound: {@code application} and
 * {@code grailsApplication}, {@code manager}, {@code plugin}, {@code parentCtx} and {@code resolver}.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class SpringSecurityBeanDefinitions {

    private SpringSecurityBeanDefinitions() {
    }

    /**
     * @param plugin the plugin whose beans are registered
     * @param postProcessorName the name of the post-processor bean
     * @param beans supplies the plugin's bean builder closure, or {@code null} for no beans
     * @return the registrar to return from the plugin's {@code beanRegistrar()}
     */
    static BeanRegistrar registrar(Plugin plugin, String postProcessorName, Supplier<Closure<?>> beans) {
        { BeanRegistry registry, Environment environment ->
            registry.registerBean(postProcessorName, BeanBuilderRegistryPostProcessor) {
                it.infrastructure().supplier {
                    GrailsApplication application = plugin.grailsApplication
                    Map<String, Object> variables = [
                            application: (Object) application,
                            grailsApplication: application,
                            manager: plugin.pluginManager,
                            plugin: plugin.plugin,
                            parentCtx: application.parentContext,
                            resolver: CachingPathMatchingResourcePatternResolver.INSTANCE
                    ]
                    new BeanBuilderRegistryPostProcessor(application.parentContext, application.classLoader, variables, beans)
                }
            }
        } as BeanRegistrar
    }
}
