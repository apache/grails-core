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

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

import com.fasterxml.jackson.annotation.JsonView
import groovy.json.JsonSlurper

import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.core.MethodParameter
import org.springframework.core.env.MapPropertySource
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.async.CallableProcessingInterceptor
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest
import org.springframework.web.context.request.async.WebAsyncUtils
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

import grails.artefact.Artefact
import grails.config.Settings
import grails.util.GrailsWebMockUtil
import grails.web.HyphenatedUrlConverter
import grails.web.UrlConverter
import grails.web.mapping.AbstractUrlMappingsSpec
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingInfo
import org.grails.core.DefaultGrailsControllerClass
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import spock.util.concurrent.PollingConditions

/**
 * What a controller action returns is written as Spring MVC writes what a handler method returns, where Spring MVC
 * writes it as the response, and, with {@code grails.controllers.responseBody}, which the features set unless they say
 * otherwise, any value of an action annotated {@code @ResponseBody}.
 */
class ActionReturnValuesSpec extends AbstractUrlMappingsSpec {

    private GenericWebApplicationContext context

    private GrailsWebRequest webRequest

    private UrlMappingsInfoHandlerAdapter adapter

    void setup() {
        context = mvcContext((Settings.CONTROLLERS_RESPONSE_BODY): true)
        webRequest = GrailsWebMockUtil.bindMockWebRequest(context)
        webRequest.currentRequest.requestURI = '/returnValues/action'
        webRequest.currentRequest.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        adapter = new UrlMappingsInfoHandlerAdapter()
        adapter.applicationContext = context
    }

    void cleanup() {
        context?.close()
    }

    void 'without grails.controllers.responseBody, the Grails 9 default, an action annotated @ResponseBody renders its view, as before'() {
        given:
        def withoutSetting = mvcContext([:])
        adapter = new UrlMappingsInfoHandlerAdapter()
        adapter.applicationContext = withoutSetting
        webRequest.currentRequest.addParameter('id', '7')

        when:
        def modelAndView = handle('show')

        then:
        modelAndView.viewName == 'show'
        modelAndView.model == [id: 7]
        response.contentAsString == ''

        when: 'a ResponseEntity, which Spring MVC writes whatever an action is annotated with'
        handle('entity')

        then:
        response.status == 201
        json() == [title: 'Dune']

        cleanup:
        withoutSetting.close()
    }

    void 'a ResponseEntity is written with its status, headers and body, and the advice for the controller'() {
        when:
        def modelAndView = handle('entity')

        then:
        modelAndView == null
        response.status == 201
        response.getHeader(HttpHeaders.LOCATION) == '/books/1'
        response.getHeader('X-Advised') == 'ReturnValuesController'
        response.contentType == MediaType.APPLICATION_JSON_VALUE
        json() == [title: 'Dune']
    }

    void 'a ProblemDetail is written as a problem, with its status'() {
        when:
        handle('problem')

        then:
        response.status == 422
        response.contentType == MediaType.APPLICATION_PROBLEM_JSON_VALUE
        json().detail == 'invalid'
        json().instance == '/returnValues/action'
    }

    void 'a ProblemDetail an action of an error page returns is written, whatever the status of the response'() {
        given:
        response.status = 500

        when:
        handle('problem')

        then:
        response.status == 422
        response.contentType == MediaType.APPLICATION_PROBLEM_JSON_VALUE
        json().detail == 'invalid'
    }

    void 'an ErrorResponse is written as its problem, with its status and headers'() {
        when:
        handle('errorResponse')

        then:
        response.status == 409
        response.getHeader('X-Conflict') == 'taken'
        response.contentType == MediaType.APPLICATION_PROBLEM_JSON_VALUE
        json().status == 409
    }

    void 'HttpHeaders are written as the headers of an empty response'() {
        when:
        handle('headers')

        then:
        response.status == 200
        response.getHeader('X-Total') == '3'
        response.contentAsString == ''
    }

