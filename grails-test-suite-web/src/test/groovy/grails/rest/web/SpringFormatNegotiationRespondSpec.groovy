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
package grails.rest.web

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.HandlerMapping

import grails.artefact.Artefact
import grails.config.Settings
import grails.testing.web.controllers.ControllerUnitTest
import grails.validation.Validateable
import grails.web.mapping.UrlMappingInfo
import org.grails.web.mime.GrailsContentNegotiationManager
import spock.lang.Specification

/**
 * With {@code grails.mime.negotiation.spring}, {@code respond} answers in the format Spring MVC's content negotiation
 * resolves for the request, and a request that accepts none of the action's formats is not acceptable.
 */
class SpringFormatNegotiationRespondSpec extends Specification implements ControllerUnitTest<NegotiatedResponseController> {

    Closure doWithConfig() {
        { config ->
            config[Settings.WEB_RENDERING_JSON_SPRING] = true
            config[Settings.MIME_NEGOTIATION_SPRING] = true
        }
    }

    void "a web unit test negotiates with the manager Spring MVC negotiates with in an application"() {
        expect:
        applicationContext.getBean('mvcContentNegotiationManager', ContentNegotiationManager) instanceof GrailsContentNegotiationManager
    }

    void 'the request of the action a web unit test calls is handled by a URL mapping, as the URL mappings mark it in an application'() {
        when:
        controller

        then:
        request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof UrlMappingInfo
    }

    void 'what Spring MVC writes for an action of a web unit test is negotiated with the format the request names, as in an application'() {
        given:
        request.addHeader('Accept', 'application/json')
        params.format = 'xml'

        when: 'the test calls the action of the controller'
        controller

        then:
        applicationContext.getBean('mvcContentNegotiationManager', ContentNegotiationManager)
                .resolveMediaTypes(new ServletWebRequest(request)).first() == MediaType.TEXT_XML
    }

    void 'a body of a type with the +json suffix is bound as JSON'() {
        given:
        request.method = 'POST'
        request.contentType = 'application/merge-patch+json'
        request.content = '{"title":"Grails"}'.bytes

        when:
        def model = controller.save()

        then:
        request.format == 'json'
        model.title == 'Grails'
    }

    void 'a client that accepts #accept is answered in #format by an action that responds in XML and JSON'() {
        given:
        request.addHeader('Accept', accept)

        when:
        controller.respond([title: 'Grails'], formats: ['xml', 'json'])

        then:
        response.status == 200
        response.contentType.contains(format)

        where:
        accept                     || format
        '*/*, application/json'    || 'json'
        'application/*+json'       || 'json'
        'text/*'                   || 'xml'
        'application/merge-patch+json' || 'json'
        'application/soap+xml'     || 'xml'
    }

    void 'a client that accepts no format of the action is not acceptable, as to Spring MVC'() {
        given:
        request.addHeader('Accept', accept)

        when:
        controller.respond([title: 'Grails'], formats: ['json'])

        then:
        response.status == 406

        where:
        accept << ['image/png', 'image/*', 'application/json;q=2']
    }

    void 'a request that names a format no type is configured for is not acceptable'() {
        given:
        request.addHeader('Accept', 'application/json')
        params.format = 'unknown'

        when:
        controller.respond([title: 'Grails'], formats: ['json'])

        then:
        response.status == 406
    }

    void 'the all format names no type, so the Accept header decides'() {
        given:
        request.addHeader('Accept', 'application/json')
        params.format = 'all'

        when:
        controller.respond([title: 'Grails'], formats: ['xml', 'json'])

        then:
        response.contentType.startsWith('application/json')
        response.json == [title: 'Grails']
    }

    void 'a problem for a client whose Accept header Spring MVC cannot read is answered with its status alone, as Spring MVC answers it'() {
        given:
        request.addHeader('Accept', 'application/json;q=2')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['json'])

        then:
        response.status == 409
        response.contentAsString == ''
    }
}

/**
 * With {@code grails.mime.negotiation.spring} and a {@code grails.mime.types} that does not list the {@code all} format,
 * a client that accepts any type is still answered in the action's first format, whatever read the response format first.
 */
class SpringFormatNegotiationWithoutAllRespondSpec extends Specification implements ControllerUnitTest<NegotiatedResponseController> {

