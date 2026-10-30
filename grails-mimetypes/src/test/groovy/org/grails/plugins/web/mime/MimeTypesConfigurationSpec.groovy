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
package org.grails.plugins.web.mime

import java.util.function.Supplier

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockServletContext
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.spring.BeanBuilder
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeProvider
import org.grails.web.mime.DefaultMimeUtility
import org.grails.web.mime.GrailsContentNegotiationStrategy
import org.grails.web.mime.GrailsMimeTypesWebMvcConfigurer
import spock.lang.Specification

class MimeTypesConfigurationSpec extends Specification {

    void "test when no mimeTypes configured then the defaults should be used"() {
        when:
        MimeType[] mimeTypes = resolveMimeTypes(null)

        then:
        MimeType.createDefaults() == mimeTypes
    }

    void "test the defaults provide the full set of supported mime types"() {
        when:
        MimeType[] mimeTypes = resolveMimeTypes(null)

        then: "every extension historically declared in application.yml is available out of the box"
        mimeTypes.find { it.extension == 'all' && it.name == '*/*' }
        mimeTypes.find { it.extension == 'form' && it.name == 'application/x-www-form-urlencoded' }
        mimeTypes.find { it.extension == 'multipartForm' && it.name == 'multipart/form-data' }
        mimeTypes.find { it.extension == 'css' && it.name == 'text/css' }
        mimeTypes.find { it.extension == 'csv' && it.name == 'text/csv' }
        mimeTypes.find { it.extension == 'js' && it.name == 'text/javascript' }
        mimeTypes.find { it.extension == 'pdf' && it.name == 'application/pdf' }
        mimeTypes.find { it.extension == 'rss' && it.name == 'application/rss+xml' }
        mimeTypes.find { it.extension == 'atom' && it.name == 'application/atom+xml' }
        mimeTypes.find { it.extension == 'text' && it.name == 'text/plain' }
        mimeTypes.find { it.extension == 'hal' && it.name == 'application/hal+json' }
        mimeTypes.find { it.extension == 'html' && it.name == 'text/html' }
        mimeTypes.find { it.extension == 'json' && it.name == 'application/json' }
        mimeTypes.find { it.extension == 'xml' && it.name == 'application/xml' }
    }

    void "test a declared configuration replaces the defaults by default"() {
        when: "only a single extension is declared"
        MimeType[] mimeTypes = resolveMimeTypes([json: 'application/json'])

        then: "the declared configuration is authoritative and the defaults are not merged in"
        mimeTypes*.name == ['application/json']
        !mimeTypes.find { it.extension == 'html' }
        !mimeTypes.find { it.extension == 'xml' }
    }

    void "test a declared configuration keeps its declared order so the first entry is the default format"() {
        when:
        MimeType[] mimeTypes = resolveMimeTypes([html: ['text/html', 'application/xhtml+xml'], json: 'application/json'])

        then:
        mimeTypes[0].extension == 'html'
        mimeTypes*.name == ['text/html', 'application/xhtml+xml', 'application/json']
    }

    void "test mergeDefaults appends the defaults the user did not declare"() {
        when: "a single custom extension is declared with merging enabled"
        MimeType[] mimeTypes = resolveMimeTypes([custom: 'application/x-custom'], true)

        then: "the declared type is first and the inherited defaults are appended"
        mimeTypes[0].extension == 'custom'
        mimeTypes.find { it.extension == 'custom' && it.name == 'application/x-custom' }
        mimeTypes.find { it.extension == 'html' }
        mimeTypes.find { it.extension == 'json' }
        mimeTypes.find { it.extension == 'all' }
    }

    void "test mergeDefaults overrides a default for the same extension"() {
        when: "the json extension is overridden with merging enabled"
        MimeType[] mimeTypes = resolveMimeTypes([json: 'application/json'], true)

        then: "only the configured json mime type is present"
        mimeTypes.findAll { it.extension == 'json' }*.name == ['application/json']

        and: "other defaults are untouched"
        mimeTypes.find { it.extension == 'html' && it.name == 'text/html' }
        mimeTypes.findAll { it.extension == 'xml' }*.name == ['text/xml', 'application/xml']
    }

    void 'Grails format resolution resolves against the MIME types of the MimeUtility, as request.format does'() {
        given:
        def custom = [new MimeType('application/vnd.acme+json', 'acme'), new MimeType('text/html', 'html')] as MimeType[]
        def mimeUtility = Stub(ObjectProvider) { getObject() >> new DefaultMimeUtility(custom) }

        expect:
        MimeTypesConfiguration.grailsMimeTypesWebMvcConfigurer(mimeUtility, new DefaultGrailsApplication())
                .contentNegotiationStrategy.configuredMimeTypes as List == custom as List
    }

