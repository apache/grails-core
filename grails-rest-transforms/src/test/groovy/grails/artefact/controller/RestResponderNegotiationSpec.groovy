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
package grails.artefact.controller

import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.web.context.request.RequestContextHolder

import grails.artefact.Controller
import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.SelectiveRenderer
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification
import spock.lang.Unroll

/**
 * respond answers in the first type the client accepts that the action responds in and a renderer renders the value in.
 * This module's tests have no grails-xml, so nothing renders XML unless a test registers a renderer for it.
 */
class RestResponderNegotiationSpec extends Specification {

    void setup() {
        new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication()).initialize()
        // as the converters plugin registers it in an application
        JSON.registerObjectMarshaller(new ValidationErrorsMarshaller())
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
        RequestContextHolder.resetRequestAttributes()
    }

    @Unroll
    void "a client that accepts any type, with #description, is answered in the first of the action's formats a renderer renders"() {
        given:
        GrailsWebRequest webRequest = bindRequest(accept)

        when:
        controller().respond([title: 'Grails'])

        then:
        webRequest.response.status == 200
        webRequest.response.contentType.startsWith('application/json')
        webRequest.response.contentAsString == '{"title":"Grails"}'

        where:
        description         | accept
        'no Accept header'  | null
        'an Accept of */*'  | '*/*'
    }

    @Unroll
    void "validation errors are answered in the first type, with #description, that a renderer writes them in"() {
        given:
        GrailsWebRequest webRequest = bindRequest(accept)

        when:
        controller().respond(invalidCommand())

        then:
        webRequest.response.status == 422
        webRequest.response.contentType.contains('json')

        where:
        description                               | accept
        'no Accept header'                        | null
        'an Accept of */*'                        | '*/*'
        'XML, which nothing renders, before JSON' | 'application/xml, application/json'
    }

    void 'a client that lists a type nothing renders ahead of JSON is answered in JSON, labelled so'() {
        given:
        GrailsWebRequest webRequest = bindRequest('application/xml, application/json')

        when:
        controller().respond([title: 'Grails'])

        then:
        webRequest.response.status == 200
        webRequest.response.contentType.startsWith('application/json')
    }

    @Unroll
    void 'a client that accepts only #accept is answered with #status for #description'() {
        given:
        GrailsWebRequest webRequest = bindRequest(accept)

        when:
        controller().respond(value)

        then:
        webRequest.response.status == status

        where: "404 for validation errors where one of the action's formats was tried, as in Grails 8, otherwise 406"
        description         | accept            | value                 || status
        'a value'           | 'application/xml' | [title: 'Grails']     || 406
        'validation errors' | 'application/xml' | invalidCommand()      || 404
        'a value'           | 'text/csv'        | [title: 'Grails']     || 406
        'validation errors' | 'text/csv'        | invalidCommand()      || 406
    }

    void 'a renderer that cannot render the value is passed over for the next format'() {
        given:
        def xmlRenderer = Mock(Renderer, additionalInterfaces: [SelectiveRenderer]) {
            getTargetType() >> Object
            getMimeTypes() >> ([MimeType.XML, MimeType.TEXT_XML] as MimeType[])
        }
        GrailsWebRequest webRequest = bindRequest(null)

        when:
        controller(xmlRenderer).respond([title: 'Grails'])

        then:
        1 * ((SelectiveRenderer) xmlRenderer).canRender([title: 'Grails'], _ as RenderContext) >> false
        0 * xmlRenderer.render(_, _)
        webRequest.response.contentType.startsWith('application/json')
        webRequest.response.contentAsString == '{"title":"Grails"}'
    }

    void 'a renderer is asked whether it can render the value in the type it would render it in'() {
        given:
        MimeType asked = null
        def xmlRenderer = Mock(Renderer, additionalInterfaces: [SelectiveRenderer]) {
            getTargetType() >> Object
            getMimeTypes() >> ([MimeType.XML, MimeType.TEXT_XML] as MimeType[])
        }
        GrailsWebRequest webRequest = bindRequest('text/html, application/xml')

        when:
        controller(xmlRenderer).respond([title: 'Grails'], formats: ['xml'])

        then:
        1 * ((SelectiveRenderer) xmlRenderer).canRender(_, _) >> { Object value, RenderContext context ->
            asked = context.acceptMimeType
            false
        }
        asked.extension == 'xml'
        webRequest.response.status == 406

        and: 'the response is in no type once it was asked, as it is answered in none'
        webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPE) == null
    }

    void 'a renderer that can render the value renders it'() {
        given:
        def xmlRenderer = Mock(Renderer, additionalInterfaces: [SelectiveRenderer]) {
            getTargetType() >> Object
            getMimeTypes() >> ([MimeType.XML, MimeType.TEXT_XML] as MimeType[])
        }
        GrailsWebRequest webRequest = bindRequest(null)

        when:
        controller(xmlRenderer).respond([title: 'Grails'])

        then:
        1 * ((SelectiveRenderer) xmlRenderer).canRender(_, _) >> true
        1 * xmlRenderer.render([title: 'Grails'], _ as RenderContext)
        webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPE).extension == 'xml'
    }

    private static GrailsWebRequest bindRequest(String accept) {
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest()
        if (accept) {
            webRequest.currentRequest.addHeader('Accept', accept)
        }
        webRequest.controllerName = 'book'
        webRequest.actionName = 'show'
        webRequest
    }

    private static NegotiatingController controller(Renderer... renderers) {
        def registry = new DefaultRendererRegistry()
        registry.setRenderers(renderers)
        registry.initialize()
        new NegotiatingController(rendererRegistry: registry)
    }

    private static NegotiatingCommand invalidCommand() {
        def command = new NegotiatingCommand(title: '')
        command.errors = new BeanPropertyBindingResult(command, 'negotiatingCommand')
        command.errors.rejectValue('title', 'blank')
        command
    }
}

class NegotiatingController implements Controller, RestResponder {
    static responseFormats = ['xml', 'json']
}

class NegotiatingCommand {
    String title
    Errors errors
}
