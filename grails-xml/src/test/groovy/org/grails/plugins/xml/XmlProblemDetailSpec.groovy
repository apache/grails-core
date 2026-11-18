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

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonSerialize

import groovy.json.JsonSlurper
import groovy.namespace.QName
import groovy.xml.XmlParser
import groovy.xml.XmlSlurper

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.converter.xml.JacksonXmlHttpMessageConverter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.servlet.ModelAndView

import grails.artefact.Artefact
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.mime.MimeType
import org.grails.gsp.io.GroovyPageScriptSource
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.xml.DefaultXmlRenderer
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification

class XmlProblemDetailSpec extends Specification implements ControllerUnitTest<ProblemXmlController> {

    Closure doWithConfig() {{ config ->
        // a user agent whose Accept header Grails ignores, which no other request sends
        config.grails.mime.disable.accept.header.userAgents = ['LegacyBrowser']
    }}

    void 'a ProblemDetail responded to a client that asks for it in XML is application/problem+xml, with the status it carries'() {
        given:
        request.addHeader('Accept', 'application/problem+xml')

        when:
        controller.respond(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, 'The title is taken'))

        then: 'Spring MVC writes it, whatever grails.web.rendering.xml.spring is set to'
        response.status == 409
        response.contentType.startsWith('application/problem+xml')

