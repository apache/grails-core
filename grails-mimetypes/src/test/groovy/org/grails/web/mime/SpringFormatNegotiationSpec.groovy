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

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.accept.HeaderContentNegotiationStrategy
import org.springframework.web.accept.ParameterContentNegotiationStrategy
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.mapping.UrlMappingInfo
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * With {@code grails.mime.negotiation.spring}, Grails formats are resolved with Spring MVC's content negotiation manager,
 * as the formats {@code grails.mime.types} gives the media types it resolves, and the manager applies the
 * {@code grails.mime} rules Grails keeps to the requests a URL mapping handles.
 */
class SpringFormatNegotiationSpec extends Specification {

    @AutoCleanup
    AnnotationConfigWebApplicationContext context

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'the strategy that resolves the formats of a request is looked up once for it, #how'() {
        given:
        if (configured) {
            start((Settings.MIME_NEGOTIATION_SPRING): true)
        }
        def first = configured ? request('Accept': 'application/json') : GrailsWebMockUtil.bindMockWebRequest().currentRequest
        def strategy = GrailsContentNegotiationStrategy.forRequest(first)

        when: 'the format of the request is read, and the strategy asked for again'
        String format = GrailsWebRequest.lookup().currentResponse.format

        then:
        format == (configured ? 'json' : 'all')
        GrailsContentNegotiationStrategy.forRequest(first).is(strategy)

        when: 'another request is negotiated'
        RequestContextHolder.resetRequestAttributes()
        def second = configured ? request('Accept': 'application/json') : GrailsWebMockUtil.bindMockWebRequest().currentRequest

        then: "it gets the application's strategy, or, without one, one of its own"
        GrailsContentNegotiationStrategy.forRequest(second).is(strategy) == configured

        where:
        how                                                    | configured
        "of the application's MimeTypesConfiguration"          | true
        'built for the request in a context without one'       | false
    }

    void "without the setting, Spring MVC's manager is left as it is"() {
        when:
        start([:])

        then:
        manager().getClass() == ContentNegotiationManager
    }

    void "with the setting, Spring MVC's manager resolves a Grails format ahead of its own strategies, and keeps its formats"() {
        when:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def manager = manager()

        then: 'the format strategy goes first, and the Accept header is still read by a header strategy'
        manager instanceof GrailsContentNegotiationManager
        manager.strategies.first() instanceof GrailsFormatContentNegotiationStrategy
        manager.getStrategy(HeaderContentNegotiationStrategy) instanceof GrailsAcceptHeaderContentNegotiationStrategy

        and: 'the formats Grails registers with Spring MVC are those of its manager'
        manager.mediaTypeMappings['hal'] == MediaType.parseMediaType('application/hal+json')
        manager.resolveFileExtensions(MediaType.APPLICATION_JSON) == ['json']
        manager.mediaTypeMappings == ((GrailsContentNegotiationManager) manager).springManager.mediaTypeMappings

        and: 'it is the one content negotiation strategy bean, which Spring Security adopts'
        context.getBeansOfType(ContentNegotiationStrategy).keySet() == [GrailsContentNegotiationStrategy.MVC_CONTENT_NEGOTIATION_MANAGER] as Set
    }

    void 'the format a request names decides what Spring MVC writes for it only while a URL mapping handles it: #handled'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def request = request('Accept': 'application/json')
        handledBy(request, handler)
        GrailsWebRequest.lookup().params.format = 'xml'

        expect:
        manager().resolveMediaTypes(new ServletWebRequest(request)) == resolved

