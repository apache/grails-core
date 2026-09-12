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
import grails.web.mapping.UrlMappingInfo
import groovy.transform.Generated
import org.grails.plugins.web.interceptors.InterceptorArtefactHandler
import org.grails.web.mapping.ForwardUrlMappingInfo
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.grails.web.util.WebUtils
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

    @Unroll
    void "Test URI matching uses the path that URL mappings route on: #requestUri"() {
        given: "an interceptor matching /admin/**"
        def interceptor = new TestAdminUriInterceptor()
        bindRequest(requestUri)

        expect: "the request is matched as URL mappings would dispatch it"
        interceptor.doesMatch() == shouldMatch

        where:
        requestUri                | shouldMatch | reason
        '/admin/deleteUser'       | true        | 'plain path'
        '/admin;x=1/deleteUser'   | true        | 'matrix parameters are not part of the path'
        '/%61dmin/deleteUser'     | true        | 'percent escapes are decoded'
        '/admin%3Bx=1/deleteUser' | false       | 'an encoded semicolon is a literal character, so this is a different path'
        '/%2561dmin/deleteUser'   | false       | 'decoded once, this is /%61dmin/deleteUser, not /admin/deleteUser'
    }

    void "Test a URI exclude only fires for the dispatched path"() {
        given: "an interceptor for everything except /health"
        def interceptor = new TestExcludeHealthUriInterceptor()
        bindRequest('/health%3Bx')

        expect: "the interceptor still runs because /health;x is dispatched to a different path than /health"
        interceptor.doesMatch()
    }

    @Unroll
    void "Test URI matching during an include matches the included path: #pattern"() {
        given: "a request for /admin/dashboard that is including /stats/index"
        def interceptor = new TestPatternUriInterceptor(pattern)
        def request = bindRequest('/admin/dashboard')
        request.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, '/stats/index')

        expect: "the included path is matched, which is also the path the include is dispatched on"
        interceptor.doesMatch() == shouldMatch

        where:
        pattern     | shouldMatch
        '/admin/**' | false
        '/stats/**' | true
    }

    @Unroll
    void "Test a malformed percent escape is matched undecoded instead of failing: #requestUri"() {
        given: "an interceptor matching /admin/**"
        def interceptor = new TestAdminUriInterceptor()
        bindRequest(requestUri)

        expect:
        interceptor.doesMatch() == shouldMatch

        where:
        requestUri    | shouldMatch
        '/foo%'       | false
        '/admin/foo%' | true
        '/admin/%zz'  | true
    }

    @Unroll
    void "Test URI decoding uses the request character encoding like URL mappings do: #characterEncoding"() {
        given: "an interceptor matching the decoded UTF-8 form of the path"
        def interceptor = new TestPatternUriInterceptor('/caf\u00e9')
        def request = bindRequest('/caf%C3%A9')
        request.characterEncoding = characterEncoding

        expect:
        interceptor.doesMatch() == shouldMatch

        where:
        characterEncoding | shouldMatch
        'UTF-8'           | true
        'ISO-8859-1'      | false
    }

    @Unroll
    void "Test match with uri and excludes with uri under a context path: #requestUri"() {
        given: "match(uri: '/api/**').excludes(uri: '/api/health') deployed under /app"
        def interceptor = new TestApiExcludingHealthUriInterceptor()
        bindRequest(requestUri, '/app')

        expect:
        interceptor.doesMatch() == shouldMatch

        where:
        requestUri        | shouldMatch
        '/app/api/orders' | true
        '/app/api/health' | false
        '/app/other'      | false
    }

    @Unroll
    void "Test match with uri and a controller exclude under a context path: #requestUri"() {
        given: "match(uri: '/api/**').excludes(controller: 'health') deployed under /app"
        def interceptor = new TestApiExcludingHealthControllerInterceptor()
        def request = bindRequest(requestUri, '/app')
        request.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, new ForwardUrlMappingInfo(controllerName: controller))

        expect:
        interceptor.doesMatch() == shouldMatch

        where:
        requestUri        | controller | shouldMatch
        '/app/api/orders' | 'orders'   | true
        '/app/api/health' | 'health'   | false
        '/app/other'      | 'other'    | false
    }

    @Unroll
    void "Test URI patterns are matched against the path within the application only: #pattern"() {
        given: "a request for /app/save deployed under /app"
        def interceptor = new TestPatternUriInterceptor(pattern)
        bindRequest('/app/save', '/app')

        expect:
        interceptor.doesMatch() == shouldMatch

        where:
        pattern     | shouldMatch | reason
        '/save'     | true        | 'application-relative pattern'
        '/*'        | true        | 'application-relative wildcard'
        '/app/save' | true        | 'context-prefixed pattern accepted for backwards compatibility'
        '/*/*'      | false       | 'the context path is not part of the matched path'
        '/app/*/*'  | false       | 'nor is it re-added when the pattern carries it'
    }

    void "Test a custom Matcher receives the canonical path through the default context path method"() {
        given: "an interceptor using a Matcher that only implements the three argument doesMatch"
        def matcher = new RecordingMatcher()
        def interceptor = new TestCustomMatcherInterceptor(matcher)
        bindRequest('/app/%61dmin;x=1/users', '/app')

        expect:
        interceptor.doesMatch()
        matcher.uri == '/admin/users'
        matcher.method == 'GET'
    }

    void "Test a custom Matcher can receive the context path"() {
        given: "an interceptor using a Matcher that overrides the four argument doesMatch"
        def matcher = new ContextPathRecordingMatcher()
        def interceptor = new TestCustomMatcherInterceptor(matcher)
        bindRequest('/app/admin/users', '/app')

        expect:
        interceptor.doesMatch()
        matcher.uri == '/admin/users'
        matcher.contextPath == '/app'
    }

    void "Test a custom Matcher that does not match is honoured"() {
        given:
        def matcher = new RecordingMatcher(result: false)
        def interceptor = new TestCustomMatcherInterceptor(matcher)
        bindRequest('/admin/users')

        expect:
        !interceptor.doesMatch()
        matcher.uri == '/admin/users'
    }

    private MockHttpServletRequest bindRequest(String requestUri, String contextPath = '') {
        def mockRequest = new MockHttpServletRequest('GET', requestUri)
        mockRequest.contextPath = contextPath
        GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), mockRequest, new MockHttpServletResponse())
        mockRequest
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