    void 'the value of an action annotated @ResponseBody is written as the body, with the advice for the controller'() {
        when:
        handle('body')

        then:
        response.status == 200
        response.contentType == MediaType.APPLICATION_JSON_VALUE
        response.getHeader('X-Advised') == 'ReturnValuesController'
        json() == [title: 'Dune', isbn: '978-0441013593']
    }

    void 'the @JsonView of an action annotated @ResponseBody selects the properties written'() {
        when:
        handle('summary')

        then:
        json() == [title: 'Dune']
    }

    void 'an action annotated @ResponseBody that takes parameters is written as the body'() {
        given:
        webRequest.currentRequest.addParameter('id', '7')

        when:
        handle('show')

        then:
        json() == [id: 7]
    }

    void 'the @ResponseStatus of an action annotated @ResponseBody sets the status'() {
        when:
        handle('created')

        then:
        response.status == 201
        json() == [created: true]
    }

    void 'the reason of an @ResponseStatus sends the status as an error'() {
        when:
        handle('retired')

        then:
        response.status == 410
        response.errorMessage == 'retired'
        response.contentAsString == ''
    }

    void 'any value of an action of a controller annotated @ResponseBody is written as the body, without advice for another controller'() {
        when:
        handle('index', ReturnValuesBodyController)

        then:
        response.contentType == MediaType.APPLICATION_JSON_VALUE
        response.getHeader('X-Advised') == null
        json() == [[title: 'Dune', isbn: '978-0441013593']]
    }

    void 'a ModelAndView an action annotated @ResponseBody returns is the view Spring MVC renders'() {
        when:
        def modelAndView = handle('bookView')

        then:
        modelAndView.viewName == 'books'
        modelAndView.model == [title: 'Dune']
        response.contentAsString == ''
    }

    void 'an action annotated @ResponseBody that its URL names with the hyphenated URL converter is written as the body'() {
        when:
        handle('show-book', ReturnValuesController, new HyphenatedUrlConverter())

        then:
        json() == [title: 'Dune']
    }

    void 'a map an action not annotated @ResponseBody returns is the model of its view, as before'() {
        when:
        def modelAndView = handle('model')

        then:
        modelAndView.viewName == 'model'
        modelAndView.model == [title: 'Dune']
        response.contentAsString == ''
    }

    void 'a value an action annotated @ResponseBody returns once it rendered the response is left to Grails'() {
        when:
        handle('rendered')

        then:
        response.contentAsString == 'done'
        response.getHeader('X-Advised') == null
    }

    void 'the value of an action that forwarded the request is left to Grails'() {
        given:
        request.setAttribute(GrailsApplicationAttributes.FORWARD_ISSUED, true)

        expect:
        handle('entity') == null
        response.contentAsString == ''
        response.status == 200
    }

    void 'the value of an action that processes the request asynchronously already is left to Grails'() {
        given:
        request.asyncSupported = true
        def asyncManager = WebAsyncUtils.getAsyncManager(request)
        asyncManager.asyncWebRequest = new StandardServletAsyncWebRequest(request, response)
        asyncManager.startDeferredResultProcessing(new DeferredResult<Object>())

        when:
        handle('body')

        then:
        response.contentAsString == ''
        response.getHeader('X-Advised') == null
    }

