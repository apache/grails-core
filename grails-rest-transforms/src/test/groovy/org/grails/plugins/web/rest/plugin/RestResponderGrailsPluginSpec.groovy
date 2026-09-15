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
package org.grails.plugins.web.rest.plugin

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.BeanPropertyBindingResult

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.rest.render.errors.ValidationProblemDetailFactory
import org.grails.plugins.web.rest.render.DefaultRendererRegistry

import spock.lang.Specification

class RestResponderGrailsPluginSpec extends Specification {

    void "beanRegistrar registers a lazy renderer registry"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()

        when:
        applyRegistrar(beanFactory, new StandardEnvironment())

        then:
        beanFactory.getBeanDefinition('rendererRegistry').lazyInit
        beanFactory.getBean('rendererRegistry', DefaultRendererRegistry).modelSuffix == ''
        beanFactory.getBean('validationProblemDetailFactory', ValidationProblemDetailFactory)
    }

    void "the renderer registry model suffix is read from the environment"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(
                new MapPropertySource('test', [(Settings.SCAFFOLDING_DOMAIN_SUFFIX): 'Bean']))

        when:
        applyRegistrar(beanFactory, environment)

        then:
        beanFactory.getBean('rendererRegistry', DefaultRendererRegistry).modelSuffix == 'Bean'
    }

    void "validation uses the application message source when a plugin provides another"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('pluginMessageSource', new StaticMessageSource())
        applyRegistrar(beanFactory, new StandardEnvironment())
        beanFactory.getBean('messageSource', StaticMessageSource).addMessage('invalid', Locale.default, 'Application message')
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.reject('invalid', 'Fallback')

        expect:
        beanFactory.getBean('validationProblemDetailFactory', ValidationProblemDetailFactory)
                .create(errors).properties.errors.first().message == 'Application message'
    }

    private static void applyRegistrar(DefaultListableBeanFactory beanFactory, StandardEnvironment environment) {
        beanFactory.registerSingleton('messageSource', new StaticMessageSource())
        def plugin = new RestResponderGrailsPlugin(grailsApplication: new DefaultGrailsApplication())
        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(beanFactory, environment, registrar.getClass()).register(registrar)
    }
}