    void 'a MIME type provider that needs #infrastructure does not close a startup cycle'() {
        given: 'circular references disallowed, as Spring Boot does, so that a cycle fails the refresh'
        def context = new AnnotationConfigWebApplicationContext()
        context.allowCircularReferences = false
        context.servletContext = new MockServletContext()
        context.register(configuration)

        when:
        context.refresh()

        then:
        noExceptionThrown()
        context.getBean(GrailsMimeTypesWebMvcConfigurer).contentNegotiationStrategy instanceof GrailsContentNegotiationStrategy

        and: 'the data formats are registered with Spring MVC, a format Spring does not know among them'
        context.getBean(ContentNegotiationManager).getMediaTypeMappings().get('hal') == MediaType.parseMediaType('application/hal+json')

        cleanup:
        context.close()

        where:
        infrastructure                    | configuration
        'a locale resolver'               | CycleConfiguration
        "Spring MVC's handler adapter"    | HandlerAdapterCycleConfiguration
    }

    void 'the data formats grails.mime.types declares are registered with Spring MVC, in place of the defaults'() {
        when:
        Map<String, MediaType> mappings = mediaTypeMappings([json: 'application/vnd.acme+json', csv: 'text/csv', html: 'text/html'])

        then:
        mappings.json == MediaType.parseMediaType('application/vnd.acme+json')
        mappings.csv == MediaType.parseMediaType('text/csv')
        !mappings.containsKey('hal')
    }

    void 'with mergeDefaults, the defaults grails.mime.types does not declare are registered with Spring MVC too'() {
        when:
        Map<String, MediaType> mappings = mediaTypeMappings([json: 'application/vnd.acme+json'], true)

        then:
        mappings.json == MediaType.parseMediaType('application/vnd.acme+json')
        mappings.hal == MediaType.parseMediaType('application/hal+json')
        mappings.csv == MediaType.parseMediaType('text/csv')
    }

    void 'the MIME types a provider adds are not registered with Spring MVC, which asks for its formats while it starts'() {
        given:
        def provider = new MimeTypeProvider() {
            @Override
            MimeType[] getMimeTypes() {
                [new MimeType('application/vnd.acme+xml', 'xml')] as MimeType[]
            }
        }

        def known = []

        when:
        Map<String, MediaType> mappings = mediaTypeMappings([json: 'application/vnd.acme+json'], false, known, provider)

        then: "Grails knows the provider's type"
        known*.name.contains('application/vnd.acme+xml')

        and: 'Spring MVC is not given it'
        mappings.json == MediaType.parseMediaType('application/vnd.acme+json')
        mappings.xml != MediaType.parseMediaType('application/vnd.acme+xml')
    }

    private static Map<String, MediaType> mediaTypeMappings(Map types, boolean mergeDefaults = false, List<MimeType> known = [],
                                                            MimeTypeProvider... providers) {
        def application = new DefaultGrailsApplication()
        application.config['grails.mime.types'] = types
        application.config['grails.mime.mergeDefaults'] = mergeDefaults
        def context = new GenericWebApplicationContext(new MockServletContext())
        application.setApplicationContext(context)
        context.registerBean('grailsApplication', GrailsApplication, { application } as Supplier<GrailsApplication>)
        providers.eachWithIndex { MimeTypeProvider provider, int index ->
            context.registerBean("mimeTypeProvider${index}".toString(), MimeTypeProvider, { provider } as Supplier<MimeTypeProvider>)
        }
        new AnnotatedBeanDefinitionReader(context).register(MvcConfiguration)
        context.refresh()
        try {
            known.addAll(context.getBean('mimeTypes', MimeType[]))
            return context.getBean(ContentNegotiationManager).mediaTypeMappings
        }
        finally {
            context.close()
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(MimeTypesConfiguration)
    static class MvcConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(MimeTypesConfiguration)
    static class CycleConfiguration {

        @Bean
        GrailsApplication grailsApplication(ApplicationContext applicationContext) {
            def application = new DefaultGrailsApplication()
            application.setApplicationContext(applicationContext)
            application
        }

        @Bean
        MimeTypeProvider localeAwareMimeTypeProvider(LocaleResolver localeResolver) {
            new MimeTypeProvider() {
                @Override
                MimeType[] getMimeTypes() {
                    [] as MimeType[]
                }
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(MimeTypesConfiguration)
    static class HandlerAdapterCycleConfiguration {

        @Bean
        GrailsApplication grailsApplication(ApplicationContext applicationContext) {
            def application = new DefaultGrailsApplication()
            application.setApplicationContext(applicationContext)
            application
        }

        @Bean
        MimeTypeProvider adapterAwareMimeTypeProvider(RequestMappingHandlerAdapter handlerAdapter) {
            new MimeTypeProvider() {
                @Override
                MimeType[] getMimeTypes() {
                    [] as MimeType[]
                }
            }
        }
    }

    private MimeType[] resolveMimeTypes(Map config, boolean mergeDefaults = false) {
        def application = new DefaultGrailsApplication()
        if (config != null) {
            application.config['grails.mime.types'] = config
        }
        if (mergeDefaults) {
            application.config['grails.mime.mergeDefaults'] = true
        }
        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        application.setApplicationContext(bb.createApplicationContext())
        MimeTypesConfiguration mimeTypesConfiguration = application.mainContext.getBean(MimeTypesConfiguration)
        return mimeTypesConfiguration.mimeTypes()
    }
}
