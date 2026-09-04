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
package org.grails.plugins.web.interceptors

import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered
import org.springframework.core.env.StandardEnvironment
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.handler.MappedInterceptor

import grails.core.GrailsApplication
import grails.core.GrailsClass

import spock.lang.Specification

class InterceptorsGrailsPluginSpec extends Specification {

    static class TestInterceptor {
    }

    def interceptorClass = Mock(GrailsClass) {
        getPropertyName() >> 'testInterceptor'
        getClazz() >> TestInterceptor
    }

    void "beanRegistrar registers only the definitions post-processor"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def application = Mock(GrailsApplication)

        when:
        applyRegistrar(beanFactory, application)

        then: 'the registrar itself does not touch the artefacts or register the interceptor beans directly'
        0 * application.getArtefacts(_)
        beanFactory.containsBeanDefinition('interceptorBeanDefinitionsPostProcessor')
        !beanFactory.containsBeanDefinition('grailsInterceptorMappedInterceptor')
    }

    void "no interceptor beans are registered when there are no interceptor artefacts"() {
        given:
        def registry = new DefaultListableBeanFactory()
        def application = Mock(GrailsApplication) {
            getArtefacts(InterceptorArtefactHandler.TYPE) >> { new GrailsClass[0] }
        }

        when:
        new InterceptorBeanDefinitionsPostProcessor(application, false).postProcessBeanDefinitionRegistry(registry)

        then:
        registry.beanDefinitionCount == 0
    }

    void "the definitions post-processor registers the mapped interceptor wrapping an inner adapter bean"() {
        given:
        def registry = new DefaultListableBeanFactory()
        def application = Mock(GrailsApplication) {
            getArtefacts(InterceptorArtefactHandler.TYPE) >> { [interceptorClass] as GrailsClass[] }
        }

        when:
        new InterceptorBeanDefinitionsPostProcessor(application, false).postProcessBeanDefinitionRegistry(registry)
        def mappedInterceptor = (AbstractBeanDefinition) registry.getBeanDefinition('grailsInterceptorMappedInterceptor')

        then:
        mappedInterceptor.beanClassName == MappedInterceptor.name

        and: 'the adapter is an inner bean, not a top-level HandlerInterceptor bean that would run twice'
        mappedInterceptor.constructorArgumentValues.getIndexedArgumentValue(1, null).value instanceof AbstractBeanDefinition
        ((AbstractBeanDefinition) mappedInterceptor.constructorArgumentValues.getIndexedArgumentValue(1, null).value)
                .beanClassName == GrailsInterceptorHandlerInterceptorAdapter.name
        registry.getBeanNamesForType(GrailsInterceptorHandlerInterceptorAdapter).length == 0

        and: 'WebUtils.lookupHandlerInterceptors (getBeansOfType) sees only the mapped interceptor, so no interceptor runs twice'
        registry.getBeanNamesForType(HandlerInterceptor) as List == ['grailsInterceptorMappedInterceptor']
    }

    void "the definitions post-processor registers name-autowired interceptor beans"() {
        given:
        def registry = new DefaultListableBeanFactory()
        def application = Mock(GrailsApplication) {
            getArtefacts(InterceptorArtefactHandler.TYPE) >> { [interceptorClass] as GrailsClass[] }
        }
        def postProcessor = new InterceptorBeanDefinitionsPostProcessor(application, true)

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)
        def definition = (AbstractBeanDefinition) registry.getBeanDefinition('testInterceptor')

        then:
        definition.beanClassName == TestInterceptor.name
        definition.autowireMode == AbstractBeanDefinition.AUTOWIRE_BY_NAME
        definition.propertyValues.getPropertyValue('useJessionId').value == true
        postProcessor.order == Ordered.HIGHEST_PRECEDENCE
    }

    void "an existing interceptor bean definition wins"() {
        given:
        def registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition('testInterceptor', new GenericBeanDefinition(beanClass: String))
        def application = Mock(GrailsApplication) {
            getArtefacts(InterceptorArtefactHandler.TYPE) >> { [interceptorClass] as GrailsClass[] }
        }

        when:
        new InterceptorBeanDefinitionsPostProcessor(application, false).postProcessBeanDefinitionRegistry(registry)

        then:
        registry.getBeanDefinition('testInterceptor').beanClassName == String.name
    }

    private static void applyRegistrar(DefaultListableBeanFactory beanFactory, GrailsApplication application) {
        def plugin = new InterceptorsGrailsPlugin(grailsApplication: application)
        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }
}
