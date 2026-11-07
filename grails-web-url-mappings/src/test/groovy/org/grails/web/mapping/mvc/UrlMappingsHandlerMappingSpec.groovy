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
package org.grails.web.mapping.mvc

import grails.artefact.Artefact
import grails.artefact.Controller
import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.Action
import grails.web.HyphenatedUrlConverter
import grails.web.mapping.AbstractUrlMappingsSpec
import jakarta.servlet.FilterChain
import org.grails.web.mapping.DefaultUrlMappingData
import org.grails.web.mapping.DefaultUrlMappingInfo
import org.grails.web.servlet.mvc.GrailsDispatcherServlet
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.GrailsWebRequestFilter
import org.grails.web.servlet.view.CompositeViewResolver
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletConfig
import org.springframework.mock.web.MockServletContext
import org.springframework.ui.ModelMap
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.WebRequest
import org.springframework.web.context.request.WebRequestInterceptor
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.filter.RequestContextFilter
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter
import org.springframework.web.servlet.view.InternalResourceView
import spock.lang.Issue

/**
 * Created by graemerocher on 26/05/14.
 */
class UrlMappingsHandlerMappingSpec extends AbstractUrlMappingsSpec {

    void "Test that when a request coming from a 404 forward is matched the correct action is executed"() {
        given:"A URL mapping definition that has a 404 mapping"
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/foo/bar"(controller:"foo", action:"bar")
            "/foo/error"(controller:"foo", action:"error")
            "404"(controller: "foo", action:"notFound")
        }

        holder = new GrailsControllerUrlMappings(grailsApplication, holder)
        def handler = new UrlMappingsHandlerMapping(holder)