    void 'the value a CompletableFuture of an action annotated @ResponseBody completes with is written as the action\'s'() {
        given:
        request.asyncSupported = true
        def future = new CompletableFuture<Object>()
        context.getBean(ReturnValuesController).pending = future

        when:
        def modelAndView = handle('later')

        then: 'with the timeout and the interceptors of Spring MVC\'s handler adapter'
        modelAndView == null
        WebAsyncUtils.getAsyncManager(request).concurrentHandlingStarted
        request.asyncContext.timeout == 1234
        request.getAttribute(ReturnValuesAsyncConfigurer.DEFERRED) == true
        webRequest.responseRendered
        response.contentAsString == ''

        when: 'the future completes, and the request is dispatched again'
        future.complete([title: 'Dune'])
        new PollingConditions(timeout: 5).eventually {
            assert WebAsyncUtils.getAsyncManager(request).hasConcurrentResult()
        }
        request.setAttribute(WebUtils.ASYNC_REQUEST_URI_ATTRIBUTE, '/returnValues/later')
        modelAndView = handle('later')

        then:
        modelAndView == null
        response.contentType == MediaType.APPLICATION_JSON_VALUE
        response.getHeader('X-Advised') == 'ReturnValuesController'
        json() == [title: 'Dune']
    }

    void 'an exception a CompletableFuture of an action completes with is thrown when the request is dispatched again'() {
        given:
        request.asyncSupported = true
        def future = new CompletableFuture<Object>()
        context.getBean(ReturnValuesController).pending = future

        when:
        handle('later')
        future.completeExceptionally(new IllegalStateException('failed'))
        new PollingConditions(timeout: 5).eventually {
            assert WebAsyncUtils.getAsyncManager(request).hasConcurrentResult()
        }
        request.setAttribute(WebUtils.ASYNC_REQUEST_URI_ATTRIBUTE, '/returnValues/later')
        handle('later')

        then:
        IllegalStateException failure = thrown()
        failure.message == 'failed'
    }

    void 'a ResponseEntity of a StreamingResponseBody is streamed with the executor and interceptors of Spring MVC\'s handler adapter'() {
        given:
        request.asyncSupported = true

        when:
        handle('download')
        new PollingConditions(timeout: 5).eventually {
            assert WebAsyncUtils.getAsyncManager(request).hasConcurrentResult()
        }

        then:
        response.contentType.startsWith(MediaType.TEXT_PLAIN_VALUE)
        response.contentAsString.startsWith('streamed by return-values-')
        request.getAttribute(ReturnValuesAsyncConfigurer.INTERCEPTED) == true

        and: 'the configuration the handler adapter has, which the application\'s WebMvcConfigurer made once'
        context.getBean(ReturnValuesAsyncConfigurer).configured == 1
    }

    void 'without Spring MVC\'s handler adapter a ResponseEntity is left to Grails, as before'() {
        given:
        def withoutMvc = new GenericWebApplicationContext(new MockServletContext())
        def linkGenerator = Stub(LinkGenerator)
        withoutMvc.registerBean(LinkGenerator, { linkGenerator })
        withoutMvc.registerBean(ReturnValuesController.name, ReturnValuesController)
        withoutMvc.refresh()
        adapter = new UrlMappingsInfoHandlerAdapter()
        adapter.applicationContext = withoutMvc

        when:
        def modelAndView = handle('entity')

        then:
        modelAndView == null
        response.status == 200
        response.contentAsString == ''

        cleanup:
        withoutMvc.close()
    }

    private GenericWebApplicationContext mvcContext(Map<String, Object> properties) {
        def mvc = new GenericWebApplicationContext(new MockServletContext())
        mvc.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        AnnotationConfigUtils.registerAnnotationConfigProcessors(mvc)
        mvc.registerBean(DelegatingWebMvcConfiguration)
        mvc.registerBean(ReturnValuesAdvice)
        mvc.registerBean(ReturnValuesAsyncConfigurer)
        def linkGenerator = Stub(LinkGenerator)
        mvc.registerBean(LinkGenerator, { linkGenerator })
        mvc.registerBean(ReturnValuesController.name, ReturnValuesController)
        mvc.registerBean(ReturnValuesBodyController.name, ReturnValuesBodyController)
        mvc.refresh()
        mvc
    }

