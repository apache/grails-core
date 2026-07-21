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

import org.springframework.aop.framework.ProxyFactoryBean
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.StandardEnvironment

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.web.mapping.UrlMappings
import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.grails.spring.beans.factory.HotSwappableTargetSourceFactoryBean
import org.grails.web.mapping.UrlMappingsHolderFactoryBean

import spock.lang.Specification

class UrlMappingsGrailsPluginSpec extends Specification {

    void "beanRegistrar contributes the default url mappings and registers the definitions post-processor"() {
        given: 'no url mappings artefacts exist'
        def beanFactory = new DefaultListableBeanFactory()
        def application = Mock(GrailsApplication) {
            getArtefacts(UrlMappingsArtefactHandler.TYPE) >> { new GrailsClass[0] }
        }

        when:
        applyRegistrar(beanFactory, application)

        then: 'the default url mappings are contributed'
        1 * application.addArtefact(UrlMappingsArtefactHandler.TYPE, UrlMappingsGrailsPlugin.DefaultUrlMappings)

        and:
        beanFactory.containsBeanDefinition('urlMappingsBeanDefinitionsPostProcessor')
    }

    void "the default url mappings are not contributed when the application defines mappings"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def existingMappings = Mock(GrailsClass)
        def application = Mock(GrailsApplication) {
            getArtefacts(UrlMappingsArtefactHandler.TYPE) >> { [existingMappings] as GrailsClass[] }
        }

        when:
        applyRegistrar(beanFactory, application)

        then:
        0 * application.addArtefact(*_)
    }

    void "the reload-mode holder is a ProxyFactoryBean defined so its produced UrlMappings type stays predictable"() {
        given:
        def registry = new DefaultListableBeanFactory()

        when:
        new UrlMappingsBeanDefinitionsPostProcessor(true, true).postProcessBeanDefinitionRegistry(registry)
        def holder = registry.getBeanDefinition('grailsUrlMappingsHolder')

        then: 'proxyInterfaces are a definition property (not hidden in an instance supplier), so Spring can predict the UrlMappings type for by-type autowiring'
        holder.beanClassName == ProxyFactoryBean.name
        holder.lazyInit
        holder.propertyValues.getPropertyValue('proxyInterfaces').value == ([UrlMappings] as Class[])
        holder.propertyValues.getPropertyValue('targetSource').value == new RuntimeBeanReference('urlMappingsTargetSource')

        and: 'the target source hot-swaps an inner UrlMappingsHolderFactoryBean, mirroring the original DSL'
        def targetSource = registry.getBeanDefinition('urlMappingsTargetSource')
        targetSource.beanClassName == HotSwappableTargetSourceFactoryBean.name
        ((AbstractBeanDefinition) targetSource.propertyValues.getPropertyValue('target').value).beanClassName ==
                UrlMappingsHolderFactoryBean.name
    }

    void "the non-reload holder is a lazy UrlMappingsHolderFactoryBean"() {
        given:
        def registry = new DefaultListableBeanFactory()

        when:
        new UrlMappingsBeanDefinitionsPostProcessor(false, true).postProcessBeanDefinitionRegistry(registry)
        def holder = registry.getBeanDefinition('grailsUrlMappingsHolder')

        then:
        holder.beanClassName == UrlMappingsHolderFactoryBean.name
        holder.lazyInit
        !registry.containsBeanDefinition('urlMappingsTargetSource')
    }

    private static void applyRegistrar(DefaultListableBeanFactory beanFactory, GrailsApplication application) {
        def plugin = new UrlMappingsGrailsPlugin(grailsApplication: application)
        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }
}
