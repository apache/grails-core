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

import grails.util.GrailsWebMockUtil
import groovy.transform.Generated
import org.grails.plugins.web.interceptors.InterceptorArtefactHandler
import org.grails.web.mapping.ForwardUrlMappingInfo
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.servlet.http.HttpServletRequest
import java.lang.reflect.Method

/**
 * @author graemerocher
 */
class InterceptorSpec extends Specification {

    void cleanup() {
        RequestContextHolder.setRequestAttributes(null)
    }

    void "Test the default interceptor mappings"() {
        given: "A test interceptor"
        def i = new TestInterceptor()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        HttpServletRequest request = webRequest.request
        when: "The current request is for a controller called test"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test"))

        then: "We match"
        i.doesMatch()

        when: "The current request is for a controller called test and action called bar"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", actionName: "bar"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for another controller"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "other"))
        then: "We don't match"
        !i.doesMatch()
    }

    void "Test the match all interceptor mappings"() {
        given: "A test interceptor"
        def i = new Test3Interceptor()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request

        when: "The current request is for a controller called test"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for a controller called test and action called bar"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", actionName: "bar"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for another controller"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "other"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for an excluded controller controller"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo"))
        then: "We don't match"
        !i.doesMatch()
    }

    void "Test the match specific controller interceptor mappings"() {
        given: "A test interceptor"
        def i = new Test2Interceptor()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request

        when: "The current request is for a controller called foo"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for a controller called foo and action called bar"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo", actionName: "bar"))
        then: "We don't match"
        !i.doesMatch()

        when: "The current request is for another controller action"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo", actionName: "stuff"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for another controller action and method"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo", actionName: "stuff", httpMethod: "POST"))
        then: "We match"
        i.doesMatch()

    }

    void "Test the multiple match with specific controller interceptor mapping and the exclude"() {
        given: "A test interceptor"
        def i = new Test5Interceptor()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request

        when: "The current request is for controller called foo"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for a controller called foo and action called bar"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo", actionName: "bar"))
        then: "We don't match"
        !i.doesMatch()

        when: "The current request is for a controller called test and action called bar"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", actionName: "bar"))
        then: "We match"
        i.doesMatch()
    }

    void "Test the multiple match with specific namespace controller action interceptor mapping and the exclude"() {
        given: "A test interceptor"
        def i = new Test6Interceptor()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request

        when: "The current request is for controller called foo"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo"))
        then: "We don't match"
        !i.doesMatch()

        when: "The current request is for controller called foo and namespace v1"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(namespace: "v1", controllerName: "foo"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for a controller called foo and action called bar and namespace v1"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(namespace: "v1", controllerName: "foo", actionName: "bar"))
        then: "We don't match"
        !i.doesMatch()

        when: "The current request is for a controller called test and action called bar"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", actionName: "bar"))
        then: "We don't match"
        !i.doesMatch()

        when: "The current request is for a controller called test and action called bar and namespace v1"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(namespace: "v1", controllerName: "test", actionName: "bar"))
        then: "We match"
        i.doesMatch()
    }

    void "Test the match all interceptor mappings exception an exact controller action pair"() {
        given: "A test interceptor"
        def i = new Test4Interceptor()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request

        when: "The current request is for a controller called test"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for a controller called test and action called bar"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", actionName: "bar"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for a controller called test and action called bar"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo", actionName: "bar"))
        then: "We match"
        !i.doesMatch()

        when: "The current request is for another controller"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "other"))
        then: "We match"
        i.doesMatch()

        when: "The current request is for an excluded controller controller"
        clearMatch(i, request)
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "foo"))
        then: "We don't match"
        i.doesMatch()
    }

    void "Test match with http method"() {
        given: "A test interceptor"
        def i = new TestMethodInterceptor()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), new MockHttpServletRequest(httpMethod, ""), new MockHttpServletResponse())
        def request = webRequest.request

        when:
        "The http method of the current request is ${httpMethod}"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", action: "save"))

        then:
        "We match: ${shouldMatch}"
        i.doesMatch() == shouldMatch

        where:
        httpMethod | shouldMatch
        'POST'     | true
        'GET'      | false
    }

    void "Test match with uri no context path"() {
        given: "A test interceptor"
        def i = new TestUriInterceptor()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), new MockHttpServletRequest("", requestUri), new MockHttpServletResponse())
        def request = webRequest.request

        when:
        "The uri of the current request is ${requestUri}"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", action: "save"))

        then:
        "We match: ${shouldMatch}"
        i.doesMatch() == shouldMatch

        where:
        requestUri | shouldMatch
        '/bar'     | true
        '/bar/x'   | true
        '/fooBar'  | false
        '/foo'     | true
        '/foo/x'   | false
        '/foo/bar' | true
    }

    void "Test match with uri and context path"() {
        given: "A test interceptor"
        def i = new TestUriInterceptor()
        def mockRequest = new MockHttpServletRequest("", requestUri)
        mockRequest.setContextPath('/grails')
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), mockRequest, new MockHttpServletResponse())

        def request = webRequest.request

        when:
        "The uri of the current request is ${requestUri}"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", action: "save"))

        then:
        "We match: ${shouldMatch}"
        i.doesMatch() == shouldMatch

        where:
        requestUri        | shouldMatch
        '/grails/bar'     | true
        '/grails/bar/x'   | true
        '/grails/fooBar'  | false
        '/grails/foo'     | true
        '/grails/foo/x'   | false
        '/grails/foo/bar' | true
    }

    void "Test match with uri and context path with an interceptor that defines the context path"() {
        given: "A test interceptor"
        def i = new TestContextUriInterceptor()
        def mockRequest = new MockHttpServletRequest("", requestUri)
        mockRequest.setContextPath('/grails')
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), mockRequest, new MockHttpServletResponse())

        def request = webRequest.request

        when:
        "The uri of the current request is ${requestUri}"
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: "test", action: "save"))

        then:
        "We match: ${shouldMatch}"
        i.doesMatch() == shouldMatch

        where:
        requestUri        | shouldMatch
        '/grails/bar'     | true
        '/grails/bar/x'   | true
        '/grails/fooBar'  | false
        '/grails/foo'     | true
        '/grails/foo/x'   | false
        '/grails/foo/bar' | true
    }

    @Issue('10857')
    void "Test match excluding uri and with context path and interceptor without context path"() {
        given: "A test interceptor"
        def i = new TestExcludeUriWithoutContextPathInterceptor()
        def mockRequest = new MockHttpServletRequest("", requestUri)
        mockRequest.setContextPath('/grails')
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), mockRequest, new MockHttpServletResponse())
        def request = webRequest.request

        expect:
        "We match: ${shouldMatch}"
        i.doesMatch() == shouldMatch

        where:
        requestUri            | shouldMatch
        '/grails/mgmt/health' | false
        '/grails'             | true
        '/grails/foo'         | true
        '/grails/foo/x'       | true
    }

    @Unroll
    @Issue('10857')
    void "Test match excluding uri and with context path and interceptor with context path"() {
        given: "A test interceptor"
        def i = new TestExcludeUriWithContextPathInterceptor()
        def mockRequest = new MockHttpServletRequest("", requestUri)
        mockRequest.setContextPath('/grails')
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), mockRequest, new MockHttpServletResponse())
        def request = webRequest.request

        expect:
        "We match: ${shouldMatch}"
        i.doesMatch() == shouldMatch

        where:
        requestUri            | shouldMatch
        '/grails/mgmt/health' | false
        '/grails'             | true
        '/grails/foo'         | true
        '/grails/foo/x'       | true
    }

    void clearMatch(i, HttpServletRequest request) {
        request.removeAttribute(i.getClass().name + InterceptorArtefactHandler.MATCH_SUFFIX)
    }

    void "test that all Interceptor trait methods are marked as Generated"() {
        expect: "all Interceptor methods are marked as Generated on implementation class"
        Interceptor.getMethods().each { Method traitMethod ->
            assert TestGeneratedAnnotations.class.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }
    }
}