        when:"A request that contains a 404 error status code is handled"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/foo/notThere")
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, "404")
        def handlerChain = handler.getHandler(request)

        then:"The handler chain is not null"
        handlerChain != null

        when:"A HandlerAdapter is used"
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then:"The controller action that is mapped to the 404 handler is executed"
        webRequest.response.contentAsString == 'Not Found'
    }

    @Issue('https://github.com/apache/grails-core/issues/10149')
    void "Test that when an include request from within a 404 forward is matched"() {
        given:"A URL mapping definition that has a 404 mapping"
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/foo/bar"(controller:"foo", action:"bar")
            "/foo/error"(controller:"foo", action:"error")
            "404"(controller: "foo", action:"notFound")
        }

        holder = new GrailsControllerUrlMappings(grailsApplication, holder)
        def handler = new UrlMappingsHandlerMapping(holder)

        when:"A request arrives that is an include within a 404 forward request"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/foo/notThere")
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, "404")
        request.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, "/foo/bar")
        def handlerChain = handler.getHandler(request)

        then:"The handler chain is not null"
        handlerChain != null

        when:"A HandlerAdapter is used"
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then:"The correct action was executed to handle the include"
        result.viewName == 'bar'
        result.model == [foo:'bar']

    }

    void "Test that a matched URL returns a URLMappingInfo"() {

        given:
            def grailsApplication = new DefaultGrailsApplication(FooController)
            grailsApplication.initialise()
            def holder = getUrlMappingsHolder {
                "/foo/bar"(controller:"foo", action:"bar")
                "/foo/error"(controller:"foo", action:"error")
            }

            holder = new GrailsControllerUrlMappings(grailsApplication, holder)
            def handler = new UrlMappingsHandlerMapping(holder)

        when:"A URI is matched"

            def webRequest = GrailsWebMockUtil.bindMockWebRequest()
            webRequest.renderView = true
            def request = webRequest.request
            request.setRequestURI("/foo/bar")
            def handlerChain = handler.getHandler(request)

        then:"A handlerChain is created"
            handlerChain != null

        when:"A HandlerAdapter is used"
            def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
            def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then:"The model and view is correct"
            result.viewName == 'bar'
            result.model == [foo:'bar']

        when:"A status is set on the response"
        request.setRequestURI("/foo/error")
        request.removeAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST)
        handlerChain = handler.getHandler(request)
        result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then:"The result is null"
        result == null

    }

    void "Test that a matched URL returns a URLMappingInfo when result == null from controller"() {

        given:
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/foo/foo-bar"(controller:"foo", action:"foo-bar")
            "/foo/error"(controller:"foo", action:"error")
        }
        def urlConverter = new HyphenatedUrlConverter()
        holder = new GrailsControllerUrlMappings(grailsApplication, holder, urlConverter)
        def handler = new UrlMappingsHandlerMapping(holder)

        when:"A URI is matched"

        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/foo/foo-bar")
        def handlerChain = handler.getHandler(request)

        then:"A handlerChain is created"
        handlerChain != null

        when:"A HandlerAdapter is used with a hyphenated url converter"
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then:"The model and view is correct"
        result.viewName == 'fooBar'
        !result.model
    }

    void "Test that a matched URL returns a URLMappingInfo with controller with defaultAction"() {

        given:
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/foo"(controller:"foo")
        }
        def urlConverter = new HyphenatedUrlConverter()
        holder = new GrailsControllerUrlMappings(grailsApplication, holder, urlConverter)
        def handler = new UrlMappingsHandlerMapping(holder)

        when:"A URI is matched"

        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/foo")
        def handlerChain = handler.getHandler(request)

        then:"A handlerChain is created"
        handlerChain != null

        when:"A HandlerAdapter is used with a hyphenated url converter"
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then:"The model and view is correct"
        result.viewName == 'fooBar'
        !result.model
    }


    void "test modelAndView is returned for URI"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, new DefaultUrlMappingInfo("/index.html", new DefaultUrlMappingData("/"), grailsApplication))

        expect:
        result
        result.view instanceof InternalResourceView
        result.view.getUrl() == "/index.html"
    }

    void "the handler chain keeps WebRequestInterceptors first and appends the Grails interceptors last"() {
        given: "a handler mapping with both a WebRequestInterceptor and an ordinary HandlerInterceptor"
        def handlerMapping = handlerMapping()
        def plainInterceptor = new HandlerInterceptor() {}
        def webRequestInterceptor = new WebRequestInterceptor() {
            void preHandle(WebRequest request) {}
            void postHandle(WebRequest request, ModelMap model) {}
            void afterCompletion(WebRequest request, Exception ex) {}
        }
        handlerMapping.setHandlerInterceptors([plainInterceptor] as HandlerInterceptor[])
        handlerMapping.setWebRequestInterceptors([webRequestInterceptor] as WebRequestInterceptor[])
        handlerMapping.setApplicationContext(new StaticWebApplicationContext())

        when:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.setRequestURI("/foo/bar")
        def interceptors = handlerMapping.getHandler(webRequest.request).interceptorList

        then: "the WebRequestInterceptor is adapted and ordered first, ahead of ordinary interceptors"
        interceptors[0] instanceof WebRequestHandlerInterceptorAdapter
        interceptors.contains(plainInterceptor)

        and: "the Grails interceptors are appended last, in order"
        interceptors[-2].class.simpleName == 'ObservationRouteHandler'
        interceptors[-1].class.simpleName == 'ErrorHandlingHandler'
    }

    void "the Grails interceptors are shared rather than allocated per request"() {
        given:
        def handlerMapping = handlerMapping()
        handlerMapping.setApplicationContext(new StaticWebApplicationContext())

        when: "two separate requests are handled"
        def first = GrailsWebMockUtil.bindMockWebRequest()
        first.request.setRequestURI("/foo/bar")
        def firstChain = handlerMapping.getHandler(first.request).interceptorList
        RequestContextHolder.resetRequestAttributes()
        def second = GrailsWebMockUtil.bindMockWebRequest()
        second.request.setRequestURI("/foo/bar")
        def secondChain = handlerMapping.getHandler(second.request).interceptorList

        then: "both chains end with the very same interceptor instances"
        firstChain[-1].is(secondChain[-1])
        firstChain[-2].is(secondChain[-2])
    }

    private UrlMappingsHandlerMapping handlerMapping() {
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder {
            "/foo/bar"(controller: "foo", action: "bar")
        })
        new UrlMappingsHandlerMapping(holder)
    }

    @Issue('https://github.com/apache/grails-core/issues/15819')
    void "late RequestContextFilter does not cause implicit view resolution after #actionName"() {
        given: "a real dispatcher and handler adapter for a template-rendering action"
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder {
            "/foo/$actionName"(controller: 'foo', action: actionName)
        })
        def servletContext = new MockServletContext()
        def ctx = new StaticWebApplicationContext()
        ctx.servletContext = servletContext
        def templateView = Mock(View)
        def templateResolver = Mock(CompositeViewResolver)
        def implicitViewResolver = Mock(ViewResolver)
        ctx.beanFactory.registerSingleton(CompositeViewResolver.BEAN_NAME, templateResolver)
        ctx.beanFactory.registerSingleton('viewResolver', implicitViewResolver)
        ctx.beanFactory.registerSingleton('handlerMapping', new UrlMappingsHandlerMapping(holder))
        ctx.beanFactory.registerSingleton('handlerAdapter', new UrlMappingsInfoHandlerAdapter())
        servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, ctx)
        ctx.refresh()
        def servlet = new GrailsDispatcherServlet(ctx)
        servlet.init(new MockServletConfig(servletContext))
        def grailsFilter = new GrailsWebRequestFilter()
        grailsFilter.setServletContext(servletContext)
        grailsFilter.setApplicationContext(ctx)
        def lateFilter = new RequestContextFilter()
        def request = new MockHttpServletRequest(servletContext, 'GET', "/foo/$actionName")
        def response = new MockHttpServletResponse()
        GrailsWebRequest filterRequest
        GrailsWebRequest renderingRequest
        GrailsWebRequest attributeRequest

        when: "Spring's filter replaces the thread binding after the Grails filter"
        grailsFilter.doFilter(request, response, { req, res ->
            filterRequest = GrailsWebRequest.lookup(req)
            lateFilter.doFilter(req, res, { lateRequest, lateResponse ->
                assert !(RequestContextHolder.requestAttributes instanceof GrailsWebRequest)
                servlet.service(lateRequest, lateResponse)
            } as FilterChain)
        } as FilterChain)

        then: "the template is rendered without committing the response"
        1 * templateResolver.resolveView('/foo/_greeting', _) >> templateView
        1 * templateView.render([name: 'Grails'], _, _) >> { model, req, res ->
            renderingRequest = GrailsWebRequest.lookup()
            attributeRequest = req.getAttribute(GrailsApplicationAttributes.WEB_REQUEST)
            res.writer.write("Hello ${model.name}")
        }
        0 * implicitViewResolver.resolveViewName(_, _)
        response.status == 200
        response.contentAsString == 'Hello Grails'
        !response.committed

        and: "rendering uses the published dispatch request, not the filter request"
        !renderingRequest.is(filterRequest)
        renderingRequest.is(attributeRequest)
        !renderingRequest.renderView
        filterRequest.renderView
        RequestContextHolder.requestAttributes == null

        cleanup:
        servlet?.destroy()
        ctx.close()

        where:
        actionName << ['renderTemplate']
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }
}

@Artefact('Controller')
class FooController implements Controller {

    static defaultAction = 'fooBar'

    @Action
    def bar() {
        [foo:"bar"]
    }

    @Action
    def fooBar() {

    }

    @Action
    def error() {
        RequestContextHolder.currentRequestAttributes().response.sendError(405)
    }

    @Action
    def notFound() {
        RequestContextHolder.currentRequestAttributes().response.writer << "Not Found"
    }

    @Action
    def renderTemplate() {
        render(template: '/foo/greeting', model: [name: 'Grails'])
    }
}
