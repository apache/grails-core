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
package grails.artefact

import grails.interceptors.Matcher
import grails.util.GrailsWebMockUtil
import org.grails.plugins.web.interceptors.GrailsInterceptorHandlerInterceptorAdapter
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author graemerocher
 */
class GrailsInterceptorHandlerInterceptorAdapterSpec extends Specification{

    void cleanup() {
        RequestContextHolder.setRequestAttributes(null)
    }

    void "Test that an interceptor can cancel request processing"() {
        given:"An interceptor"
            def i = new MyInterceptor()
            def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
            adapter.setInterceptors([i] as Interceptor[])


        when:"The adapter prehandle is executed"
            def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        then:"The prehandle is true"
            adapter.preHandle(webRequest.request, webRequest.response, this)

        when:"A condition is met for exclusion"
            webRequest.request.setAttribute("something", "test")
        then:"The prehandle is false"
            !adapter.preHandle(webRequest.request, webRequest.response, this)

    }

    void "Test that an interceptor can cancel view rendering"() {
        given:"An interceptor"
        def i = new MyInterceptor()
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.setInterceptors([i] as Interceptor[])


        when:"The adapter prehandle is executed"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def modelAndView = new ModelAndView()
        adapter.preHandle(webRequest.request, webRequest.response, this)
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then:"The prehandle is true"
            modelAndView.model.foo == 'bar'
            modelAndView.viewName== 'foo'

        when:"A condition is met for exclusion"
        webRequest.request.setAttribute("bar", "test")
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then:"The prehandle is false"
        modelAndView.viewName == null
    }

    void "Test an execution order of interceptors"() {
        given: "An interceptor"
            def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
            adapter.setInterceptors([new HighestInterceptor(), new LowestInterceptor()] as Interceptor[])

        when: "The adapter preHandle is executed"
            def webRequest = GrailsWebMockUtil.bindMockWebRequest()
            def modelAndView = new ModelAndView()
            adapter.preHandle(webRequest.request, webRequest.response, this)

        then: "The interceptors are executed in the order of highest priority"
            webRequest.request.getAttribute('executed') == ['highest before', 'lowest before']

        when: "The adapter postHandle is executed"
            webRequest.request.setAttribute('executed', null)
            adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then: "The interceptors are executed in the order of lowest priority"
            webRequest.request.getAttribute('executed') == ['lowest after', 'highest after']

        when: "The adapter afterCompletion is executed"
            webRequest.request.setAttribute('executed', null)
            adapter.afterCompletion(webRequest.request, webRequest.response, this, null)

        then: "The interceptors are executed in the order of lowest priority"
            webRequest.request.getAttribute('executed') == ['lowest afterView', 'highest afterView']
    }

    @Issue('https://github.com/apache/grails-core/issues/9548')
    void "Test the exception is set in the request if thrown"() {
        given:"An interceptor"
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.setInterceptors([new MyInterceptor()] as Interceptor[])
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        expect:
        !webRequest.request.getAttribute(Matcher.THROWABLE)

        when:
        adapter.afterCompletion(webRequest.request, webRequest.response, this, new Exception("foo"))

        then:
        webRequest.request.getAttribute(Matcher.THROWABLE) instanceof Exception
    }
}
class MyInterceptor implements Interceptor {

    MyInterceptor() {
        matchAll()
    }

    @Override
    boolean before() {
        if(request.getAttribute("something")) {
            return false
        }
        return true
    }

    @Override
    boolean after() {
        if(request.getAttribute("bar")) {
            return false
        }
        else {
            model = [foo:"bar"]
            view = "foo"
            return true
        }
    }

    @Override
    void afterView() {
       if(request.getAttribute("bar")) {
           throw throwable
       }
    }
}
class HighestInterceptor implements Interceptor {

    int order = HIGHEST_PRECEDENCE

    HighestInterceptor() {
        matchAll()
    }

    @Override
    boolean before() {
        executed << 'highest before'
        true
    }

    @Override
    boolean after() {
        executed << 'highest after'
        true
    }

    @Override
    void afterView() {
        executed << 'highest afterView'
    }

    def getExecuted() {
        def executed = request.getAttribute('executed')
        if (!executed) {
            executed = []
            request.setAttribute('executed', executed)
        }
        executed
    }
}
class LowestInterceptor implements Interceptor {

    int order = LOWEST_PRECEDENCE

    LowestInterceptor() {
        matchAll()
    }

    @Override
    boolean before() {
        executed << 'lowest before'
        true
    }

    @Override
    boolean after() {
        executed << 'lowest after'
        true
    }

    @Override
    void afterView() {
        executed << 'lowest afterView'
    }

    def getExecuted() {
        def executed = request.getAttribute('executed')
        if (!executed) {
            executed = []
            request.setAttribute('executed', executed)
        }
        executed
    }
}