        and: 'as the problem element of RFC 9457'
        def problem = new XmlSlurper().parseText(response.contentAsString)
        tree(response.contentAsString)[0..1] == ['problem', 'urn:ietf:rfc:7807']
        problem.status.text() == '409'
        problem.detail.text() == 'The title is taken'
    }

    void 'a client that asks for a problem in XML from an action that does not respond in XML is answered with application/problem+json, #formats'() {
        given:
        request.addHeader('Accept', 'application/problem+xml')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: formats)

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+json')
        response.json.status == 409

        where:
        formats << [['json'], ['json', 'html'], ['html']]
    }

    void "a browser, whose Accept header lists XML, is answered with application/problem+json, as Spring MVC answers it with Spring Boot's converters"() {
        given:
        request.addHeader('Accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8')
        request.addHeader('User-Agent', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json', 'html'])

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+json')
        response.json.status == 409
    }

    void 'a browser is answered with application/problem+json where respond names a view for the problem that does not exist'() {
        given:
        request.addHeader('Accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8')
        request.addHeader('User-Agent', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), view: 'conflict', formats: ['xml', 'json', 'html'])

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+json')
        response.json.status == 409
    }

    void 'a client asking for a problem in XML is answered with application/problem+xml where respond names a view for the problem that does not exist'() {
        given:
        request.addHeader('Accept', 'text/html, application/problem+xml;q=0.9')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), view: 'conflict', formats: ['html', 'xml'])

        then: 'as without a view'
        response.status == 409
        response.contentType.startsWith('application/problem+xml')
    }

    void "a client that asks for #accept is answered with #type, as Spring MVC answers it with Spring Boot's converters"() {
        given:
        request.addHeader('Accept', accept)

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType.startsWith(type)

        where:
        accept                                                    || type
        'application/xml'                                         || 'application/problem+json'
        'text/xml'                                                || 'application/problem+json'
        'application/xml, application/json'                       || 'application/problem+json'
        '*/*'                                                     || 'application/problem+json'
        'application/problem+xml;q=0.5, application/problem+json' || 'application/problem+json'
        'application/problem+xml'                                 || 'application/problem+xml'
        'application/*+xml'                                       || 'application/problem+xml'
        'application/json, application/problem+xml'               || 'application/problem+xml'
        'application/problem+json;q=0.5, application/problem+xml' || 'application/problem+xml'
    }

    void 'a client whose Accept header Spring MVC cannot read, #description, is answered with the status alone, as Spring MVC answers it'() {
        given:
        request.addHeader('Accept', accept)
        request.addHeader('User-Agent', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), arguments + [formats: ['html', 'xml', 'json']])

        then: 'without a body or a content type, and without the error page'
        response.status == status
        response.contentType == null
        response.contentAsString == ''
        response.errorMessage == null
        !response.committed
        view == null

        where:
        description                                    | accept                                                                                       | arguments          || status
        'with more types than Spring sorts'             | (1..51).collect { "application/x-type-${it}" }.join(', ') + ', application/problem+xml' | [:]                || 409
        'with a quality value above 1'                  | 'application/problem+xml;q=2'                                                                | [:]                || 409
        'for a status respond is given'                 | 'application/json;q=2'                                                                       | [status: 400]      || 400
        'for a view respond names that does not exist' | 'text/html;q=2, application/json'                                                            | [view: 'conflict'] || 409
        'for a view respond names in a format no renderer writes' | 'text/csv;q=2'                                                                | [view: 'conflict'] || 409
    }

    void 'a problem respond names a view for that the format Grails reads has not is answered with the status alone, where Spring MVC cannot read the Accept header'() {
        given: 'a header Grails reads as JSON, as Grails 8 read it, and no JSON view of the name'
        request.addHeader('Accept', 'application/json;q=2')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), view: 'conflict', formats: ['html', 'xml', 'json'])

        then: 'as Spring MVC answers it without a view'
        response.status == 409
        response.contentType == null
        response.contentAsString == ''
    }

    void 'the page respond names for a problem renders it for a client whose Accept header Spring MVC cannot read'() {
        given: 'the HTML page of the name, and a header Grails reads as Grails 8 read it'
        views['/problemXml/conflict.gsp'] = '<p>${problemDetail.detail}</p>'
        request.addHeader('Accept', 'text/html;q=2')
        request.addHeader('User-Agent', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)')

        when:
        controller.respond(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, 'taken'), view: 'conflict',
                formats: ['html', 'xml', 'json'])

        then:
        response.status == 409
        view == 'conflict'
        model.problemDetail.detail == 'taken'
    }

    void 'a problem whose status is not an error status fails as Spring MVC fails it for a client whose Accept header Spring MVC cannot read'() {
        given:
        request.addHeader('Accept', 'application/json;q=2')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), status: 200, formats: ['xml', 'json'])

        then: "which Spring MVC's exception resolver answers with 406"
        thrown(HttpMediaTypeNotAcceptableException)
        response.contentAsString == ''
    }

    void 'a request that names the XML format is answered with application/problem+json, as Spring MVC does not consult the format'() {
        given:
        response.format = 'xml'

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT))

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+json')
    }

    void 'a browser whose Accept header is ignored, for an action that lists XML first, is answered with application/problem+json'() {
        given:
        request.addHeader('Accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8')
        request.addHeader('User-Agent', 'LegacyBrowser/1.0')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+json')
        response.mimeTypesFormatAware == [MimeType.ALL] as MimeType[]
    }

    void 'an XML renderer the application registers for problems renders them for a client that asks for XML'() {
        given:
        def page = Stub(Renderer) {
            getTargetType() >> ProblemDetail
            getMimeTypes() >> ([MimeType.XML, MimeType.TEXT_XML] as MimeType[])
            render(_, _) >> { Object problem, RenderContext context -> context.writer.write('<conflict/>') }
        }
        def registry = new DefaultRendererRegistry()
        registry.initialize()
        registry.addRenderer(page)
        controller.rendererRegistry = registry
        request.addHeader('Accept', 'application/xml')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentAsString == '<conflict/>'
    }

    void "a problem a browser is answered with by an XML renderer the application registers for problems is written so where respond names a view no page renders, #arguments"() {
        given: 'a browser, which accepts XML after HTML'
        def page = Stub(Renderer) {
            getTargetType() >> ProblemDetail
            getMimeTypes() >> ([MimeType.XML, MimeType.TEXT_XML] as MimeType[])
            render(_, _) >> { Object problem, RenderContext context -> context.writer.write('<conflict/>') }
        }
        def registry = new DefaultRendererRegistry()
        registry.initialize()
        registry.addRenderer(page)
        controller.rendererRegistry = registry
        request.addHeader('Accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8')
        request.addHeader('User-Agent', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), arguments + [formats: ['html', 'xml', 'json']])

        then: 'as without a view'
        response.status == 409
        response.contentAsString == '<conflict/>'

        where:
        arguments << [[:], [view: 'conflict']]
    }

    void 'a client that asks for application/problem+xml is answered with it, as Spring MVC answers it with an XML converter'() {
        given:
        request.addHeader('Accept', 'application/problem+xml')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT))

        then:
        response.status == 409
        response.contentType.startsWith('application/problem+xml')
    }

    void 'a problem the XML converter fails to write part-way leaves the response uncommitted, so that the error page applies'() {
        given: 'a problem with an extension member that cannot be read, which Spring MVC writes once it has begun the problem'
        request.addHeader('Accept', 'application/problem+xml')
        def problem = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        problem.setProperty('unreadable', new UnreadableXmlMember())

        when:
        controller.respond(problem)

        then:
        thrown(HttpMessageNotWritableException)
        !response.committed
    }

    void 'a status respond is given decides the status of the response, and the problem keeps its own'() {
        given:
        request.addHeader('Accept', 'application/problem+xml')

        when:
        controller.respond(ProblemDetail.forStatus(HttpStatus.CONFLICT), status: 400)

        then:
        response.status == 400
        new XmlSlurper().parseText(response.contentAsString).status.text() == '409'
    }

    void 'a problem of a class an XML converter writes is written by it, as Spring MVC asks the converters about its class'() {
        given: 'a converter that writes the subclass only, as a JAXB converter writes an annotated class'
        def renderer = new DefaultXmlRenderer<Object>(Object)
        renderer.springHttpMessageConvertersSupplier = { [new OutOfStockXmlConverter()] }

        when:
        def response = renderProblem(renderer, new OutOfStock(balance: 12.5G, product: 'Grails'))

        then:
        response.status == 409
        response.contentAsString == '<outOfStock/>'
    }

    void 'without a converter that writes problems in XML, the XML renderer writes a problem in JSON, as Spring MVC answers a client that asks for one in XML'() {
        given: 'Spring MVC without jackson-dataformat-xml, which has its JSON converter alone'
        def renderer = new DefaultXmlRenderer<Object>(Object)
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }

        when:
        def response = renderProblem(renderer, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, 'The title is taken'))

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        new JsonSlurper().parseText(response.contentAsString).detail == 'The title is taken'
    }

    void 'without a converter that writes problems, the legacy XML converter writes a problem as a bean, with the status it carries'() {
        given:
        def renderer = new DefaultXmlRenderer<Object>(Object)
        renderer.springHttpMessageConvertersSupplier = { [] }

        when:
        def response = renderProblem(renderer, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, 'The title is taken'))

        then:
        response.status == 409
        response.contentType.startsWith('application/xml')
        new XmlSlurper().parseText(response.contentAsString).detail.text() == 'The title is taken'
    }

    void 'a problem a client asks for as #accept whose member refers to itself fails as Spring MVC fails to write it'() {
        given:
        request.addHeader('Accept', accept)
        def member = new SelfReferencingMember(name: 'a')
        member.self = member

        when:
        controller.respond(extended('member', member))

        then:
        HttpMessageConversionException e = thrown()
        e.message.startsWith('Type definition error: ')

        where:
        accept << ['application/problem+json', 'application/problem+xml']
    }

    void 'a ProblemDetail is rendered by the XML view respond names, with the status it carries'() {
        given: 'an XML view of the action, and one that respond names'
        def locator = Stub(GrailsConventionGroovyPageLocator) {
            findViewForFormat('book', 'show', 'xml') >> Stub(GroovyPageScriptSource)
            findViewForFormat('book', 'conflict', 'xml') >> Stub(GroovyPageScriptSource)
        }
        def renderer = new DefaultXmlRenderer<Object>(Object, locator)
        def problem = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        def named = xmlRequest()

        when:
        renderer.render(problem, new ServletRenderContext(named, [view: 'conflict']) {
            @Override
            MimeType getAcceptMimeType() {
                MimeType.XML
            }
        })
        ModelAndView modelAndView = named.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:
        modelAndView.viewName == 'conflict'
        modelAndView.model == [problemDetail: problem]
        named.currentResponse.status == 409

        when: 'respond names no view'
        def unnamed = xmlRequest()
        renderer.springHttpMessageConvertersSupplier = { [new JacksonXmlHttpMessageConverter()] }
        renderer.render(ProblemDetail.forStatus(HttpStatus.CONFLICT), new ServletRenderContext(unnamed) {
            @Override
            MimeType getAcceptMimeType() {
                MimeType.XML
            }
        })

        then: 'the view of the action does not render the problem'
        unnamed.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW) == null
        unnamed.currentResponse.contentType.startsWith('application/problem+xml')
    }

    void 'the XML view respond names renders a ProblemDetail, rather than the HTML renderer an application registers for problems'() {
        given:
        def locator = Stub(GrailsConventionGroovyPageLocator) {
            findViewForFormat('book', 'conflict', 'xml') >> Stub(GroovyPageScriptSource)
        }
        def page = Stub(Renderer) {
            render(_, _) >> { Object problem, RenderContext context -> context.viewName = '/errors/problem' }
        }
        def registry = Stub(RendererRegistry) {
            findRenderer(MimeType.HTML, _) >> page
        }
        def renderer = new DefaultXmlRenderer<Object>(Object, locator, registry)
        def problem = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        def named = xmlRequest()

        when:
        renderer.render(problem, new ServletRenderContext(named, [view: 'conflict']) {
            @Override
            MimeType getAcceptMimeType() {
                MimeType.XML
            }
        })
        ModelAndView modelAndView = named.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:
        modelAndView.viewName == 'conflict'
        modelAndView.model == [problemDetail: problem]
        named.currentResponse.status == 409
    }

    void 'the XML view respond names gets a ProblemDetail under the model name its HTML view gets it'() {
        given: 'a registry that names the model of a view with the domain suffix'
        def locator = Stub(GrailsConventionGroovyPageLocator) {
            findViewForFormat('book', 'conflict', 'xml') >> Stub(GroovyPageScriptSource)
        }
        def registry = new DefaultRendererRegistry(modelSuffix: 'Instance')
        registry.initialize()
        def renderer = new DefaultXmlRenderer<Object>(Object, locator, registry)
        def problem = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        def html = xmlRequest()

        when: 'the HTML view'
        registry.findRenderer(MimeType.HTML, problem).render(problem, new ServletRenderContext(html, [view: 'conflict']))

        then:
        html.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW).model == [problemDetailInstance: problem]

        when: 'the XML view'
        def xml = xmlRequest()
        renderer.render(problem, new ServletRenderContext(xml, [view: 'conflict']) {
            @Override
            MimeType getAcceptMimeType() {
                MimeType.XML
            }
        })

        then:
        xml.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW).model == [problemDetailInstance: problem]
    }

    private static ProblemDetail extended(String name, Object value) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        problem.setProperty(name, value)
        problem
    }

    private static GrailsWebRequest xmlRequest() {
        def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockServletContext())
        webRequest.controllerName = 'book'
        webRequest.actionName = 'show'
        webRequest
    }

    private static MockHttpServletResponse renderProblem(DefaultXmlRenderer<Object> renderer, ProblemDetail problem) {
        def response = new MockHttpServletResponse()
        def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())
        renderer.render(problem, new ServletRenderContext(webRequest) {
            @Override
            MimeType getAcceptMimeType() {
                MimeType.XML
            }
        })
        response
    }

    /** The name, namespace, nil attribute and content of each element, which the XML a writer formats leaves alike. */
    private static List tree(String xml) {
        node(new XmlParser(false, true).parseText(xml))
    }

    private static List node(Node element) {
        def named = element.name()
        QName name = named instanceof QName ? (QName) named : new QName('', named.toString())
        Object nil = null
        element.attributes().each { Object key, Object value ->
            if (key instanceof QName && ((QName) key).localPart == 'nil') {
                nil = value
            }
        }
        List<Node> children = element.children().findAll { it instanceof Node } as List<Node>
        List content = []
        for (Node child : children) {
            content << node(child)
        }
        [name.localPart, name.namespaceURI, nil, children ? content : element.text()]
    }
}

