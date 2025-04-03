package org.grails.web.mapping.mvc

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.Action
import grails.web.HyphenatedUrlConverter
import grails.web.mapping.AbstractUrlMappingsSpec
import org.grails.web.mapping.DefaultUrlMappingData
import org.grails.web.mapping.DefaultUrlMappingInfo
import org.grails.web.util.WebUtils
import org.springframework.web.context.request.RequestContextHolder
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