class TestAdminUriInterceptor implements Interceptor {
    TestAdminUriInterceptor() {
        match(uri: '/admin/**')
    }
}

class TestPatternUriInterceptor implements Interceptor {
    TestPatternUriInterceptor(String pattern) {
        match(uri: pattern)
    }
}

class TestExcludeHealthUriInterceptor implements Interceptor {
    TestExcludeHealthUriInterceptor() {
        matchAll().excludes(uri: '/health')
    }
}

class TestApiExcludingHealthUriInterceptor implements Interceptor {
    TestApiExcludingHealthUriInterceptor() {
        match(uri: '/api/**').excludes(uri: '/api/health')
    }
}

class TestApiExcludingHealthControllerInterceptor implements Interceptor {
    TestApiExcludingHealthControllerInterceptor() {
        match(uri: '/api/**').excludes(controller: 'health')
    }
}

class TestCustomMatcherInterceptor implements Interceptor {
    TestCustomMatcherInterceptor(Matcher matcher) {
        matchers << matcher
    }
}

class RecordingMatcher implements Matcher {
    String uri
    String method
    boolean result = true

    @Override
    boolean doesMatch(String uri, UrlMappingInfo info) {
        doesMatch(uri, info, null)
    }

    @Override
    boolean doesMatch(String uri, UrlMappingInfo info, String method) {
        this.uri = uri
        this.method = method
        result
    }

    @Override
    Matcher matches(Map arguments) { this }

    @Override
    Matcher matchAll() { this }

    @Override
    Matcher excludes(Map arguments) { this }

    @Override
    Matcher except(Map arguments) { this }

    @Override
    Matcher excludes(Closure<Boolean> condition) { this }

    @Override
    boolean isExclude() { false }
}

class ContextPathRecordingMatcher extends RecordingMatcher {
    String contextPath

    @Override
    boolean doesMatch(String uri, UrlMappingInfo info, String method, String contextPath) {
        this.contextPath = contextPath
        doesMatch(uri, info, method)
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
        matchAll().excludes(uri: '/grails/mgmt/*')
    }
}

class TestGeneratedAnnotations implements Interceptor {

}
