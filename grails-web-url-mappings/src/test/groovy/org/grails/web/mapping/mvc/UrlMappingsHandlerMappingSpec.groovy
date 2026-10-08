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
import org.grails.web.mapping.DefaultUrlMappingData
import org.grails.web.mapping.DefaultUrlMappingInfo
import org.grails.web.util.WebUtils
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.ui.ModelMap
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.WebRequest
import org.springframework.web.context.request.WebRequestInterceptor
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter
import org.springframework.web.servlet.view.InternalResourceView
import spock.lang.Issue
import spock.lang.Unroll

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
    @Unroll
    void "adapter returns null when render() writes the body for action '#actionName' (result=#resultDesc)"() {
        given: "a URL mapping for an action that calls render() to write the response body"
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def linkGenerator = getLinkGenerator {
            "/$controller/$action?/$id?"()
        }
        def holder = getUrlMappingsHolder {
            "/foo/$actionName"(controller: "foo", action: actionName)
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)
        def handler = new UrlMappingsHandlerMapping(holder)

        and: "an application context that provides the LinkGenerator needed by redirect()"
        def ctx = new StaticWebApplicationContext()
        ctx.beanFactory.registerSingleton('grailsLinkGenerator', linkGenerator)
        ctx.refresh()

        when: "the request is dispatched"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(ctx, new MockHttpServletRequest(), new MockHttpServletResponse())
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/foo/$actionName")
        def handlerChain = handler.getHandler(request)
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then: "the adapter returns null — no ModelAndView is passed to DispatcherServlet for view resolution"
        result == null

        where:
        actionName              | resultDesc
        'renderText'            | 'null (render(text:) returns null)'
        'renderTextWithMap'     | 'Map (render(text:) called, action also returns a Map)'
        'redirectWithMap'       | 'Map (redirect() issued, action also returns a Map)'
        'committedWithMap'      | 'Map (response already committed, action also returns a Map)'
    }

    @Issue('https://github.com/apache/grails-core/issues/15819')
    void "adapter returns ModelAndView when render(view:) is used (renderView stays true, MODEL_AND_VIEW attribute is set)"() {
        given: "a URL mapping for an action that uses render(view:)"
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/foo/renderView"(controller: "foo", action: "renderView")
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)
        def handler = new UrlMappingsHandlerMapping(holder)

        when: "the request is dispatched"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/foo/renderView")
        def handlerChain = handler.getHandler(request)
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then: "the adapter returns the ModelAndView set by render(view:) so DispatcherServlet resolves the named view"
        result != null
        result.viewName == '/foo/myView'
    }

    @Issue('https://github.com/apache/grails-core/issues/15819')
    void "adapter returns ModelAndView for an include dispatch even when the outer response is already committed"() {
        given: "a URL mapping for an action that returns a Map"
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/foo/bar"(controller: "foo", action: "bar")
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)
        def handler = new UrlMappingsHandlerMapping(holder)

        when: "the request is an include dispatch and the outer response is already committed"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        def response = webRequest.response
        request.setRequestURI("/foo/bar")
        // Mark the request as a servlet include dispatch
        request.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, "/foo/bar")
        // Simulate the outer response having been flushed before the include ran
        response.flushBuffer()
        def handlerChain = handler.getHandler(request)
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, response, handlerChain.handler)

        then: "the adapter returns a ModelAndView so the included action's view is rendered"
        result != null
        result.viewName == 'bar'
        result.model == [foo: 'bar']
    }

    @Issue('https://github.com/apache/grails-core/issues/15819')
    void "adapter returns ModelAndView when action sets an error status and returns a model (guard must not swallow error views)"() {
        given: "a URL mapping for an action that sets an error status and returns a model"
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/foo/errorWithModel"(controller: "foo", action: "errorWithModel")
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)
        def handler = new UrlMappingsHandlerMapping(holder)

        when: "the request is dispatched"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/foo/errorWithModel")
        def handlerChain = handler.getHandler(request)
        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then: "the adapter returns a ModelAndView so the error view is rendered — the guard must not swallow it"
        result != null
        result.model == [message: 'not found']
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

    /**
     * Calls render(text:), which sets renderView=false and writes the body. The adapter must
     * return null so DispatcherServlet does not attempt view resolution. (#15819)
     */
    @Action
    def renderText() {
        render(text: 'hello')
        null
    }

    /**
     * Calls render(text:) and also returns a Map — the exact bug scenario from #15819 where the
     * adapter previously ignored renderView=false when result instanceof Map.
     */
    @Action
    def renderTextWithMap() {
        render(text: 'hello')
        [foo: 'bar']
    }

    /**
     * Calls redirect() (which sets REDIRECT_ISSUED on the request and a 3xx status without calling
     * setRenderView(false)) and also returns a Map. The adapter must return null — there is nothing
     * left for DispatcherServlet to do after a redirect. (#15819)
     */
    @Action
    def redirectWithMap() {
        redirect(uri: 'http://example.com/redirected')
        [foo: 'bar']
    }

    /**
     * Writes directly to the response (committing it) and also returns a Map. The adapter must
     * return null — DispatcherServlet cannot render a view into an already-committed response.
     * (#15819)
     */
    @Action
    def committedWithMap() {
        response.writer.write('already committed')
        response.flushBuffer()
        [foo: 'bar']
    }

    /**
     * Calls render(view: 'myView'), which sets MODEL_AND_VIEW on the request but does NOT set
     * renderView=false. The adapter must return the ModelAndView so DispatcherServlet resolves
     * the named view. (#15819)
     */
    @Action
    def renderView() {
        render(view: '/foo/myView')
    }

    /**
     * Sets an error status on the response and returns a model Map without calling render().
     * The renderView flag stays true, so the adapter must still return a ModelAndView and let
     * DispatcherServlet render the error view. The guard introduced for #15819 must not swallow
     * this case. (#15819)
     */
    @Action
    def errorWithModel() {
        response.status = 404
        [message: 'not found']
    }
}