class TestInterceptor implements Interceptor {
    @Override
    boolean before() {
        return false
    }

    @Override
    boolean after() {
        return false
    }

    @Override
    void afterView() {

    }
}

class Test2Interceptor implements Interceptor {
    Test2Interceptor() {
        match(controller: "foo")
                .excludes(action: "bar")
    }
}

class Test3Interceptor implements Interceptor {
    Test3Interceptor() {
        matchAll()
                .excludes(controller: "foo")
    }
}

class Test4Interceptor implements Interceptor {
    Test4Interceptor() {
        matchAll()
                .excludes(controller: "foo", action: "bar")
    }
}

class Test5Interceptor implements Interceptor {
    Test5Interceptor() {
        match(controller: "foo")
                .excludes(action: "bar")
        match(controller: "test")
    }
}

class Test6Interceptor implements Interceptor {
    Test6Interceptor() {
        match(namespace: "v1", controller: "foo")
                .excludes(action: "bar")
        match(namespace: "v1", controller: "test")
    }
}

class TestMethodInterceptor implements Interceptor {
    TestMethodInterceptor() {
        match(method: 'POST')
    }
}

class TestUriInterceptor implements Interceptor {
    TestUriInterceptor() {
        match(uri: '/bar/**')
        match(uri: '/foo')
        match(uri: '/foo/bar')
    }
}

class TestContextUriInterceptor implements Interceptor {
    TestContextUriInterceptor() {
        match(uri: '/grails/bar/**')
        match(uri: '/grails/foo')
        match(uri: '/grails/foo/bar')
    }
}

class TestExcludeUriWithoutContextPathInterceptor implements Interceptor {
    TestExcludeUriWithoutContextPathInterceptor() {
        matchAll().excludes(uri: "/mgmt/*")
    }
}

class TestExcludeUriWithContextPathInterceptor implements Interceptor {
    TestExcludeUriWithContextPathInterceptor() {
        matchAll().excludes(uri: "/grails/mgmt/*")
    }
}

class TestGeneratedAnnotations implements Interceptor {

}