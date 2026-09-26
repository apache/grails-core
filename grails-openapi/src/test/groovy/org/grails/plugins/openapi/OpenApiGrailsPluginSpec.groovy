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

import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.oas.models.OpenAPI
import org.springdoc.core.customizers.OpenApiBuilderCustomizer
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.openapi.GrailsOpenApiGenerator
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.util.Holders
import grails.web.mapping.UrlMappingsHolder
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.grails.config.PropertySourcesConfig
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.mapping.UrlMappingsHolderFactoryBean

import spock.lang.Specification

class OpenApiGrailsPluginSpec extends Specification {

    void 'registers the generator wired to the application URL mappings'() {
        given:
        def beanFactory = register()

        when:
        def openApi = beanFactory.getBean(OpenApiGrailsPlugin.GENERATOR_BEAN_NAME, GrailsOpenApiGenerator).generate()

        then:
        openApi.paths.containsKey('/books')
    }

    void 'contributes the description to the document springdoc serves'() {
        given:
        def beanFactory = register()
        def openApi = new OpenAPI()

        when:
        beanFactory.getBean('grailsOpenApiCustomizer', OpenApiCustomizer).customise(openApi)

        then:
        openApi.paths.containsKey('/books')
    }

    void 'serves each configured group through springdoc, with the same criteria'() {
        when:
        def beanFactory = register('grails.openapi.groups.catalog.paths-to-match': '/books/**',
                'grails.openapi.groups.catalog.display-name': 'Catalog',
                'grails.openapi.groups.catalog.produces-to-match': 'application/json',
                'grails.openapi.groups.catalog.consumes-to-match': 'application/json',
                'grails.openapi.groups.catalog.headers-to-match': 'X-Api-Version=1')
        def group = beanFactory.getBeansOfType(GroupedOpenApi).values().find { it.group == 'catalog' }

        then:
        group.pathsToMatch == ['/books/**']
        group.displayName == 'Catalog'
        group.producesToMatch == ['application/json']
        group.consumesToMatch == ['application/json']
        group.headersToMatch == ['X-Api-Version=1']
    }

    void 'registers with springdoc a model converter, and what records the types springdoc resolves as it builds a document'() {
        when:
        def beanFactory = register()

        then: 'springdoc registers the converters and the builder customizers the application declares'
        beanFactory.getBeansOfType(ModelConverter).size() == 1
        beanFactory.getBeansOfType(OpenApiBuilderCustomizer).size() == 1
    }

    void 'registers nothing when the document is disabled'() {
        when:
        def beanFactory = register('grails.openapi.enabled': false)

        then:
        !beanFactory.containsBean(OpenApiGrailsPlugin.GENERATOR_BEAN_NAME)
        !beanFactory.containsBean('grailsOpenApiCustomizer')
        beanFactory.getBeansOfType(ModelConverter).isEmpty()
        beanFactory.getBeansOfType(OpenApiBuilderCustomizer).isEmpty()
    }

    void 'leaves the paths springdoc serves to it, past a catch-all mapping of the application'() {
        given:
        GrailsApplication previous = Holders.findApplication()

        when:
        UrlMappingsHolder holder = urlMappingsWith(config)

        then: 'springdoc answers the paths it is configured to serve'
        served.every { String path -> holder.matchAll(path).length == 0 }

        and: 'the catch-all answers every other'
        mapped.every { String path -> holder.matchAll(path).length > 0 }

        cleanup:
        Holders.setGrailsApplication(previous)

        where:
        config                                     | served                                                  | mapped
        [:]                                        | ['/v3/api-docs', '/v3/api-docs/catalog',
                                                      '/v3/api-docs.yaml', '/v3/api-docs/swagger-config']    | ['/v3/other']
        ['springdoc.api-docs.path': '/api/spec/']  | ['/api/spec', '/api/spec/catalog', '/api/spec.yaml']   | ['/v3/api-docs']
        ['springdoc.api-docs.enabled': false]      | []                                                      | ['/v3/api-docs']
    }

    void 'leaves Swagger UI to the application where it is not on the classpath'() {
        given:
        GrailsApplication previous = Holders.findApplication()

        when:
        UrlMappingsHolder holder = urlMappingsWith([:])

        then:
        holder.matchAll('/swagger-ui.html').length > 0
        holder.matchAll('/swagger-ui/index.html').length > 0

        cleanup:
        Holders.setGrailsApplication(previous)
    }

    void 'declares a dependency on the URL mappings plugin'() {
        expect:
        new OpenApiGrailsPlugin().dependsOn.containsKey('urlMappings')
    }

    private static DefaultListableBeanFactory register(Map<String, Object> config = [:]) {
        def beanFactory = new DefaultListableBeanFactory()
        def application = new DefaultGrailsApplication(BookController).tap { it.initialise() }
        beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        beanFactory.registerSingleton('grailsUrlMappingsHolder', urlMappingsHolder(application) {
            '/books'(controller: 'book', action: 'index', method: 'GET')
        })
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', config))
        def registrar = new OpenApiGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, environment, registrar.class).register(registrar)
        beanFactory
    }

    private static UrlMappingsHolder urlMappingsWith(Map<String, Object> config) {
        def context = new GenericApplicationContext()
        context.refresh()
        def application = new DefaultGrailsApplication(([CatchAllUrlMappings] + new OpenApiGrailsPlugin().providedArtefacts) as Class[])
        application.config = new PropertySourcesConfig(config)
        application.initialise()
        Holders.setGrailsApplication(application)
        def discovery = new DefaultPluginDiscovery()
        discovery.init(context.environment)
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, new DefaultGrailsPluginManager(application, discovery))
        def factoryBean = new UrlMappingsHolderFactoryBean()
        factoryBean.applicationContext = context
        factoryBean.afterPropertiesSet()
        (UrlMappingsHolder) factoryBean.object
    }

    private static UrlMappingsHolder urlMappingsHolder(GrailsApplication application, Closure mappings) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings(mappings))
    }
}

class CatchAllUrlMappings {

    static mappings = {
        '/**'(controller: 'book', action: 'index')
    }
}

@Artefact('Controller')
class BookController {
    def index() { }
    def show() { }
}
