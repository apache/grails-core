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
package grails.plugin.markup.view.renderer

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.context.support.StaticApplicationContext
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.View
import org.springframework.web.servlet.view.AbstractUrlBasedView

import grails.artefact.Controller
import grails.artefact.controller.RestResponder
import grails.core.DefaultGrailsApplication
import grails.plugin.markup.view.MarkupViewConfiguration
import grails.plugin.markup.view.mvc.MarkupViewResolver
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.SpringMessageConverters
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification

/**
 * Without the grails-xml module, which this module's tests do not include, the registry has no XML renderer of its own,
 * so the markup view renderer answers 406 for what no view renders, and a problem as JSON, as {@code respond} answers
 * for a format no renderer writes.
 */
class MarkupViewXmlRendererSpec extends Specification {

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
        ConvertersConfigurationHolder.clear()
    }

    void 'without an XML renderer, the renderer says it renders only what a view renders'() {
        given:
        def renderer = new MarkupViewXmlRenderer(Object, resolving(views, templates), null, null, null)

        expect:
        renderer.canRender(value, context(arguments)) == renders

        where:
        value                           | arguments           | views                         | templates                        || renders
        new MarkupBody(title: 'Grails') | [view: '/missing']  | [:]                           | [:]                              || false
        new MarkupBody(title: 'Grails') | [view: '/book']     | ['/book': new XmlView()]      | [:]                              || true
        new MarkupBody(title: 'Grails') | [:]                 | ['/book/show': new XmlView()] | [:]                              || true
        new MarkupBody(title: 'Grails') | [:]                 | [:]                           | [(MarkupBody): new XmlView()]    || true
        ProblemDetail.forStatus(409)    | [:]                 | ['/book/show': new XmlView()] | [(ProblemDetail): new XmlView()] || false
        ProblemDetail.forStatus(409)    | [view: '/missing']  | [:]                           | [:]                              || false
        ProblemDetail.forStatus(409)    | [view: '/conflict'] | ['/conflict': new XmlView()]  | [:]                              || true
    }

    void 'with an XML renderer to fall back to, the renderer renders any value'() {
        given:
        def renderer = new MarkupViewXmlRenderer(Object, resolving([:], [:]), null, null, Stub(Renderer))

        expect:
        renderer.canRender(new MarkupBody(title: 'Grails'), context([view: '/missing']))
        renderer.canRender(ProblemDetail.forStatus(409), context([:]))
    }

    void "respond answers in the action's next format, #description, when no markup view renders the value as XML"() {
        given:
        new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication()).initialize()
        def registry = new DefaultRendererRegistry()
        registry.initialize()
        def resolver = new MarkupViewResolver(new MarkupViewConfiguration())
        resolver.rendererRegistry = registry
        resolver.initialize()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.controllerName = 'book'
        webRequest.actionName = 'show'
        if (accept) {
            webRequest.currentRequest.addHeader('Accept', accept)
        }

        when:
        new XmlFirstController(rendererRegistry: registry).respond([title: 'Grails'])

        then:
        registry.findRenderer(MimeType.XML, [:]) instanceof MarkupViewXmlRenderer
        webRequest.response.status == 200
        webRequest.response.contentType.startsWith('application/json')
        webRequest.response.contentAsString == '{"title":"Grails"}'

        where:
        description                          | accept
        'for a client that lists XML first'  | 'application/xml, application/json'
        'for a client that accepts any type' | null
    }

    void 'respond renders a problem with the markup view respond names in the controller namespace'() {
        given: 'an application whose views include no GSP of the name'
        new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication()).initialize()
        def registry = new DefaultRendererRegistry()
        registry.initialize()
        def resolver = resolving(['/admin/book/conflict': new XmlView()], [:])
        resolver.rendererRegistry = registry
        resolver.initialize()
        def problem = ProblemDetail.forStatus(409)
        def webRequest = xmlClientRequest()
        webRequest.controllerNamespace = 'admin'

        when:
        new XmlFirstController(rendererRegistry: registry).respond(problem, view: 'conflict')

        then:
        webRequest.response.status == 409
        webRequest.response.contentType.startsWith('application/xml')
        ((ModelAndView) webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)).view instanceof XmlView
    }

    void 'respond writes a problem with the XML renderer markup views fall back to, where the view respond names does not exist'() {
        given: "a renderer standing for grails-xml's, which the markup view renderer falls back to"
        new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication()).initialize()
        def registry = new DefaultRendererRegistry()
        registry.initialize()
        def standIn = new ProblemXmlStandIn()
        registry.addDefaultRenderer(standIn)
        def resolver = resolving([:], [:])
        resolver.rendererRegistry = registry
        resolver.initialize()
        def problem = ProblemDetail.forStatus(409)
        def webRequest = xmlClientRequest()

        when:
        new XmlFirstController(rendererRegistry: registry).respond(problem, view: 'conflict')

        then:
        standIn.rendered == [problem]
        webRequest.response.status == 409
        webRequest.response.contentType.startsWith('application/problem+xml')
    }

    private static GrailsWebRequest xmlClientRequest() {
        def context = new StaticApplicationContext()
        context.refresh()
        def request = new MockHttpServletRequest()
        request.addHeader('Accept', 'application/xml')
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(context, request, new MockHttpServletResponse())
        webRequest.controllerName = 'book'
        webRequest.actionName = 'update'
        webRequest
    }

    private static MarkupViewResolver resolving(Map<String, View> views, Map<Class, View> templates) {
        new MarkupViewResolver(new MarkupViewConfiguration()) {
            @Override
            View resolveView(String viewName, HttpServletRequest request, HttpServletResponse response) {
                views[viewName]
            }

            @Override
            View resolveView(Class type, HttpServletRequest request, HttpServletResponse response) {
                templates[type]
            }
        }
    }

    private static ServletRenderContext context(Map<String, Object> arguments) {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.controllerName = 'book'
        webRequest.actionName = 'show'
        webRequest.request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView())
        new ServletRenderContext(webRequest, arguments)
    }

    void 'without a view or an XML renderer, an object is not acceptable as XML'() {
        given:
        def registry = new DefaultRendererRegistry()
        registry.initialize()
        def resolver = new MarkupViewResolver(new MarkupViewConfiguration())
        resolver.rendererRegistry = registry
        resolver.initialize()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView())

        when:
        def renderer = registry.findRenderer(MimeType.XML, value)
        renderer.render(value, new ServletRenderContext(webRequest, [view: '/missing']))

        then: 'the error is sent, as respond sends it, so that the error page applies'
        renderer instanceof MarkupViewXmlRenderer
        webRequest.response.status == 406
        webRequest.response.committed

        where:
        value << [new MarkupBody(title: 'Grails')]
    }

    void 'without a renderer to fall back to, a problem is not acceptable in a context other than a servlet one'() {
        given:
        def renderer = new MarkupViewXmlRenderer(Object, new MarkupViewResolver(new MarkupViewConfiguration()), null, null, null)
        def context = Mock(RenderContext) {
            getArguments() >> [:]
        }

        when:
        renderer.render(ProblemDetail.forStatus(422), context)

        then:
        1 * context.setStatus(HttpStatus.NOT_ACCEPTABLE)
    }

    void 'without a view or an XML renderer, a problem is written as problem JSON, as respond writes it, with status #status'() {
        given: "a registry with Spring MVC's JSON converter, which writes problems"
        def registry = new DefaultRendererRegistry()
        registry.springMessageConverters = new SpringMessageConverters()
        registry.springMessageConverters.setConverters([new JacksonJsonHttpMessageConverter()])
        registry.initialize()
        def resolver = new MarkupViewResolver(new MarkupViewConfiguration())
        resolver.rendererRegistry = registry
        resolver.initialize()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView())
        def problem = ProblemDetail.forStatus(422)

        when:
        def renderer = registry.findRenderer(MimeType.XML, problem)
        renderer.render(problem, new ServletRenderContext(webRequest, [view: '/missing'] + arguments))

        then:
        renderer instanceof MarkupViewXmlRenderer
        webRequest.response.status == status
        webRequest.response.contentType.startsWith('application/problem+json')

        where: 'the status the problem carries, or the one respond is given'
        arguments     || status
        [:]           || 422
        [status: 400] || 400
    }
}

class MarkupBody {
    String title
}

class XmlFirstController implements Controller, RestResponder {
    static responseFormats = ['xml', 'json']
}

class ProblemXmlStandIn implements Renderer<Object> {

    List<Object> rendered = []

    @Override
    Class<Object> getTargetType() {
        Object
    }

    @Override
    MimeType[] getMimeTypes() {
        [MimeType.XML] as MimeType[]
    }

    @Override
    void render(Object object, RenderContext context) {
        rendered << object
        context.setContentType('application/problem+xml')
        context.writer.write('<problem/>')
    }
}

class XmlView extends AbstractUrlBasedView {

    @Override
    protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) {
    }
}
