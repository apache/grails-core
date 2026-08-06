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
package org.grails.plugins.web.mapping

import groovy.transform.CompileStatic

import org.springframework.aop.framework.ProxyFactoryBean
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered

import grails.web.mapping.UrlMappings
import org.grails.spring.beans.factory.HotSwappableTargetSourceFactoryBean
import org.grails.web.mapping.UrlMappingsHolderFactoryBean
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping

/**
 * Registers the URL-mapping bean definitions the url-mappings plugin previously contributed through
 * the {@code doWithSpring()} bean DSL. The {@code grailsUrlMappingsHolder} bean is a
 * {@link ProxyFactoryBean} (in reload mode) whose produced type — {@link UrlMappings}, which
 * extends {@link grails.web.mapping.UrlMappingsHolder} — is only known from its
 * {@code proxyInterfaces} property, and {@link UrlMappingsHandlerMapping} takes a constructor
 * reference to it. Neither the property-driven factory-bean type nor the constructor reference can
 * be expressed through the {@link org.springframework.beans.factory.BeanRegistry} API without hiding
 * the produced type inside an instance supplier — which breaks by-type autowiring of
 * {@code UrlMappingsHolder} (e.g. {@code DefaultLinkGenerator}). The definitions are therefore
 * contributed here, mirroring the original DSL so Spring's factory-bean type prediction behaves
 * identically.
 *
 * <p>Runs as a {@link PriorityOrdered} post-processor with highest precedence so the definitions
 * are registered before Spring Boot's configuration-class post-processor evaluates auto-configuration
 * conditions — the same visibility the {@code doWithSpring()} registration had.</p>
 *
 * @since 8.0
 */
@CompileStatic
class UrlMappingsBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final boolean reloadEnabled
    private final boolean corsFilterEnabled

    UrlMappingsBeanDefinitionsPostProcessor(boolean reloadEnabled, boolean corsFilterEnabled) {
        this.reloadEnabled = reloadEnabled
        this.corsFilterEnabled = corsFilterEnabled
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition('urlMappingsHandlerMapping')) {
            GenericBeanDefinition handlerMapping = new GenericBeanDefinition()
            handlerMapping.beanClass = UrlMappingsHandlerMapping
            handlerMapping.constructorArgumentValues.addIndexedArgumentValue(0, new RuntimeBeanReference('grailsUrlMappingsHolder'))
            if (!corsFilterEnabled) {
                handlerMapping.propertyValues.addPropertyValue('grailsCorsConfiguration', new RuntimeBeanReference('grailsCorsConfiguration'))
            }
            registry.registerBeanDefinition('urlMappingsHandlerMapping', handlerMapping)
        }

        if (registry.containsBeanDefinition('grailsUrlMappingsHolder')) {
            return
        }
        if (reloadEnabled) {
            GenericBeanDefinition innerHolderFactory = new GenericBeanDefinition()
            innerHolderFactory.beanClass = UrlMappingsHolderFactoryBean
            innerHolderFactory.lazyInit = true

            GenericBeanDefinition targetSource = new GenericBeanDefinition()
            targetSource.beanClass = HotSwappableTargetSourceFactoryBean
            targetSource.lazyInit = true
            targetSource.propertyValues.addPropertyValue('target', innerHolderFactory)
            registry.registerBeanDefinition('urlMappingsTargetSource', targetSource)

            GenericBeanDefinition proxy = new GenericBeanDefinition()
            proxy.beanClass = ProxyFactoryBean
            proxy.lazyInit = true
            proxy.propertyValues.addPropertyValue('targetSource', new RuntimeBeanReference('urlMappingsTargetSource'))
            proxy.propertyValues.addPropertyValue('proxyInterfaces', [UrlMappings] as Class[])
            registry.registerBeanDefinition('grailsUrlMappingsHolder', proxy)
        } else {
            GenericBeanDefinition holder = new GenericBeanDefinition()
            holder.beanClass = UrlMappingsHolderFactoryBean
            holder.lazyInit = true
            registry.registerBeanDefinition('grailsUrlMappingsHolder', holder)
        }
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}
