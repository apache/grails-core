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
package grails.plugin.formfields

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.StandardEnvironment

import grails.core.support.proxy.DefaultProxyHandler
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.mapping.model.MappingContext
import org.grails.scaffolding.model.DomainModelServiceImpl
import org.grails.scaffolding.model.property.DomainPropertyFactory
import org.grails.scaffolding.model.property.DomainPropertyFactoryImpl

import spock.lang.Specification

class FieldsGrailsPluginSpec extends Specification {

    def beanFactory = new DefaultListableBeanFactory()

    void setup() {
        def registrar = new FieldsGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }

    void "beanRegistrar registers the fields beans"() {
        expect:
        beanFactory.containsBeanDefinition('beanPropertyAccessorFactory')
        beanFactory.getBeanDefinition('formFieldsTemplateService').beanClassName == FormFieldsTemplateService.name
        beanFactory.getBeanDefinition('fieldsDomainPropertyFactory').beanClassName == DomainPropertyFactoryImpl.name
        beanFactory.containsBeanDefinition('domainModelService')
    }

    void "the bean property accessor factory is wired from the surrounding beans"() {
        given:
        def constraintsEvaluator = Mock(ConstraintsEvaluator)
        def proxyHandler = new DefaultProxyHandler()
        def mappingContext = Mock(MappingContext)
        beanFactory.registerSingleton(FieldsGrailsPlugin.CONSTRAINTS_EVALULATOR_BEAN_NAME, constraintsEvaluator)
        beanFactory.registerSingleton('proxyHandler', proxyHandler)
        beanFactory.registerSingleton('grailsDomainClassMappingContext', mappingContext)

        when:
        def factory = beanFactory.getBean('beanPropertyAccessorFactory', BeanPropertyAccessorFactory)

        then:
        factory.constraintsEvaluator.is(constraintsEvaluator)
        factory.proxyHandler.is(proxyHandler)
        factory.grailsDomainClassMappingContext.is(mappingContext)
        factory.fieldsDomainPropertyFactory.is(beanFactory.getBean('fieldsDomainPropertyFactory', DomainPropertyFactory))
    }

    void "the domain model service is wired with the fields domain property factory"() {
        expect:
        beanFactory.getBean('domainModelService', DomainModelServiceImpl).domainPropertyFactory
                .is(beanFactory.getBean('fieldsDomainPropertyFactory', DomainPropertyFactory))
    }
}