    private Object handle(String action, Class<?> controller = ReturnValuesController, UrlConverter urlConverter = null) {
        def controllerClass = new DefaultGrailsControllerClass(controller)
        if (urlConverter != null) {
            controllerClass.registerUrlConverter(urlConverter)
        }
        def info = new GrailsControllerUrlMappingInfo(controllerClass, Stub(UrlMappingInfo) {
            getActionName() >> action
        })
        adapter.handle(request, response, info)
    }

    private Object json() {
        new JsonSlurper().parseText(response.contentAsString)
    }

    private getRequest() {
        webRequest.currentRequest
    }

    private getResponse() {
        webRequest.currentResponse
    }
}

interface Summary {
}

class ReturnValuesBook {

    @JsonView(Summary)
    String title

    String isbn
}

@Artefact('Controller')
class ReturnValuesController {

    CompletableFuture<Object> pending

    def entity() {
        ResponseEntity.created(URI.create('/books/1')).body([title: 'Dune'])
    }

    def problem() {
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, 'invalid')
    }

    def errorResponse() {
        new ErrorResponseException(HttpStatus.CONFLICT).tap {
            headers.add('X-Conflict', 'taken')
        }
    }

    def headers() {
        new HttpHeaders().tap {
            add('X-Total', '3')
        }
    }

    @ResponseBody
    def body() {
        new ReturnValuesBook(title: 'Dune', isbn: '978-0441013593')
    }

    @ResponseBody
    @JsonView(Summary)
    def summary() {
        new ReturnValuesBook(title: 'Dune', isbn: '978-0441013593')
    }

    @ResponseBody
    def show(Long id) {
        [id: id]
    }

    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    def created() {
        [created: true]
    }

    @ResponseBody
    @ResponseStatus(code = HttpStatus.GONE, reason = 'retired')
    def retired() {
        [retired: true]
    }

    def model() {
        [title: 'Dune']
    }

    @ResponseBody
    def bookView() {
        new ModelAndView('books', [title: 'Dune'])
    }

    @ResponseBody
    def showBook() {
        [title: 'Dune']
    }

    @ResponseBody
    def rendered() {
        render 'done'
        [ignored: true]
    }

    @ResponseBody
    def later() {
        pending
    }

    ResponseEntity<StreamingResponseBody> download() {
        ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body({ OutputStream out ->
            out << "streamed by ${Thread.currentThread().name}".toString()
        } as StreamingResponseBody)
    }
}

@ResponseBody
@Artefact('Controller')
class ReturnValuesBodyController {

    def index() {
        [new ReturnValuesBook(title: 'Dune', isbn: '978-0441013593')]
    }
}

class ReturnValuesAsyncConfigurer implements WebMvcConfigurer {

    static final String INTERCEPTED = ReturnValuesAsyncConfigurer.name + '.INTERCEPTED'

    static final String DEFERRED = ReturnValuesAsyncConfigurer.name + '.DEFERRED'

    int configured

    @Override
    void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configured++
        configurer.taskExecutor = new SimpleAsyncTaskExecutor('return-values-')
        configurer.defaultTimeout = 1234
        configurer.registerCallableInterceptors(new CallableProcessingInterceptor() {
            @Override
            <T> void preProcess(NativeWebRequest request, Callable<T> task) {
                request.setAttribute(INTERCEPTED, true, RequestAttributes.SCOPE_REQUEST)
            }
        })
        configurer.registerDeferredResultInterceptors(new DeferredResultProcessingInterceptor() {
            @Override
            <T> void preProcess(NativeWebRequest request, DeferredResult<T> deferredResult) {
                request.setAttribute(DEFERRED, true, RequestAttributes.SCOPE_REQUEST)
            }
        })
    }
}

@ControllerAdvice(assignableTypes = ReturnValuesController)
class ReturnValuesAdvice implements ResponseBodyAdvice<Object> {

    @Override
    boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        true
    }

    @Override
    Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                           Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                           ServerHttpResponse response) {
        response.headers.add('X-Advised', returnType.containingClass.simpleName)
        body
    }
}