        where:
        handled                                      | handler      || resolved
        'a URL mapping'                              | urlMapping() || ['text/xml', 'application/xml', 'application/*+xml'].collect { MediaType.parseMediaType(it) }
        'a Spring MVC endpoint'                      | endpoint()   || [MediaType.APPLICATION_JSON]
        'a filter, before any handler'               | null         || [MediaType.APPLICATION_JSON]
        'a filter, after a controller dispatch ended' | 'controller' || [MediaType.APPLICATION_JSON]
    }

    void 'a request a URL mapping handles that names a format no type is configured for is not acceptable to Spring MVC'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def request = request('Accept': 'application/json')
        handledBy(request, urlMapping())
        GrailsWebRequest.lookup().params.format = 'unknown'

        when:
        manager().resolveMediaTypes(new ServletWebRequest(request))

        then:
        HttpMediaTypeNotAcceptableException e = thrown()
        e.supportedMediaTypes.contains(MediaType.APPLICATION_JSON)

        and: 'Grails resolves no format for it'
        GrailsWebRequest.lookup().currentResponse.format == null
        GrailsWebRequest.lookup().currentResponse.mimeType == null
    }

    void 'the all format names no type, so the Accept header decides, for Spring MVC and for Grails'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def request = request('Accept': 'application/json')
        handledBy(request, urlMapping())
        GrailsWebRequest.lookup().params.format = 'all'

        expect:
        manager().resolveMediaTypes(new ServletWebRequest(request)) == [MediaType.APPLICATION_JSON]
        GrailsWebRequest.lookup().currentResponse.format == 'json'
    }

    void 'the first value of the format parameter is the format, as Spring MVC reads its parameter'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def request = request([:])
        request.addParameter('format', 'xml', 'json')
        handledBy(request, urlMapping())

        expect:
        GrailsWebRequest.lookup().currentResponse.format == 'xml'
        manager().resolveMediaTypes(new ServletWebRequest(request)).first() == MediaType.TEXT_XML
    }

    void 'the Accept header of a user agent the settings ignore it for is ignored by Spring MVC for a #handled request only'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, (Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS): ['Gecko'])
        def request = request('Accept': 'application/json', 'User-Agent': 'Mozilla/5.0 Gecko/20100101')
        handledBy(request, handler)

        expect:
        manager().resolveMediaTypes(new ServletWebRequest(request)) == resolved
        GrailsWebRequest.lookup().currentResponse.format == 'all'

        where:
        handled               | handler      || resolved
        'URL mapping'         | urlMapping() || ContentNegotiationStrategy.MEDIA_TYPE_ALL_LIST
        'Spring MVC endpoint' | endpoint()   || [MediaType.APPLICATION_JSON]
    }

    void 'an XHR request keeps its Accept header unless the settings ignore it for XHR requests too'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, (Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS): ['Gecko'],
                (Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS_XHR): xhrToo)
        def request = request('Accept': 'application/json', 'User-Agent': 'Gecko', 'X-Requested-With': 'XMLHttpRequest')
        handledBy(request, urlMapping())

        expect:
        manager().resolveMediaTypes(new ServletWebRequest(request)) == resolved
        GrailsWebRequest.lookup().currentResponse.format == format

        where:
        xhrToo || resolved                                       | format
        false  || [MediaType.APPLICATION_JSON]                   | 'json'
        true   || ContentNegotiationStrategy.MEDIA_TYPE_ALL_LIST | 'all'
    }

    void 'grails.mime.use.accept.header: false ignores the Accept header of a request a URL mapping handles'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, (Settings.MIME_USE_ACCEPT_HEADER): false)
        def request = request('Accept': 'application/json')
        handledBy(request, urlMapping())

        expect:
        manager().resolveMediaTypes(new ServletWebRequest(request)) == ContentNegotiationStrategy.MEDIA_TYPE_ALL_LIST
        GrailsWebRequest.lookup().currentResponse.format == 'all'
    }

    void 'the Accept header #accept resolves to the formats #formats, in the order Spring MVC resolves its types in'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        request('Accept': accept)

        expect:
        GrailsWebRequest.lookup().currentResponse.mimeTypes*.extension == formats
        GrailsWebRequest.lookup().currentResponse.format == formats[0]

        where:
        accept                                                    || formats
        '*/*'                                                     || ['all']
        '*/*, application/json'                                   || ['json', 'all']
        'application/json;q=0.5, application/xml'                 || ['xml', 'json']
        'text/html;q=0.9, application/json;q=0.9'                 || ['html', 'json']
        'text/*'                                                  || ['all']
        'application/*+json'                                      || ['all']
        'image/*'                                                 || []
        'application/problem+json'                                || ['json']
        'application/vnd.api+json, application/xml'               || ['json', 'xml']
        'application/soap+xml'                                    || ['xml']
        'application/xml, application/atom+xml'                   || ['atom', 'xml']
        'text/xml;q=0.5, application/xml;q=0.9, application/json' || ['json', 'xml', 'xml']
        'image/png'                                               || []
        'application/json;q=abc'                                  || []
    }

    void 'a configured type of the Accept header keeps its parameters, the quality among them'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        request('Accept': 'application/json;q=0.8;profile=compact')

        expect:
        with(GrailsWebRequest.lookup().currentResponse.mimeTypes.first()) {
            name == 'application/json'
            extension == 'json'
            parameters == [q: '0.8', profile: 'compact']
        }
    }

    void 'a client that accepts any type is of the all format, also where it is not configured'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, 'grails.mime.types': [json: 'application/json', html: 'text/html'])
        request('Accept': '*/*')

        expect:
        with(GrailsWebRequest.lookup().currentResponse.mimeTypes.toList()) {
            it*.name == ['*/*']
            it*.extension == ['all']
        }
        GrailsWebRequest.lookup().currentResponse.withFormat {
            html { 'html' }
            json { 'json' }
        } == 'html'
    }

    void 'a client that accepts any type answers respond with any format after its format is read, #how, also where the all format is not configured'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, 'grails.mime.types': [json: 'application/json', html: 'text/html'])
        def request = request(accept ? ['Accept': accept] : [:])
        def response = GrailsWebRequest.lookup().currentResponse

        when: 'the format is read, which saves it for the response, and then the types respond answers in'
        String format = response.format
        def formatAware = response.mimeTypesFormatAware.toList()

        then:
        format == 'all'
        formatAware*.extension == ['all']
        formatAware*.name == ['*/*']
        response.mimeType.name == '*/*'

        where:
        how                   | accept
        'with Accept: */*'    | '*/*'
        'without Accept'      | null
    }

    void 'a format parameter of all, where the all format is not configured, leaves the format to the Accept header'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, 'grails.mime.types': [json: 'application/json', html: 'text/html'])
        def request = request('Accept': 'application/json')
        handledBy(request, urlMapping())
        GrailsWebRequest.lookup().params.format = 'all'

        expect:
        GrailsWebRequest.lookup().currentResponse.format == 'json'
        manager().resolveMediaTypes(new ServletWebRequest(request)) == [MediaType.APPLICATION_JSON]
    }

    void 'a type with the +xml suffix comes ahead of the XML type of equal quality, for Grails and for what Spring MVC writes: #accept'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def request = request('Accept': accept)
        handledBy(request, urlMapping())

        expect:
        GrailsWebRequest.lookup().currentResponse.mimeTypes*.extension == formats
        manager().resolveMediaTypes(new ServletWebRequest(request))*.toString() == resolved

        where:
        accept                                                    || formats                    | resolved
        'application/xml, application/atom+xml'                   || ['atom', 'xml']            | ['application/atom+xml', 'application/xml']
        'text/xml, application/json, application/atom+xml'        || ['atom', 'xml', 'json']    | ['application/atom+xml', 'text/xml', 'application/json']
        'application/xml, application/rss+xml, application/atom+xml' || ['atom', 'rss', 'xml']  | ['application/atom+xml', 'application/rss+xml', 'application/xml']
        'application/xml, application/atom+xml;q=0.5'             || ['xml', 'atom']            | ['application/xml', 'application/atom+xml;q=0.5']
        'application/atom+xml, application/xml'                   || ['atom', 'xml']            | ['application/atom+xml', 'application/xml']
        'application/json, application/atom+xml'                  || ['json', 'atom']           | ['application/json', 'application/atom+xml']
        'application/problem+xml, application/atom+xml'           || ['atom', 'xml']            | ['application/atom+xml', 'application/problem+xml']
        'application/problem+xml;q=0.5, application/atom+xml;q=0.4' || ['xml', 'atom']          | ['application/problem+xml;q=0.5', 'application/atom+xml;q=0.4']
        'application/atom+xml, application/xml, application/atom+xml' || ['atom', 'xml']        | ['application/atom+xml', 'application/atom+xml', 'application/xml']
        'application/xml, application/atom+xml, application/atom+xml' || ['atom', 'xml']        | ['application/atom+xml', 'application/atom+xml', 'application/xml']
    }

    void 'a problem type grails.mime.types names keeps the order of the header, as a format of its own, for Grails and for what Spring MVC writes'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, 'grails.mime.types': [all: '*/*', xml: 'application/xml',
                atom: 'application/atom+xml', problem: 'application/problem+xml'])
        def request = request('Accept': 'application/problem+xml, application/atom+xml')
        handledBy(request, urlMapping())

        expect:
        GrailsWebRequest.lookup().currentResponse.mimeTypes*.extension == ['problem', 'atom']
        GrailsWebRequest.lookup().currentResponse.format == 'problem'
        manager().resolveMediaTypes(new ServletWebRequest(request))*.toString() == ['application/problem+xml', 'application/atom+xml']
    }

    void "a problem type only Spring MVC's media types map is one of the XML types, which a +xml type comes ahead of and which is not moved itself: #accept"() {
        given: 'problem mapped to application/problem+xml in Spring MVC only, as spring.mvc.contentnegotiation.media-types.problem maps it'
        start((Settings.MIME_NEGOTIATION_SPRING): true, (ParameterConfiguration.PROBLEM): true)
        def request = request('Accept': accept)
        handledBy(request, urlMapping())

        expect: 'the format and the type Spring MVC writes agree, as the Grails rules read the header'
        GrailsWebRequest.lookup().currentResponse.format == format
        manager().resolveMediaTypes(new ServletWebRequest(request))*.toString() == resolved

        where:
        accept                                                             || format | resolved
        'application/problem+xml, application/atom+xml'                    || 'atom' | ['application/atom+xml', 'application/problem+xml']
        'application/xml, application/atom+xml, application/problem+xml'   || 'atom' | ['application/atom+xml', 'application/xml', 'application/problem+xml']
        'text/xml, application/atom+xml, application/problem+xml'          || 'atom' | ['application/atom+xml', 'text/xml', 'application/problem+xml']
        'application/xml, application/problem+xml'                         || 'xml'  | ['application/xml', 'application/problem+xml']
        'application/problem+xml, application/xml, application/atom+xml'   || 'atom' | ['application/atom+xml', 'application/problem+xml', 'application/xml']
        'application/atom+xml, application/problem+xml, application/atom+xml' || 'atom' | ['application/atom+xml', 'application/atom+xml', 'application/problem+xml']
        'application/problem+xml, application/atom+xml, application/atom+xml' || 'atom' | ['application/atom+xml', 'application/atom+xml', 'application/problem+xml']
    }

    void 'a wildcard is of the all format, which withFormat answers with the first block compatible with it: #result for #accept'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        request('Accept': accept)

        expect:
        GrailsWebRequest.lookup().currentResponse.mimeTypes*.name == [accept]
        GrailsWebRequest.lookup().currentResponse.format == 'all'
        GrailsWebRequest.lookup().currentResponse.withFormat {
            pdf { 'pdf' }
            json { 'json' }
            html { 'html' }
        } == result

        where:
        accept              || result
        'text/*'            || 'json'
        'application/*+xml' || 'html'
        'application/*'     || 'pdf'
    }

    void 'a wildcard no block is compatible with leaves withFormat to the next type the request accepts'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        request('Accept': 'text/*, application/pdf;q=0.5')

        expect:
        GrailsWebRequest.lookup().currentResponse.format == 'all'
        GrailsWebRequest.lookup().currentResponse.withFormat {
            pdf { 'pdf' }
        } == 'pdf'
    }

    void 'withFormat runs the block of the first format the request accepts that has one: #result for #accept'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        request('Accept': accept)

        expect:
        GrailsWebRequest.lookup().currentResponse.withFormat {
            json { 'json' }
            text { 'text' }
            html { 'html' }
        } == result

        where:
        accept                            || result
        'application/xml, */*, text/html' || 'html'
        'application/xml, text/csv, */*'  || 'json'
        'application/xml, text/csv'       || null
        'image/png'                       || null
    }

    void "withFormat runs its * block for a request that accepts no configured type"() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        request('Accept': 'image/png')

        expect:
        GrailsWebRequest.lookup().currentResponse.withFormat {
            json { 'json' }
            '*' { 'any' }
        } == 'any'
    }

    void "Spring MVC's parameter negotiation, which the application enables, resolves Grails formats too"() {
        given: 'parameter negotiation with a parameter of its own name, as spring.mvc.contentnegotiation.* enables it'
        start((Settings.MIME_NEGOTIATION_SPRING): true, (ParameterConfiguration.ENABLED): true)
        def request = request('Accept': 'application/json')
        request.addParameter('mediaType', 'xml')

        expect:
        manager().getStrategy(ParameterContentNegotiationStrategy) != null
        GrailsWebRequest.lookup().currentResponse.format == 'xml'
    }

    void "a format parameter of all, which Spring MVC's parameter negotiation of the format parameter does not know, leaves the format to the Accept header"() {
        given: 'parameter negotiation on the format parameter, as spring.mvc.contentnegotiation.favor-parameter enables it'
        start((Settings.MIME_NEGOTIATION_SPRING): true, (ParameterConfiguration.ENABLED): true, (ParameterConfiguration.NAME): 'format')
        def request = request('Accept': 'application/json')
        request.addParameter('format', 'all')
        handledBy(request, urlMapping())

        expect:
        GrailsWebRequest.lookup().currentResponse.format == 'json'
        GrailsWebRequest.lookup().currentResponse.mimeTypesFormatAware*.extension == ['json']
        GrailsWebRequest.lookup().currentResponse.mimeTypes*.extension == ['json']
        manager().resolveMediaTypes(new ServletWebRequest(request)) == [MediaType.APPLICATION_JSON]
    }

    void "Spring MVC's parameter negotiation of the format parameter still answers #format for an endpoint, as it does without the setting"() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, (ParameterConfiguration.ENABLED): true, (ParameterConfiguration.NAME): 'format')
        def request = request('Accept': 'application/json')
        request.addParameter('format', format)
        handledBy(request, endpoint())

        when:
        def resolved = resolve(request)

        then:
        resolved == expected

        where:
        format || expected
        'xml'  || [MediaType.APPLICATION_XML]
        'all'  || 'not acceptable'
    }

    void "a format only Spring MVC's media types map is written in the type they map it to, #how"() {
        given: 'plain mapped to text/plain in Spring MVC only, as spring.mvc.contentnegotiation.media-types.plain maps it'
        start((Settings.MIME_NEGOTIATION_SPRING): true, (ParameterConfiguration.PLAIN): true,
                (ParameterConfiguration.ENABLED): favorParameter, (ParameterConfiguration.NAME): 'format')
        def request = request('Accept': 'application/json')
        request.addParameter('format', format)
        handledBy(request, urlMapping())

        expect:
        resolve(request) == expected

        where:
        how                                                    | favorParameter | format    || expected
        'without parameter negotiation'                        | false          | 'plain'   || [MediaType.TEXT_PLAIN]
        'with parameter negotiation of the format parameter'   | true           | 'plain'   || [MediaType.TEXT_PLAIN]
        'whatever the case of the format'                      | false          | 'PLAIN'   || [MediaType.TEXT_PLAIN]
        'and a format neither maps is not acceptable'          | false          | 'unknown' || 'not acceptable'
        'and with parameter negotiation, still not acceptable' | true           | 'unknown' || 'not acceptable'
    }

    void "a format grails.mime.types does not list is written in the type Spring MVC maps it to by default, as #format"() {
        given: 'grails.mime.types without json, which Spring MVC maps by default for its JSON message converter'
        start((Settings.MIME_NEGOTIATION_SPRING): true, 'grails.mime.types': [all: '*/*', xml: 'application/xml'])
        def request = request('Accept': 'application/xml')
        request.addParameter('format', format)
        handledBy(request, urlMapping())

        expect:
        resolve(request) == [MediaType.APPLICATION_JSON]

        where:
        format << ['json', 'JSON']
    }

    void "a data format Grails registers with Spring MVC is written in its type whatever its case, while Grails resolves no format for #format"() {
        given: 'the default grails.mime.types, whose data formats Grails registers with Spring MVC as format aliases'
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def request = request('Accept': 'application/xml')
        request.addParameter('format', format)
        handledBy(request, urlMapping())

        expect:
        resolve(request) == [MediaType.parseMediaType(expected)]
        GrailsWebRequest.lookup().currentResponse.format == null

        where:
        format || expected
        'HAL'  || 'application/hal+json'
        'CSV'  || 'text/csv'
        'Text' || 'text/plain'
    }

    void "a format Grails configures keeps the types grails.mime.types gives it, whatever type Spring MVC's media types map it to"() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true, 'grails.mime.types': [all: '*/*', json: 'application/json', plain: 'text/csv'],
                (ParameterConfiguration.PLAIN): true)
        def request = request('Accept': 'application/json')
        request.addParameter('format', 'plain')
        handledBy(request, urlMapping())

        expect:
        resolve(request) == [MediaType.parseMediaType('text/csv')]
    }

    void 'the Content-Type #contentType is the request format #format'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def request = request([:])
        if (contentType) {
            request.contentType = contentType
        }

        expect:
        request.format == format

        where:
        contentType                       || format
        null                              || 'all'
        'application/json;charset=UTF-8'  || 'json'
        'APPLICATION/JSON'                || 'json'
        'text/xml'                        || 'xml'
        'application/hal+json'            || 'hal'
        'application/vnd.acme+json'       || 'json'
        'application/problem+xml'         || 'xml'
        'application/x-www-form-urlencoded' || 'form'
        'image/png'                       || null
        'application/json;charset='       || null
    }

    void 'a Content-Type keeps its parameters, and one no type is configured for is the type itself, in lower case'() {
        given:
        start((Settings.MIME_NEGOTIATION_SPRING): true)
        def request = request([:])

        when:
        request.contentType = 'application/json;charset=UTF-8'

        then:
        request.mimeTypes*.name == ['application/json']
        request.mimeTypes[0].parameters.charset == 'UTF-8'

        when:
        request.removeAttribute(GrailsApplicationAttributes.REQUEST_FORMATS)
        request.contentType = 'Image/PNG'

        then:
        request.mimeTypes*.name == ['image/png']
    }

    void "the setting is read from the application's configuration, for Spring MVC's manager as for Grails formats, where the environment says #environment"() {
        given: 'a configuration of the application that is not the environment, as one a test builds by hand'
        context = new AnnotationConfigWebApplicationContext()
        context.servletContext = new MockServletContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', [
                (Settings.MIME_NEGOTIATION_SPRING)        : environment,
                (SeparateConfigConfiguration.CONFIGURED): configured
        ]))
        context.register(SeparateConfigConfiguration)
        context.refresh()
        request('Accept': '*/*, application/json')

        expect:
        (manager() instanceof GrailsContentNegotiationManager) == configured
        GrailsWebRequest.lookup().currentResponse.format == format

        where:
        environment | configured || format
        false       | true       || 'json'
        true        | false      || 'all'
    }

    void 'without the setting, the Grails 9 rules resolve the formats'() {
        given:
        start([:])
        request('Accept': '*/*, application/json')

        expect:
        GrailsWebRequest.lookup().currentResponse.format == 'all'
    }

    private void start(Map<String, Object> properties) {
        context = new AnnotationConfigWebApplicationContext()
        context.servletContext = new MockServletContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        context.register(NegotiationConfiguration)
        context.refresh()
    }

    private ContentNegotiationManager manager() {
        context.getBean(GrailsContentNegotiationStrategy.MVC_CONTENT_NEGOTIATION_MANAGER, ContentNegotiationManager)
    }

    private Object resolve(MockHttpServletRequest request) {
        try {
            manager().resolveMediaTypes(new ServletWebRequest(request))
        }
        catch (HttpMediaTypeNotAcceptableException ignored) {
            'not acceptable'
        }
    }

    private MockHttpServletRequest request(Map<String, String> headers) {
        def request = new MockHttpServletRequest(context.servletContext)
        headers.each { String name, String value -> request.addHeader(name, value) }
        GrailsWebMockUtil.bindMockWebRequest(context, request, new MockHttpServletResponse())
        request
    }

    private static void handledBy(MockHttpServletRequest request, Object handler) {
        if (handler == 'controller') {
            request.setAttribute(GrailsApplicationAttributes.CONTROLLER, new Object())
        }
        else if (handler != null) {
            request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handler)
        }
    }

    private UrlMappingInfo urlMapping() {
        Stub(UrlMappingInfo)
    }

    private static HandlerMethod endpoint() {
        new HandlerMethod(new Object(), Object.getMethod('toString'))
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import([MimeTypesConfiguration, ParameterConfiguration])
    static class NegotiationConfiguration {

        @Bean
        GrailsApplication grailsApplication(ApplicationContext applicationContext, ConfigurableEnvironment environment) {
            def application = new DefaultGrailsApplication()
            application.config = new PropertySourcesConfig(environment.propertySources)
            application.applicationContext = applicationContext
            application.mainContext = applicationContext
            application
        }
    }

    /** An application whose configuration has the setting the test gives it apart from the environment. */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(MimeTypesConfiguration)
    static class SeparateConfigConfiguration {

        static final String CONFIGURED = 'test.configured.negotiation'

        @Bean
        GrailsApplication grailsApplication(ApplicationContext applicationContext, ConfigurableEnvironment environment) {
            def propertySources = new MutablePropertySources()
            propertySources.addFirst(new MapPropertySource('configured',
                    [(Settings.MIME_NEGOTIATION_SPRING): environment.getProperty(CONFIGURED, Boolean)]))
            def application = new DefaultGrailsApplication()
            application.config = new PropertySourcesConfig(propertySources)
            application.applicationContext = applicationContext
            application.mainContext = applicationContext
            application
        }
    }

    /** Enables Spring MVC's parameter negotiation, as {@code spring.mvc.contentnegotiation.*} does, where the test says. */
    @Configuration(proxyBeanMethods = false)
    static class ParameterConfiguration implements WebMvcConfigurer {

        static final String ENABLED = 'test.parameter.negotiation'

        static final String NAME = 'test.parameter.negotiation.name'

        /** Maps the {@code plain} format to {@code text/plain}, as {@code spring.mvc.contentnegotiation.media-types.plain} does. */
        static final String PLAIN = 'test.parameter.negotiation.plain'

        /**
         * Maps the {@code problem} format to {@code application/problem+xml}, as
         * {@code spring.mvc.contentnegotiation.media-types.problem} does.
         */
        static final String PROBLEM = 'test.parameter.negotiation.problem'

        private final boolean enabled

        private final String name

        private final boolean plain

        private final boolean problem

        ParameterConfiguration(ConfigurableEnvironment environment) {
            enabled = environment.getProperty(ENABLED, Boolean, false)
            name = environment.getProperty(NAME, 'mediaType')
            plain = environment.getProperty(PLAIN, Boolean, false)
            problem = environment.getProperty(PROBLEM, Boolean, false)
        }

        @Override
        void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
            if (enabled) {
                configurer.favorParameter(true).parameterName(name)
            }
            if (plain) {
                configurer.mediaType('plain', MediaType.TEXT_PLAIN)
            }
            if (problem) {
                configurer.mediaType('problem', MediaType.APPLICATION_PROBLEM_XML)
            }
        }
    }
}
