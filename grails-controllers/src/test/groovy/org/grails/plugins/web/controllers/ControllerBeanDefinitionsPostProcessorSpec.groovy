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

import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import org.grails.core.artefact.ControllerArtefactHandler

import spock.lang.Specification

class ControllerBeanDefinitionsPostProcessorSpec extends Specification {

    static class TestController {
    }

    def controllerClass = Mock(GrailsControllerClass) {
        getFullName() >> TestController.name
        getClazz() >> TestController
        getAvailable() >> true
        hasProperty('lazyInit') >> false
    }
    def grailsApplication = Mock(GrailsApplication) {
        getArtefacts(ControllerArtefactHandler.TYPE) >> { [controllerClass] as GrailsClass[] }
    }

    void "registers a lazy, name-autowired bean definition for each controller artefact"() {
        given:
        controllerClass.getScope() >> 'singleton'
        def registry = new DefaultListableBeanFactory()

        when:
        new ControllerBeanDefinitionsPostProcessor(grailsApplication, false).postProcessBeanDefinitionRegistry(registry)
        def definition = (AbstractBeanDefinition) registry.getBeanDefinition(TestController.name)

        then:
        definition.beanClassName == TestController.name
        definition.lazyInit
        definition.scope == 'singleton'
        definition.autowireMode == AbstractBeanDefinition.AUTOWIRE_BY_NAME
        definition.dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_NONE
        !definition.propertyValues.contains('useJessionId')
    }

    void "prototype controllers skip the dependency check and jsessionid can be enabled"() {
        given:
        controllerClass.getScope() >> 'prototype'
        def registry = new DefaultListableBeanFactory()

        when:
        new ControllerBeanDefinitionsPostProcessor(grailsApplication, true).postProcessBeanDefinitionRegistry(registry)
        def definition = (AbstractBeanDefinition) registry.getBeanDefinition(TestController.name)

        then:
        definition.scope == BeanDefinition.SCOPE_PROTOTYPE
        definition.dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_NONE
        definition.propertyValues.getPropertyValue('useJessionId').value == true
    }

    void "an existing controller bean definition wins"() {
        given:
        controllerClass.getScope() >> 'singleton'
        def registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition(TestController.name, new GenericBeanDefinition(beanClass: String))

        when:
        new ControllerBeanDefinitionsPostProcessor(grailsApplication, false).postProcessBeanDefinitionRegistry(registry)

        then:
        registry.getBeanDefinition(TestController.name).beanClassName == String.name
    }

    void "runs with highest precedence so definitions precede auto-configuration processing"() {
        expect:
        new ControllerBeanDefinitionsPostProcessor(grailsApplication, false).order == Ordered.HIGHEST_PRECEDENCE
    }
}
