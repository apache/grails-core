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
package org.grails.plugins.services

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsServiceClass
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import org.grails.core.artefact.ServiceArtefactHandler

/**
 * Registers a bean definition for every service artefact, replacing the registration the services
 * plugin previously performed through the {@code doWithSpring()} bean DSL. Service beans autowire
 * by name and use the scope declared on the service class, which cannot be expressed through the
 * {@link org.springframework.beans.factory.BeanRegistry} API, so the definitions are contributed
 * by this post-processor instead.
 *
 * <p>Runs as a {@link PriorityOrdered} post-processor with highest precedence so the service
 * definitions are registered before Spring Boot's configuration-class post-processor evaluates
 * auto-configuration conditions — the same visibility the {@code doWithSpring()} registration had.
 * An existing definition for a service name wins, preserving the ability of the application
 * (or another plugin) to override a service bean.</p>
 *
 * @since 8.0
 */
@CompileStatic
class ServiceBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final GrailsApplication grailsApplication
    private final GrailsPluginManager pluginManager

    ServiceBeanDefinitionsPostProcessor(GrailsApplication grailsApplication, GrailsPluginManager pluginManager) {
        this.grailsApplication = grailsApplication
        this.pluginManager = pluginManager
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (GrailsClass service in grailsApplication.getArtefacts(ServiceArtefactHandler.TYPE)) {
            GrailsServiceClass serviceClass = (GrailsServiceClass) service
            GrailsPlugin providingPlugin = pluginManager?.getPluginForClass(serviceClass.clazz)

            String beanName
            if (providingPlugin && !serviceClass.shortName.toLowerCase().startsWith(providingPlugin.name.toLowerCase())) {
                beanName = "${providingPlugin.name}${serviceClass.shortName}"
            } else {
                beanName = serviceClass.propertyName
            }
            if (registry.containsBeanDefinition(beanName)) {
                continue
            }
            Object scope = serviceClass.getPropertyValue('scope')
            Object lazyInit = serviceClass.hasProperty('lazyInit') ? serviceClass.getPropertyValue('lazyInit') : true

            GenericBeanDefinition definition = new GenericBeanDefinition()
            definition.beanClass = serviceClass.clazz
            definition.autowireMode = AbstractBeanDefinition.AUTOWIRE_BY_NAME
            if (lazyInit instanceof Boolean) {
                definition.lazyInit = lazyInit
            }
            if (scope) {
                definition.scope = scope.toString()
            }
            registry.registerBeanDefinition(beanName, definition)
        }
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}
