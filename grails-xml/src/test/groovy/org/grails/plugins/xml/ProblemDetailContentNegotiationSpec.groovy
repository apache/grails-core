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
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.accept.FixedContentNegotiationStrategy
import org.springframework.web.accept.HeaderContentNegotiationStrategy
import org.springframework.web.accept.ParameterContentNegotiationStrategy
import org.springframework.web.context.request.NativeWebRequest

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * A problem is written in the type Spring MVC's content negotiation selects for it, as the application configures it: a
 * strategy of its own, the format parameter spring.mvc.contentnegotiation.favor-parameter reads, the Accept header and a
 * default content type, in the order Spring MVC asks them.
 */
class ProblemDetailContentNegotiationSpec extends Specification implements ControllerUnitTest<ProblemNegotiationController> {

    @Configuration
    static class Negotiation {

        @Bean
        ContentNegotiationManager mvcContentNegotiationManager() {
            def parameter = new ParameterContentNegotiationStrategy([json      : MediaType.APPLICATION_JSON,
                                                                     xml       : MediaType.APPLICATION_XML,
                                                                     problemxml: MediaType.APPLICATION_PROBLEM_XML])
            parameter.useRegisteredExtensionsOnly = true
            new ContentNegotiationManager([new ListedTypesStrategy(), parameter, new HeaderContentNegotiationStrategy(),
                                           new FixedContentNegotiationStrategy(MediaType.APPLICATION_PROBLEM_XML)])
        }
    }

    void 'the format parameter Spring MVC reads decides the type of a problem, whatever the Accept header asks for, #format'() {
        given:
        request.addParameter('format', format)
        request.addHeader('Accept', accept)

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType.startsWith(type)

        where:
        format       | accept                     || type
        'json'       | 'application/problem+xml'  || 'application/problem+json'
        'xml'        | 'application/problem+xml'  || 'application/problem+json'
        'problemxml' | 'application/problem+json' || 'application/problem+xml'
    }

    void 'a request whose format parameter Spring MVC reads is answered with the problem though Spring MVC cannot read its Accept header'() {
        given:
        request.addParameter('format', 'json')
        request.addHeader('Accept', 'application/json;q=2')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+json')
        response.json.status == 409
    }

    void 'a format Spring MVC does not know is answered with the status alone'() {
        given:
        request.addParameter('format', 'html')
        request.addHeader('Accept', 'application/problem+xml')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentAsString == ''
        view == null
    }

    void 'a format Spring MVC does not know fails as Spring MVC fails it for a status that is not an error status'() {
        given:
        request.addParameter('format', 'html')
        request.addHeader('Accept', 'application/problem+xml')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), status: 200, formats: ['xml', 'json'])

        then: "which Spring MVC's exception resolver answers with 406"
        thrown(HttpMediaTypeNotAcceptableException)
        response.contentAsString == ''
        view == null
    }

    void 'a client that sends no Accept header is answered in the default content type Spring MVC is configured with'() {
        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+xml')
    }

    void 'the types a strategy of the application resolves are ordered as Spring MVC orders them, #accepted'() {
        given:
        request.addHeader('X-Accepted-Types', accepted)

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.contentType.startsWith(type)

        where:
        accepted                                                || type
        'application/problem+json;q=0.5, application/problem+xml' || 'application/problem+xml'
        '*/*, application/*+xml'                                || 'application/problem+json'
    }

    void 'without a format parameter, the Accept header decides as it does without such a configuration, #accept'() {
        given:
        request.addHeader('Accept', accept)

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType == type

        where:
        accept                    || type
        'application/problem+xml' || 'application/problem+xml'
        'application/json;q=2'    || null
    }
}

/** A strategy an application configures Spring MVC's content negotiation with, which reads a header of its own. */
class ListedTypesStrategy implements ContentNegotiationStrategy {

    @Override
    List<MediaType> resolveMediaTypes(NativeWebRequest request) {
        String listed = request.getHeader('X-Accepted-Types')
        listed ? MediaType.parseMediaTypes(listed) : MEDIA_TYPE_ALL_LIST
    }
}

@Artefact('Controller')
class ProblemNegotiationController { }
