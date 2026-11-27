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
package org.grails.plugins.xml

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * A web unit test negotiates the type of a problem as the application does: with Spring MVC's content negotiation
 * manager, which spring.mvc.contentnegotiation.* configures.
 */
class ControllerUnitTestContentNegotiationPropertiesSpec extends Specification implements ControllerUnitTest<NegotiatingController> {

    Closure doWithConfig() {
        { config ->
            config['spring.mvc.contentnegotiation.favor-parameter'] = true
            config['spring.mvc.contentnegotiation.parameter-name'] = 'type'
            config['spring.mvc.contentnegotiation.media-types.xml'] = 'application/problem+xml'
            config['spring.mvc.contentnegotiation.default-content-types'] = 'application/problem+xml'
        }
    }

    void 'the type of a problem is negotiated as spring.mvc.contentnegotiation configures it, #description'() {
        given:
        if (parameter) {
            request.addParameter('type', parameter)
        }
        if (accept) {
            request.addHeader('Accept', accept)
        }

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType.startsWith(type)

        where:
        description                                       | parameter | accept                     || type
        'by the parameter, ahead of the header'           | 'json'    | 'application/problem+xml'  || 'application/problem+json'
        'by a media type the properties give a format'    | 'xml'     | 'application/problem+json' || 'application/problem+xml'
        'by the default content type, without a header'   | null      | null                       || 'application/problem+xml'
    }
}

class ControllerUnitTestWebMvcConfigurerNegotiationSpec extends Specification implements ControllerUnitTest<NegotiatingController> {

    Closure doWithConfig() {
        { config -> config['spring.mvc.contentnegotiation.default-content-types'] = 'application/problem+json' }
    }

    @Configuration
    static class Negotiation {

        @Bean
        WebMvcConfigurer problemXmlByDefault() {
            new WebMvcConfigurer() {
                @Override
                void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                    configurer.defaultContentType(MediaType.APPLICATION_PROBLEM_XML)
                }
            }
        }
    }

    void "a WebMvcConfigurer of the test configures the negotiation after spring.mvc.contentnegotiation, as in the application"() {
        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+xml')
    }
}

@Artefact('Controller')
class NegotiatingController { }
