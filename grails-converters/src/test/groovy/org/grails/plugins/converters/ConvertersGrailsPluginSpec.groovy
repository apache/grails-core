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

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.StandardEnvironment

import grails.converters.JSON
import grails.converters.json.NamedJsonConfigurationRegistry
import tools.jackson.databind.json.JsonMapper
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.jackson.JacksonNamedJsonRenderer
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller as JsonErrorsMarshaller

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
            getBeanDefinition('convertersConfigurationInitializer').beanClassName == ConvertersConfigurationInitializer.name
            containsBeanDefinition('namedJsonConfigurationRegistry')
            containsBeanDefinition('namedJsonRenderer')
            containsBeanDefinition('errorsJsonMarshallerRegisterer')
        }
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
        def mapper = JsonMapper.builder().build()
        beanFactory.registerSingleton('jacksonJsonMapper', mapper)
        def configurationRegistry = beanFactory.getBean('namedJsonConfigurationRegistry', NamedJsonConfigurationRegistry)

        when:
        configurationRegistry.register('deep') { it.attribute('depth', 'deep') }

        then:
        configurationRegistry.writeValueAsString('deep', [title: 'Grails']) == '{"title":"Grails"}'
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
}
