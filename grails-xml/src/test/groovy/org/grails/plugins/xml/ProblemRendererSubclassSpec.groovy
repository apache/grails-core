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
import grails.rest.render.RenderContext
import grails.rest.render.json.JsonRenderer
import grails.rest.render.xml.XmlRenderer
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class ProblemRendererSubclassSpec extends Specification implements ControllerUnitTest<ProblemRendererSubclassController> {

    @Configuration
    static class RendererConfiguration {

        @Bean
        XmlRenderer<ProblemDetail> customProblemXmlRenderer() {
            new CustomProblemXmlRenderer()
        }

        @Bean
        JsonRenderer<ProblemDetail> customProblemJsonRenderer() {
            new CustomProblemJsonRenderer()
        }
    }

    void 'a subclass of a renderer registered for problems that renders in #format itself renders them'() {
        given:
        request.addHeader('Accept', accept)

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['json', 'xml'])

        then:
        response.contentAsString == written

        where:
        format | accept                     || written
        'XML'  | 'application/problem+xml'  || '<custom/>'
        'JSON' | 'application/problem+json' || '{"custom":true}'
    }
}

@Artefact('Controller')
class ProblemRendererSubclassController { }

class CustomProblemXmlRenderer extends XmlRenderer<ProblemDetail> {

    CustomProblemXmlRenderer() {
        super(ProblemDetail)
    }

    @Override
    protected void renderXml(Object object, RenderContext context) {
        context.writer << '<custom/>'
    }
}

class CustomProblemJsonRenderer extends JsonRenderer<ProblemDetail> {

    CustomProblemJsonRenderer() {
        super(ProblemDetail)
    }

    @Override
    protected void renderJson(ProblemDetail object, RenderContext context) {
        context.writer << '{"custom":true}'
    }
}