@Artefact('Controller')
class ProblemXmlController { }

class OutOfStockXmlConverter extends AbstractHttpMessageConverter<Object> {

    OutOfStockXmlConverter() {
        super(MediaType.APPLICATION_PROBLEM_XML)
    }

    @Override
    protected boolean supports(Class<?> type) {
        OutOfStock.isAssignableFrom(type)
    }

    @Override
    protected Object readInternal(Class<? extends Object> type, HttpInputMessage message) {
        throw new UnsupportedOperationException()
    }

    @Override
    protected void writeInternal(Object value, HttpOutputMessage message) {
        message.body.write('<outOfStock/>'.getBytes('UTF-8'))
    }
}

class UnreadableXmlMember {
    String getValue() {
        throw new IllegalStateException('the value cannot be read')
    }
}

class OutOfStock extends ProblemDetail {
    BigDecimal balance
    String product

    OutOfStock() {
        super(409)
    }
}

@JsonSerialize(using = SelfWrittenProblemSerializer)
class SelfWrittenProblem extends ProblemDetail {

    SelfWrittenProblem() {
        super(409)
    }
}

class SelfWrittenProblemSerializer extends ValueSerializer<SelfWrittenProblem> {

    @Override
    void serialize(SelfWrittenProblem value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeStringProperty('title', 'Own')
        generator.writeNumberProperty('status', value.status)
        generator.writeStringProperty('retry', 'later')
        generator.writeEndObject()
    }
}

class SelfReferencingMember {
    String name
    SelfReferencingMember self
}
