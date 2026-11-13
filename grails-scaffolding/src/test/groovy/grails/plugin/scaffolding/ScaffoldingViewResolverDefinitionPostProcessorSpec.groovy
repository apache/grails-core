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

import org.apache.grails.web.layout.GrailsLayoutViewResolverPostProcessor

import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.web.servlet.view.InternalResourceViewResolver

import org.grails.plugins.web.GroovyPagesPostProcessor

import spock.lang.Specification

class ScaffoldingViewResolverDefinitionPostProcessorSpec extends Specification {

    ScaffoldingViewResolverDefinitionPostProcessor postProcessor = new ScaffoldingViewResolverDefinitionPostProcessor()

    void "registers the scaffolding view resolver definition with the GSP parent template"() {
        given:
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory()
        postProcessor.environment = new StandardEnvironment()

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)
        BeanDefinition definition = registry.getBeanDefinition('jspViewResolver')

        then:
        definition.beanClassName == ScaffoldingViewResolver.name
        definition.parentName == 'abstractViewResolver'
        definition.lazyInit
        definition.propertyValues.contains('enableReload')
        definition.propertyValues.getPropertyValue('enableNamespaceViewDefaults').value == false
    }

    void "the enableNamespaceViewDefaults property is read from the environment"() {
        given:
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory()
        StandardEnvironment environment = new StandardEnvironment()
        environment.propertySources.addFirst(
                new MapPropertySource('test', ['grails.scaffolding.enableNamespaceViewDefaults': 'true']))
        postProcessor.environment = environment

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        then:
        registry.getBeanDefinition('jspViewResolver')
                .propertyValues.getPropertyValue('enableNamespaceViewDefaults').value == true
    }

    void "an existing jspViewResolver definition wins"() {
        given:
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition('jspViewResolver',
                new GenericBeanDefinition(beanClass: InternalResourceViewResolver))
        postProcessor.environment = new StandardEnvironment()

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        then:
        registry.getBeanDefinition('jspViewResolver').beanClassName == InternalResourceViewResolver.name
    }

    void "runs before the SiteMesh 2 layout post-processor and the GSP default post-processor"() {
        expect: "grails-layout embeds this definition as its inner resolver, and the GSP default only registers when no definition exists"
        postProcessor.order < new GrailsLayoutViewResolverPostProcessor().order
        postProcessor.order < GroovyPagesPostProcessor.ORDER
    }
}
