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
package org.grails.plugins.openapi

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.openapi.UrlMappingsOpenApiCustomizer
import grails.web.mapping.UrlMappingsHolder
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder

import spock.lang.Specification

class OpenApiGrailsPluginSpec extends Specification {

    void 'registers the OpenAPI customizer wired to the application URL mappings'() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton('grailsUrlMappingsHolder', urlMappingsHolder())
        def registrar = new OpenApiGrailsPlugin().beanRegistrar()

        when:
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.class).register(registrar)

        then:
        beanFactory.getBean('grailsUrlMappingsOpenApiCustomizer', UrlMappingsOpenApiCustomizer)
    }

    void 'declares a dependency on the URL mappings plugin'() {
        expect:
        new OpenApiGrailsPlugin().dependsOn.containsKey('urlMappings')
    }

    private static UrlMappingsHolder urlMappingsHolder() {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        new DefaultUrlMappingsHolder(evaluator.evaluateMappings {
            '/books'(controller: 'book', action: 'index', method: 'GET')
        })
    }
}
