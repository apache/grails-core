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

import java.sql.SQLException

import jakarta.servlet.DispatcherType

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.Action
import grails.web.HyphenatedUrlConverter
import grails.web.mapping.AbstractUrlMappingsSpec
import org.grails.web.errors.GrailsWrappedRuntimeException
import org.grails.web.mapping.DefaultUrlMappingData
import org.grails.web.mapping.DefaultUrlMappingInfo
import org.grails.web.util.WebUtils
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.ui.ModelMap
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.WebRequest
import org.springframework.web.context.request.WebRequestInterceptor
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.filter.ServerHttpObservationFilter
import org.springframework.web.servlet.HandlerInterceptor
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

    void "a mapping for an exception is found for #what"() {
        given:
        def holder = getUrlMappingsHolder {
            "500"(controller: 'errors', action: 'ioFailure', exception: IOException)
            "500"(controller: 'errors', action: 'illegalArgument', exception: IllegalArgumentException)
            "500"(controller: 'errors', action: 'anyFailure')
        }
        def handler = new UrlMappingsHandlerMapping(holder)
        def webContext = new StaticWebApplicationContext()
        webContext.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        webContext.refresh()
        def request = GrailsWebMockUtil.bindMockWebRequest().request
        request.servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext)
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, '500')
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE,
                wrapped ? new GrailsWrappedRuntimeException(request.servletContext, thrown) : thrown)

        when:
        def info = handler.getHandler(request).handler

        then:
        info.actionName == action

        cleanup:
        webContext.close()

        where:
        what                                                     | wrapped | thrown                                                                           | action
        'the exception an error handler is forwarded for'        | true    | new IOException('thrown', new IllegalStateException('its own cause'))            | 'ioFailure'
        'the exception the container reports'                    | false   | new IOException('thrown', new IllegalStateException('its own cause'))            | 'ioFailure'
        'the root cause an error handler is forwarded for'       | true    | new IllegalStateException('thrown', new IOException('the root cause'))           | 'ioFailure'
        'the root cause the container reports'                   | false   | new IllegalStateException('thrown', new IOException('the root cause'))           | 'ioFailure'
        'the exception first, where its root cause has one'      | true    | new IllegalArgumentException('thrown', new IOException('the root cause'))        | 'illegalArgument'
        'neither, which leaves the mapping without an exception' | true    | new IllegalStateException('thrown', new UnsupportedOperationException('no map')) | 'anyFailure'
    }

    void "where several mappings match the exception, the one declared last answers, which keeps #how"() {
        given:
        def holder = getUrlMappingsHolder(mappings)
        def handler = new UrlMappingsHandlerMapping(holder)
        def webContext = new StaticWebApplicationContext()
        webContext.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        webContext.refresh()
        def request = GrailsWebMockUtil.bindMockWebRequest().request
        request.servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext)
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, '500')
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, new GrailsWrappedRuntimeException(request.servletContext,
                new DataLayerException(new SQLException('the root cause'))))

        when:
        def info = handler.getHandler(request).handler

        then:
        info.actionName == action

        cleanup:
        webContext.close()

        where:
        how                                                         | action     | mappings
        'the mapping for the exception ahead of its root cause'     | 'runtime'  | {
            "500"(controller: 'errors', action: 'database', exception: SQLException)
            "500"(controller: 'errors', action: 'runtime', exception: RuntimeException)
        }
        'a mapping declared before a broader one from answering'    | 'runtime'  | {
            "500"(controller: 'errors', action: 'database', exception: SQLException)
            "500"(controller: 'errors', action: 'database', exception: DataLayerException)
            "500"(controller: 'errors', action: 'runtime', exception: RuntimeException)
        }
        'the workaround the upgrade guide gives working'            | 'database' | {
            "500"(controller: 'errors', action: 'database', exception: SQLException)
            "500"(controller: 'errors', action: 'runtime', exception: RuntimeException)
            "500"(controller: 'errors', action: 'database', exception: DataLayerException)
        }
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

    void "the handler chain keeps WebRequestInterceptors first, then the route, and appends the error handling last"() {
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

        and: "the route is set ahead of the ordinary interceptors, which can fail, and the error handling is last"
        interceptors[1].class.simpleName == 'ObservationRouteHandler'
        interceptors.indexOf(plainInterceptor) > 1
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

    void "a request routed to an error handler keeps the route that failed on its observation"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder {
            "/foo/bar"(controller: 'foo', action: 'bar')
            "/handled"(controller: 'foo', action: 'fooBar')
            "500"(controller: 'foo', action: 'fooBar')
        })
        def handler = new UrlMappingsHandlerMapping(holder)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request
        request.requestURI = '/foo/bar'
        def context = new ServerRequestObservationContext(request, webRequest.response)
        request.setAttribute(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, context)

        when: 'the action is routed'
        def chain = handler.getHandler(request)
        chain.interceptorList.each { it.preHandle(request, webRequest.response, chain.handler) }

        then:
        context.pathPattern == '/foo/bar'

        when: 'it failed, and its error handler is routed, as a forward to it is'
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, 500)
        chain = handler.getHandler(request)
        chain.handler.configure(webRequest)
        chain.interceptorList.each { it.preHandle(request, webRequest.response, chain.handler) }

        then: 'the error handler answers, and the route is still the one that failed'
        webRequest.actionName == 'fooBar'
        context.pathPattern == '/foo/bar'

        when: 'the error handler forwards, which takes the status code off the request and leaves the exception'
        request.removeAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE)
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, new IllegalStateException('failed'))
        request.requestURI = '/handled'
        chain = handler.getHandler(request)
        chain.interceptorList.each { it.preHandle(request, webRequest.response, chain.handler) }

        then:
        context.pathPattern == '/foo/bar'
    }

    void "the match of a dispatch answers for the async dispatch that follows it, and not for a #dispatch to another action"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder {
            "/foo/bar"(controller: 'foo', action: 'bar')
            "/other"(controller: 'foo', action: 'fooBar')
        })
        def handler = new UrlMappingsHandlerMapping(holder)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request
        request.requestURI = '/foo/bar'
        def started = handler.getHandler(request).handler

        when: 'the action started async processing, and its result comes back on an async dispatch'
        request.dispatcherType = DispatcherType.ASYNC

        then:
        handler.getHandler(request).handler.is(started)

        when: 'the view of that result sends the request to another action'
        request.dispatcherType = dispatch
        request.requestURI = '/other'
        def info = handler.getHandler(request).handler

        then:
        info.actionName == 'fooBar'

        where:
        dispatch << [DispatcherType.FORWARD, DispatcherType.INCLUDE]
    }

    void "an action a page includes leaves the route of the page on its observation"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(FooController)
        grailsApplication.initialise()
        def holder = new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder {
            "/foo/bar"(controller: 'foo', action: 'bar')
            "/included"(controller: 'foo', action: 'fooBar')
        })
        def handler = new UrlMappingsHandlerMapping(holder)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request
        request.requestURI = '/foo/bar'
        def context = new ServerRequestObservationContext(request, webRequest.response)
        request.setAttribute(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, context)
        def chain = handler.getHandler(request)
        chain.interceptorList.each { it.preHandle(request, webRequest.response, chain.handler) }

        when: 'the page includes another action'
        request.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, '/included')
        request.requestURI = '/included'
        chain = handler.getHandler(request)
        chain.interceptorList.each { it.preHandle(request, webRequest.response, chain.handler) }

        then:
        context.pathPattern == '/foo/bar'
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }
}

@Artefact('Controller')
class FooController  {

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
}

/** Stands in for a data access exception, such as Spring's {@code DataAccessException}, that wraps a {@code SQLException}. */
class DataLayerException extends RuntimeException {

    DataLayerException(Throwable cause) {
        super('a data layer failure', cause)
    }
}
