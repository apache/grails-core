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
package org.grails.web.mime

import grails.web.mime.MimeType
import grails.config.Config
import grails.core.DefaultGrailsApplication
import org.grails.config.PropertySourcesConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import spock.lang.Specification

class GrailsContentNegotiationStrategySpec extends Specification {

    private static final MediaType APPLICATION_ALIAS = MediaType.parseMediaType('application/vnd.books+xml')

    private static final MimeType[] MIME_TYPES = [
            new MimeType('text/html', 'html'),
            new MimeType('application/json', 'json'),
            new MimeType('application/xml', 'xml'),
            MimeType.ALL
    ] as MimeType[]

    void "the Web MVC configurer contributes format aliases and leaves Spring's strategies alone"() {
        given:
        def configurer = Mock(ContentNegotiationConfigurer)

        when:
        new GrailsMimeTypesWebMvcConfigurer(strategy()).configureContentNegotiation(configurer)

        then: "Spring keeps its own header strategy, so unknown media types still get a 406"
        0 * configurer.strategies(_)

        and: "the configured extensions are registered as Spring format aliases"
        1 * configurer.mediaTypes({ Map<String, MediaType> aliases ->
            aliases['json'] == MediaType.APPLICATION_JSON &&
                    aliases['xml'] == MediaType.APPLICATION_XML &&
                    !aliases.containsKey('html') &&
                    !aliases.containsKey('all')
        })
    }

    void 'real default aliases prefer application XML and do not mark browser formats safe'() {
        given:
        def configured = (MimeType.createDefaults().toList() + new MimeType('text/ invalid', 'csv')) as MimeType[]
        def grailsConfigurer = new GrailsMimeTypesWebMvcConfigurer(new GrailsContentNegotiationStrategy(configured, config([:])))
        def mvc = new WebMvcConfigurationSupport() {
            @Override
            protected void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                grailsConfigurer.configureContentNegotiation(configurer)
                configurer.favorParameter(true).parameterName('format')
            }
        }
        def manager = mvc.mvcContentNegotiationManager()
        def request = new MockHttpServletRequest()
        request.setParameter('format', 'xml')

        expect:
        manager.resolveMediaTypes(new ServletWebRequest(request)) == [MediaType.APPLICATION_XML]
        manager.allFileExtensions.disjoint(['html', 'js', 'css', 'pdf', 'form', 'multipartform'])
    }

    void 'the format aliases do not steer Spring MVC while parameter negotiation is off'() {
        given:
        def grailsConfigurer = new GrailsMimeTypesWebMvcConfigurer(strategy())
        def mvc = new WebMvcConfigurationSupport() {
            @Override
            protected void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                grailsConfigurer.configureContentNegotiation(configurer)
            }
        }
        def manager = mvc.mvcContentNegotiationManager()
        def request = new MockHttpServletRequest()
        request.setParameter('format', 'xml')
        request.addHeader('Accept', MediaType.APPLICATION_JSON_VALUE)

        expect: 'the alias is registered, but Spring answers the Accept header and ignores the format parameter'
        manager.mediaTypeMappings['xml'] == MediaType.APPLICATION_XML
        manager.resolveMediaTypes(new ServletWebRequest(request)) == [MediaType.APPLICATION_JSON]
    }

    void 'an alias configured by Boot or the application overrides the Grails one'() {
        given:
        def context = new AnnotationConfigWebApplicationContext()
        context.servletContext = new MockServletContext()
        context.register(AliasOverrideConfiguration)
        context.refresh()

        when:
        def manager = context.getBean('mvcContentNegotiationManager', ContentNegotiationManager)

        then: 'the application alias wins, while the aliases it does not set stay registered by Grails'
        manager.mediaTypeMappings['xml'] == APPLICATION_ALIAS
        manager.mediaTypeMappings['hal'] == MediaType.parseMediaType('application/hal+json')

        cleanup:
        context.close()
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class AliasOverrideConfiguration {

        @Bean
        GrailsMimeTypesWebMvcConfigurer grailsMimeTypesWebMvcConfigurer() {
            new GrailsMimeTypesWebMvcConfigurer(new GrailsContentNegotiationStrategy(
                    MimeType.createDefaults(), new DefaultGrailsApplication().config))
        }

        @Bean
        @Order(0)
        WebMvcConfigurer applicationAliases() {
            // Ordered as Boot's WebMvcAutoConfigurationAdapter applies spring.mvc.contentnegotiation.media-types.*
            new WebMvcConfigurer() {
                @Override
                void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                    configurer.mediaType('xml', APPLICATION_ALIAS)
                }
            }
        }
    }

    private static GrailsContentNegotiationStrategy strategy(Config config = config([:])) {
        return new GrailsContentNegotiationStrategy(MIME_TYPES, config)
    }

    private static Config config(Map<String, Object> values) {
        def propertySources = new MutablePropertySources()
        propertySources.addLast(new MapPropertySource('test', values))
        return new PropertySourcesConfig(propertySources)
    }
}
