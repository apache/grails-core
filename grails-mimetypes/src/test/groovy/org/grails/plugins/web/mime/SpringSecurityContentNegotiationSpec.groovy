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

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.config.annotation.EnableWebMvc

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication

import spock.lang.Specification

class SpringSecurityContentNegotiationSpec extends Specification {

    void "Spring Security starts with Grails' MIME configuration, which gives it no strategy of its own to adopt"() {
        given:
        def context = new AnnotationConfigWebApplicationContext()
        context.servletContext = new MockServletContext()
        context.register(SecurityWebConfiguration)

        when:
        context.refresh()

        then: 'the one strategy is the MVC manager, which keeps Spring negotiation for Spring Security'
        context.getBean(SecurityFilterChain)
        context.getBeansOfType(ContentNegotiationStrategy).keySet() == ['mvcContentNegotiationManager'] as Set

        and:
        def manager = context.getBean('mvcContentNegotiationManager', ContentNegotiationManager)
        def request = new MockHttpServletRequest('GET', '/books')
        request.addHeader('Accept', 'application/json')
        def resolved = manager.resolveMediaTypes(new ServletWebRequest(request))
        resolved.size() == 1
        resolved.first().type == MediaType.APPLICATION_JSON.type
        resolved.first().subtype == MediaType.APPLICATION_JSON.subtype

        cleanup:
        context.close()
    }

    void 'an unknown media type is not widened to the Grails defaults'() {
        given:
        def context = new AnnotationConfigWebApplicationContext()
        context.servletContext = new MockServletContext()
        context.register(SecurityWebConfiguration)
        context.refresh()
        def manager = context.getBean('mvcContentNegotiationManager', ContentNegotiationManager)
        def request = new MockHttpServletRequest('GET', '/books')
        request.addHeader('Accept', 'application/vnd.myapi.v2+json')

        when: "a media type absent from grails.mime.types is requested"
        def resolved = manager.resolveMediaTypes(new ServletWebRequest(request))

        then: "Spring reports exactly what was asked for, so an endpoint can still answer 406"
        resolved == [MediaType.parseMediaType('application/vnd.myapi.v2+json')]

        and: "it is not replaced by the Grails default list, which contains */*"
        !resolved.contains(MediaType.ALL)

        cleanup:
        context.close()
    }

    void 'a format alias configured by Grails is registered with Spring'() {
        given:
        def context = new AnnotationConfigWebApplicationContext()
        context.servletContext = new MockServletContext()
        context.register(SecurityWebConfiguration)
        context.refresh()

        expect: "Spring resolves the Grails extension through its own alias mechanism, for a format Spring does not know"
        context.getBean('mvcContentNegotiationManager', ContentNegotiationManager)
                .getMediaTypeMappings()['hal'] == MediaType.parseMediaType('application/hal+json')

        cleanup:
        context.close()
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(MimeTypesConfiguration)
    static class SecurityWebConfiguration {

        @Bean
        GrailsApplication grailsApplication(ApplicationContext applicationContext) {
            def application = new DefaultGrailsApplication()
            application.setApplicationContext(applicationContext)
            application
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) {
            http.authorizeHttpRequests { authorization ->
                authorization.anyRequest().permitAll()
            }
            http.build()
        }
    }
}
