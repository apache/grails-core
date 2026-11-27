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
import org.springframework.http.ProblemDetail

import grails.artefact.Artefact
import grails.rest.render.json.JsonRenderer
import grails.rest.render.xml.XmlRenderer
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class ProblemRendererSettingsSpec extends Specification implements ControllerUnitTest<ProblemRendererSettingsController> {

    @Configuration
    static class RendererConfiguration {

        @Bean
        XmlRenderer<ProblemDetail> problemXmlRenderer() {
            new XmlRenderer<ProblemDetail>(ProblemDetail).tap { excludes = ['detail'] }
        }

        @Bean
        JsonRenderer<ProblemDetail> problemJsonRenderer() {
            new JsonRenderer<ProblemDetail>(ProblemDetail).tap { excludes = ['detail'] }
        }
    }

    void 'a renderer registered for problems writes them in #format as Spring MVC writes them, whatever its excludes'() {
        given:
        request.addHeader('Accept', accept)

        when:
        controller.respond(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, 'The title is taken'), formats: ['json', 'xml'])

        then:
        response.status == 409
        response.contentType.startsWith(accept)
        response.contentAsString.contains('The title is taken')

        where:
        format | accept
        'XML'  | 'application/problem+xml'
        'JSON' | 'application/problem+json'
    }
}

@Artefact('Controller')
class ProblemRendererSettingsController { }
