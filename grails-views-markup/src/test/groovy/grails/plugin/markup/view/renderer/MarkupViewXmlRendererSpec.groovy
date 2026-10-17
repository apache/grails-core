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

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView

import grails.plugin.markup.view.MarkupViewConfiguration
import grails.plugin.markup.view.mvc.MarkupViewResolver
import grails.rest.render.RenderContext
import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.SpringMessageConverters
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

    void 'without a view or an XML renderer, a problem is written as problem JSON, as respond writes it'() {
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
        renderer.render(problem, new ServletRenderContext(webRequest, [view: '/missing']))

        then:
        renderer instanceof MarkupViewXmlRenderer
        webRequest.response.status == 422
        webRequest.response.contentType.startsWith('application/problem+json')
    }
}

class MarkupBody {
    String title
}
