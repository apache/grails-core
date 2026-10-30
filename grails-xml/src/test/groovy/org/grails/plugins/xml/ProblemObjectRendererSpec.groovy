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
import grails.rest.render.xml.XmlRenderer
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * A renderer the application registers for Object stands in for the default renderer of its format, which does not
 * write a problem: Spring MVC writes it, in the type its content negotiation selects.
 */
class ProblemObjectRendererSpec extends Specification implements ControllerUnitTest<ProblemObjectRendererController> {

    @Configuration
    static class RendererConfiguration {

        @Bean
        XmlRenderer<Object> objectXmlRenderer() {
            new ObjectXmlRenderer()
        }
    }

    void 'a client that asks for #accept is answered with #type, which the renderer for Object does not render'() {
        given:
        request.addHeader('Accept', accept)

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['json', 'xml'])

        then:
        response.status == 409
        response.contentType.startsWith(type)
        !response.contentAsString.contains('<object/>')

        where:
        accept                    || type
        'application/xml'         || 'application/problem+json'
        'application/problem+xml' || 'application/problem+xml'
    }
}

@Artefact('Controller')
class ProblemObjectRendererController { }

class ObjectXmlRenderer extends XmlRenderer<Object> {

    ObjectXmlRenderer() {
        super(Object)
    }

    @Override
    protected void renderXml(Object object, RenderContext context) {
        context.writer << '<object/>'
    }
}
