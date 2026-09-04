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
package grails.plugin.scaffolding

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.EnvironmentAware
import org.springframework.core.Ordered
import org.springframework.core.env.Environment

import grails.util.Metadata
import org.grails.plugins.web.GroovyPagesPostProcessor

/**
 * Registers the {@code jspViewResolver} bean definition as a
 * {@link ScaffoldingViewResolver} unless a definition already exists, replacing
 * the registration the plugin previously performed through the
 * {@code doWithSpring()} bean DSL. Registering a definition (with the same
 * {@code abstractViewResolver} parent the GSP plugin's default uses) rather
 * than building the resolver directly keeps the view-resolver configuration in
 * one place and preserves the established post-processor pipeline.
 */
@CompileStatic
class ScaffoldingViewResolverDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, Ordered {

    /**
     * Runs before the SiteMesh 2 module's {@code GrailsLayoutViewResolverPostProcessor}
     * ({@code GroovyPagesPostProcessor.ORDER - 1}), which embeds the definition
     * registered here as its inner view resolver, and before
     * {@link GroovyPagesPostProcessor} itself, which contributes the plain GSP
     * resolver only when no definition exists by then.
     */
    public static final int ORDER = GroovyPagesPostProcessor.ORDER - 2

    private static final String JSP_VIEW_RESOLVER_BEAN_NAME = 'jspViewResolver'

    private Environment environment

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry.containsBeanDefinition(JSP_VIEW_RESOLVER_BEAN_NAME)) {
            // Any existing definition wins, whether the application's or another
            // plugin's. Note this is stricter than the old doWithSpring()
            // registration, which overwrote definitions from earlier-loading
            // plugins: scaffolding now stands down for those too, matching how
            // GroovyPagesPostProcessor contributes its default.
            return
        }
        GenericBeanDefinition definition = new GenericBeanDefinition()
        definition.beanClass = ScaffoldingViewResolver
        definition.parentName = 'abstractViewResolver'
        definition.lazyInit = true
        MutablePropertyValues properties = definition.propertyValues
        properties.addPropertyValue('enableReload', isReloadEnabled())
        properties.addPropertyValue('enableNamespaceViewDefaults', this.environment != null &&
                this.environment.getProperty('grails.scaffolding.enableNamespaceViewDefaults', Boolean, false))
        registry.registerBeanDefinition(JSP_VIEW_RESOLVER_BEAN_NAME, definition)
    }

    private static boolean isReloadEnabled() {
        grails.util.Environment env = grails.util.Environment.current
        env.isReloadEnabled() ||
                (Metadata.current.isDevelopmentEnvironmentAvailable() && env == grails.util.Environment.DEVELOPMENT)
    }

    @Override
    void setEnvironment(Environment environment) {
        this.environment = environment
    }

    @Override
    int getOrder() {
        ORDER
    }
}
