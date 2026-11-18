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
package org.grails.plugins.web.controllers

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.BeansException
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered

import grails.core.GrailsApplication
import grails.core.GrailsControllerClass
import org.grails.core.artefact.ControllerArtefactHandler

/**
 * Registers a bean definition for every controller artefact, replacing the registration the
 * controllers plugin previously performed through the {@code doWithSpring()} bean DSL. Controller
 * beans autowire by name, use the scope declared on the controller class and cannot be expressed
 * through the {@link org.springframework.beans.factory.BeanRegistry} API, so the definitions are
 * contributed by this post-processor instead.
 *
 * <p>Runs as a {@link PriorityOrdered} post-processor with highest precedence so the controller
 * definitions are registered before Spring Boot's configuration-class post-processor evaluates
 * auto-configuration conditions — the same visibility the {@code doWithSpring()} registration had.
 * An existing definition for a controller name wins, preserving the ability of the application
 * (or another plugin) to override a controller bean.</p>
 *
 * @since 8.0
 */
@Slf4j
@CompileStatic
class ControllerBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final GrailsApplication grailsApplication
    private final boolean useJsessionId

    ControllerBeanDefinitionsPostProcessor(GrailsApplication grailsApplication, boolean useJsessionId) {
        this.grailsApplication = grailsApplication
        this.useJsessionId = useJsessionId
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (def controllerClass : resolveControllerClasses(grailsApplication)) {
            log.debug('Configuring controller {}', controllerClass.fullName)
            if (!controllerClass.available || registry.containsBeanDefinition(controllerClass.fullName)) {
                continue
            }
            def beanDefinition = new GenericBeanDefinition(
                    beanClass: controllerClass.clazz,
                    lazyInit: resolveControllerLazyInit(controllerClass),
                    scope: controllerClass.scope,
                    autowireMode: AbstractBeanDefinition.AUTOWIRE_BY_NAME
            )
            if (controllerClass.scope == 'prototype') {
                beanDefinition.dependencyCheck = AbstractBeanDefinition.DEPENDENCY_CHECK_NONE
            }
            if (useJsessionId) {
                beanDefinition.propertyValues.addPropertyValue('useJessionId', useJsessionId)
            }
            registry.registerBeanDefinition(controllerClass.fullName, beanDefinition)
        }
    }

    private static List<GrailsControllerClass> resolveControllerClasses(GrailsApplication grailsApplication) {
        grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE).collect {
            (GrailsControllerClass) it
        }
    }

    private static boolean resolveControllerLazyInit(GrailsControllerClass controllerClass) {
        controllerClass.hasProperty('lazyInit') ? controllerClass.getPropertyValue('lazyInit') as boolean : true
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}
