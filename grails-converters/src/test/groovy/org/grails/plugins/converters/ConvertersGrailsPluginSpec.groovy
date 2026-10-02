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
package org.grails.plugins.converters

import tools.jackson.databind.json.JsonMapper

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.converters.JSON
import grails.converters.XML
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.jackson.DomainClassJacksonModule
import org.grails.web.converters.jackson.Shelf
import org.grails.web.converters.jackson.Volume
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller as JsonErrorsMarshaller
import org.grails.web.converters.marshaller.xml.ValidationErrorsMarshaller as XmlErrorsMarshaller

import spock.lang.Specification

class ConvertersGrailsPluginSpec extends Specification {

    def beanFactory = new DefaultListableBeanFactory()

    void setup() {
        def registrar = new ConvertersGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }

    void "beanRegistrar registers the converters beans"() {
        expect:
        with(beanFactory) {
            getBeanDefinition('jsonErrorsMarshaller').beanClassName == JsonErrorsMarshaller.name
            getBeanDefinition('xmlErrorsMarshaller').beanClassName == XmlErrorsMarshaller.name
            getBeanDefinition('convertersConfigurationInitializer').beanClassName == ConvertersConfigurationInitializer.name
            containsBeanDefinition('errorsXmlMarshallerRegisterer')
            containsBeanDefinition('errorsJsonMarshallerRegisterer')
        }
    }

    void "the errors marshaller registerers use the named errors marshaller beans"() {
        when:
        def xmlRegisterer = beanFactory.getBean('errorsXmlMarshallerRegisterer', ObjectMarshallerRegisterer)
        def jsonRegisterer = beanFactory.getBean('errorsJsonMarshallerRegisterer', ObjectMarshallerRegisterer)

        then:
        xmlRegisterer.marshaller.is(beanFactory.getBean('xmlErrorsMarshaller', XmlErrorsMarshaller))
        xmlRegisterer.converterClass == XML
        jsonRegisterer.marshaller.is(beanFactory.getBean('jsonErrorsMarshaller', JsonErrorsMarshaller))
        jsonRegisterer.converterClass == JSON
    }

    void "beanRegistrar registers a Jackson module for domain classes"() {
        expect:
        beanFactory.getBean('domainClassJacksonModule') instanceof DomainClassJacksonModule
    }

    void "the domain class Jackson module is not registered when grails.converters.json.domain.jackson.enabled is false"() {
        given:
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test',
                [(ConvertersConfigurationInitializer.SETTING_CONVERTERS_JSON_DOMAIN_JACKSON_ENABLED): 'false']))
        def factory = new DefaultListableBeanFactory()
        def registrar = new ConvertersGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(factory, environment, registrar.getClass()).register(registrar)

        expect:
        !factory.containsBeanDefinition('domainClassJacksonModule')
    }

    void "Spring Boot's JsonMapper renders domain classes as the JSON converter does"() {
        given:
        def context = new AnnotationConfigApplicationContext()
        context.registerBean('grailsApplication', GrailsApplication, { domainApplication() })
        context.registerBean(ProxyHandler, { new DefaultProxyHandler() })
        def registrar = new ConvertersGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(context.defaultListableBeanFactory, context.environment, registrar.getClass()).register(registrar)
        context.register(JacksonAutoConfiguration)
        context.refresh()
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        def volume = new Volume(title: 'Grails', shelf: shelf)
        volume.id = 1

        expect: 'the shelf as a reference of its id'
        context.getBean(JsonMapper).writeValueAsString(volume) ==
                '{"id":1,"title":"Grails","shelf":{"id":3},"writers":null,"writersByName":null}'

        cleanup:
        context.close()
        ConvertersConfigurationHolder.clear()
    }

    private GrailsApplication domainApplication() {
        def grailsApplication = new DefaultGrailsApplication(Volume, Shelf)
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('json')
        mappingContext.addPersistentEntities(Volume, Shelf)
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> mappingContext
        })
        grailsApplication.setMappingContext(mappingContext)
        grailsApplication
    }
}