    Closure doWithConfig() {
        { config ->
            config[Settings.WEB_RENDERING_JSON_SPRING] = true
            config[Settings.MIME_NEGOTIATION_SPRING] = true
            config[Settings.MIME_TYPES] = [json: 'application/json', xml: 'application/xml', html: 'text/html']
        }
    }

    void 'a client that accepts any type is answered in the first format of the action after the response format is read, #how'() {
        given:
        if (accept) {
            request.addHeader('Accept', accept)
        }

        when:
        String format = response.format
        controller.respond([title: 'Grails'], formats: ['json', 'xml'])

        then:
        format == 'all'
        response.status == 200
        response.contentType.startsWith('application/json')

        where:
        how               | accept
        'with Accept */*' | '*/*'
        'without Accept'  | null
    }
}

/**
 * With {@code grails.mime.negotiation.spring} and Spring MVC's parameter negotiation of the {@code format} parameter,
 * which {@code spring.mvc.contentnegotiation.favor-parameter} enables, {@code respond} answers the format the parameter
 * names, and the {@code all} format with the {@code Accept} header. A format only Spring MVC's media types map is not one
 * {@code respond} renders.
 */
class SpringFormatNegotiationParameterRespondSpec extends Specification implements ControllerUnitTest<NegotiatedResponseController> {

    Closure doWithConfig() {
        { config ->
            config[Settings.WEB_RENDERING_JSON_SPRING] = true
            config[Settings.MIME_NEGOTIATION_SPRING] = true
            config['spring.mvc.contentnegotiation.favor-parameter'] = true
            config['spring.mvc.contentnegotiation.media-types.plain'] = 'text/plain'
        }
    }

    void 'a format parameter of #format answers with #status #contentType'() {
        given:
        request.addHeader('Accept', 'application/json')
        request.addParameter('format', format)

        when:
        controller.respond([title: 'Grails'], formats: ['json', 'xml'])

        then:
        response.status == status
        (response.contentType ?: '').startsWith(contentType)

        where:
        format    || status | contentType
        'all'     || 200    | 'application/json'
        'xml'     || 200    | 'text/xml'
        'unknown' || 406    | ''
        'plain'   || 406    | ''
    }
}

/**
 * Without {@code grails.mime.negotiation.spring}, the Grails 9 rules resolve the format {@code respond} answers in.
 */
class GrailsFormatNegotiationRespondSpec extends Specification implements ControllerUnitTest<NegotiatedResponseController> {

    Closure doWithConfig() {
        { config -> config[Settings.WEB_RENDERING_JSON_SPRING] = true }
    }

    void 'the request of the action a web unit test calls is not marked as handled by a URL mapping, as in Grails 9'() {
        when:
        controller

        then:
        request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) == null
    }

    void "Spring MVC's manager is left as it is"() {
        expect:
        applicationContext.getBean('mvcContentNegotiationManager', ContentNegotiationManager).getClass() == ContentNegotiationManager
    }

    void 'a client that accepts #accept is answered in #format, as in Grails 9'() {
        given:
        request.addHeader('Accept', accept)

        when:
        controller.respond([title: 'Grails'], formats: ['xml', 'json'])

        then:
        response.status == 200
        response.contentType.contains(format)

        where:
        accept                     || format
        '*/*, application/json'    || 'xml'
        'application/*+json'       || 'xml'
        'application/merge-patch+json' || 'xml'
        'image/png'                || 'xml'
    }

    void 'a body of a type with the +json suffix is of no format, and not bound'() {
        given:
        request.method = 'POST'
        request.contentType = 'application/merge-patch+json'
        request.content = '{"title":"Grails"}'.bytes

        when:
        def model = controller.save()

        then:
        request.format == null
        model.title == null
    }

    void 'a client that accepts any type of a version is answered 406, as in Grails 9'() {
        given:
        request.addHeader('Accept', '*/*;v=2')

        when:
        controller.respond([title: 'Grails'], formats: ['json'])

        then:
        response.status == 406
    }

    void 'a request that names a format no type is configured for is answered in the first configured type'() {
        given:
        request.addHeader('Accept', 'application/json')
        params.format = 'unknown'

        when:
        controller.respond([title: 'Grails'], formats: ['xml', 'json'])

        then: 'the first configured type is the all format, which the first format of the action answers'
        response.status == 200
        response.contentType.contains('xml')
    }
}

@Artefact('Controller')
class NegotiatedResponseController {

    def save(NegotiatedCommand command) {
        [title: command.title]
    }
}

class NegotiatedCommand implements Validateable {

    String title
}
