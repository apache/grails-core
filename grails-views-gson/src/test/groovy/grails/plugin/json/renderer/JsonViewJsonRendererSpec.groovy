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
package grails.plugin.json.renderer

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.http.ProblemDetail
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.View
import org.springframework.web.servlet.view.AbstractUrlBasedView

import grails.plugin.json.view.mvc.JsonViewResolver
import grails.rest.render.Renderer
import grails.util.GrailsWebMockUtil
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification

class JsonViewJsonRendererSpec extends Specification {

    Renderer defaultRenderer = Mock(Renderer)

    JsonViewJsonRenderer<Object> renderer = new JsonViewJsonRenderer<Object>(Object, new JsonViewResolver(), null, null,
            defaultRenderer)

    ModelAndView modelAndView = new ModelAndView()

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'the view respond names renders an object'() {
        given:
        def view = new ProblemView()
        def viewRenderer = new JsonViewJsonRenderer<Object>(Object, resolving('/book': view), null, null, defaultRenderer)

        when:
        viewRenderer.render([title: 'Grails'], context([view: '/book']))

        then:
        0 * defaultRenderer.render(_, _)
        modelAndView.view.is(view)
    }

    void 'a ProblemDetail is written by the default renderer, even where a view is found by convention'() {
        given: 'no view named, where the action has a view'
        def viewRenderer = new JsonViewJsonRenderer<Object>(Object, resolving('/book/show': new ProblemView()), null, null,
                defaultRenderer)
        def context = context([:])
        context.webRequest.controllerName = 'book'
        context.webRequest.actionName = 'show'
        def problem = ProblemDetail.forStatus(422)

        when:
        viewRenderer.render(problem, context)

        then: 'the problem keeps its status and content type rather than becoming the model of the view'
        1 * defaultRenderer.render(problem, context)
        modelAndView.view == null
    }

    void 'a ProblemDetail is rendered with the view respond names, with the status it carries'() {
        given:
        def problemView = new ProblemView()
        def viewRenderer = new JsonViewJsonRenderer<Object>(Object, resolving('/problem': problemView), null, null,
                defaultRenderer)
        def context = context([view: '/problem'])
        context.webRequest.currentRequest.requestURI = '/books/1'
        def problem = ProblemDetail.forStatus(422)

        when:
        viewRenderer.render(problem, context)

        then:
        0 * defaultRenderer.render(_, _)
        modelAndView.view.is(problemView)
        context.webRequest.currentResponse.status == 422
        problem.instance == URI.create('/books/1')
    }

    void 'a ProblemDetail is written by the default renderer when the view respond names does not exist'() {
        given:
        def context = context([view: '/missing'])
        def problem = ProblemDetail.forStatus(422)

        when:
        renderer.render(problem, context)

        then: 'rather than by the object template'
        1 * defaultRenderer.render(problem, context)
        modelAndView.view == null
    }

    private static JsonViewResolver resolving(Map<String, View> views) {
        new JsonViewResolver() {
            @Override
            View resolveView(String viewName, HttpServletRequest request, HttpServletResponse response) {
                views[viewName]
            }
        }
    }

    private ServletRenderContext context(Map<String, Object> arguments = [view: '/foo']) {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, modelAndView)
        new ServletRenderContext(webRequest, arguments)
    }
}

class ProblemView extends AbstractUrlBasedView {

    @Override
    protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) {
    }
}
