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

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.converters.JSON
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.core.GrailsApplication
import grails.core.support.proxy.ProxyHandler
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.jackson.JacksonNamedJsonRenderer
import org.grails.web.converters.jackson.Shelf
import org.grails.web.converters.jackson.Volume
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller as JsonErrorsMarshaller

import spock.lang.Specification

class ConvertersGrailsPluginSpec extends Specification {

    def beanFactory = new DefaultListableBeanFactory()

    void setup() {
        beanFactory.registerSingleton('grailsApplication', new DefaultGrailsApplication())
        beanFactory.registerSingleton('proxyHandler', new DefaultProxyHandler())
        def registrar = new ConvertersGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }

    void "beanRegistrar registers the converters beans"() {
        expect:
        with(beanFactory) {
            getBeanDefinition('jsonErrorsMarshaller').beanClassName == JsonErrorsMarshaller.name
            getBeanDefinition('convertersConfigurationInitializer').beanClassName == ConvertersConfigurationInitializer.name
            containsBeanDefinition('namedJsonConfigurationRegistry')
            containsBeanDefinition('namedJsonRenderer')
            containsBeanDefinition('errorsJsonMarshallerRegisterer')
        }
    }

    void "the Grails 8 errors marshaller is registered only while the JSON converter renders as Grails 8 did"() {
        given: 'the converters beans of an application that renders through the JsonMapper'
        def mapperFactory = new DefaultListableBeanFactory()
        mapperFactory.registerSingleton('grailsApplication', new DefaultGrailsApplication())
        mapperFactory.registerSingleton('proxyHandler', new DefaultProxyHandler())
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', ['grails.converters.json.legacy': false]))
        def registrar = new ConvertersGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(mapperFactory, environment, registrar.getClass()).register(registrar)

        expect:
        beanFactory.containsBeanDefinition('errorsJsonMarshallerRegisterer')
        !mapperFactory.containsBeanDefinition('errorsJsonMarshallerRegisterer')
    }

    void "the named JSON configuration registry is created without a Boot JsonMapper"() {
        when: "the bean is instantiated in a context that has no JsonMapper, such as a unit test slice"
        def configurationRegistry = beanFactory.getBean('namedJsonConfigurationRegistry', NamedJsonConfigurationRegistry)

        then: "creating it does not fail the application context"
        configurationRegistry != null

        and: "configurations still register, since that needs no mapper"
        configurationRegistry.register('deep') { it.attribute('depth', 'deep') }
        configurationRegistry.contains('deep')
    }

    void "using a named configuration without a JsonMapper says so rather than substituting one"() {
        given:
        def configurationRegistry = beanFactory.getBean('namedJsonConfigurationRegistry', NamedJsonConfigurationRegistry)
        configurationRegistry.register('deep') { it.attribute('depth', 'deep') }

        when: "a writer is needed but Jackson auto-configuration never ran"
        configurationRegistry.writeValueAsString('deep', [title: 'Grails'])

        then: "the failure names the cause instead of silently using a differently configured mapper"
        IllegalStateException e = thrown()
        e.message.contains('no JsonMapper is available')
    }

    void "a registered JsonMapper is the one configurations derive from"() {
        given: "a mapper registered after the registry bean definition, as Boot's is"
        def mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
        beanFactory.registerSingleton('jacksonJsonMapper', mapper)
        def configurationRegistry = beanFactory.getBean('namedJsonConfigurationRegistry', NamedJsonConfigurationRegistry)

        when:
        configurationRegistry.register('catalog') { it.attribute('depth', 'deep') }

        then:
        configurationRegistry.writeValueAsString('catalog', new PluginBook(bookTitle: 'Grails')) == '{"book_title":"Grails"}'
    }

    void "the conventional Boot mapper resolves multiple non-primary mapper beans"() {
        given:
        beanFactory.registerSingleton('jacksonJsonMapper',
                JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build())
        beanFactory.registerSingleton('pluginMapper',
                JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE).build())
        def registry = beanFactory.getBean('namedJsonConfigurationRegistry', NamedJsonConfigurationRegistry)
        registry.register('catalog') { }

        expect:
        registry.writeValueAsString('catalog', new PluginBook(bookTitle: 'Grails')) == '{"book_title":"Grails"}'
        registry.writeValueAsString(null, new PluginBook(bookTitle: 'Grails')) == '{"book_title":"Grails"}'
    }

    void "the named JSON renderer is created without a Boot JsonMapper"() {
        when:
        def renderer = beanFactory.getBean('namedJsonRenderer', JacksonNamedJsonRenderer)

        then:
        renderer != null
        !renderer.contains('absent')
    }

    void "the errors marshaller registerers use the named errors marshaller beans"() {
        when:
        def jsonRegisterer = beanFactory.getBean('errorsJsonMarshallerRegisterer', ObjectMarshallerRegisterer)

        then:
        jsonRegisterer.marshaller.is(beanFactory.getBean('jsonErrorsMarshaller', JsonErrorsMarshaller))
        jsonRegisterer.converterClass == JSON
    }

    void "the Jackson module for domain classes is not registered by default"() {
        expect:
        !beanFactory.containsBeanDefinition('domainClassJacksonModule')
    }

    void "the Jackson module for domain classes is registered when #settings"() {
        given:
        def factory = new DefaultListableBeanFactory()
        def registrar = new ConvertersGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(factory, environment(settings), registrar.getClass()).register(registrar)

        expect:
        factory.containsBeanDefinition('domainClassJacksonModule') == registered

        where:
        settings                                                                               || registered
        [(ConvertersConfigurationInitializer.SETTING_CONVERTERS_JSON_DOMAIN_JACKSON_ENABLED): 'true'] || true
        [(ConvertersConfigurationInitializer.SETTING_CONVERTERS_JSON_DOMAIN_JACKSON_ENABLED): 'false'] || false
        [(ConvertersConfigurationInitializer.SETTING_CONVERTERS_JSON_DOMAIN_JACKSON_ENABLED): 'true',
         (ConvertersConfigurationInitializer.SETTING_CONVERTERS_JSON_LEGACY): 'true']                 || true
    }

    void "by default, Spring Boot's JsonMapper renders domain classes as Jackson beans, as in Grails 8"() {
        given:
        def context = bootContext([:])
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        def volume = new Volume(title: 'Grails', shelf: shelf)
        volume.id = 1

        when:
        def json = context.getBean(JsonMapper).writeValueAsString(volume)

        then: 'the shelf in full'
        json.contains('"shelf":{')
        json.contains('"name":"top"')

        cleanup:
        context.close()
        ConvertersConfigurationHolder.clear()
    }

    void "with the module enabled, Spring Boot's JsonMapper renders domain classes as the JSON converter does"() {
        given:
        def context = bootContext([(ConvertersConfigurationInitializer.SETTING_CONVERTERS_JSON_DOMAIN_JACKSON_ENABLED): 'true'])
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

    private AnnotationConfigApplicationContext bootContext(Map<String, Object> settings) {
        def context = new AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', settings))
        context.registerBean('grailsApplication', GrailsApplication, { domainApplication() })
        context.registerBean(ProxyHandler, { new DefaultProxyHandler() })
        def registrar = new ConvertersGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(context.defaultListableBeanFactory, context.environment, registrar.getClass()).register(registrar)
        context.register(JacksonAutoConfiguration)
        context.refresh()
        context
    }

    private static StandardEnvironment environment(Map<String, Object> settings) {
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', settings))
        environment
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

class PluginBook {
    String bookTitle
}
