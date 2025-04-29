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
package grails.web.mapping

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.http.HttpHeaders
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingInfo
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.core.io.ByteArrayResource
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Issue

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class RedirectWithParamsSpec extends AbstractUrlMappingsSpec {

    UrlMappings createUrlMappingHolder(String mappings) {
        ByteArrayResource res = new ByteArrayResource(mappings.bytes)
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        List mappingsList = evaluator.evaluateMappings(res)
        new DefaultUrlMappingsHolder(mappingsList)
    }

    @Issue('#10622, #10965')
    void "Test that redirects keeps params previously stored in the request only with the option enabled"() {
        given: 'a link generator'
        def linkGenerator = getLinkGenerator {
            "/example/my-action"(redirect: [uri: "/example/new-foo", keepParamsWhenRedirect: true])
        }
        def responseRedirector = new ResponseRedirector(linkGenerator)

        and: 'and the params for the redirect'
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.addParameter('foo', 'bar')
        webRequest.request.addParameter('baz', '123')

        and: 'mocking request and response'
        HttpServletRequest request = Mock(HttpServletRequest) { lookup() >> webRequest }
        HttpServletResponse response = Mock(HttpServletResponse)

        when: 'the response is redirected'
        responseRedirector.redirect(request, response, [uri: '/example/my-action', keepParamsWhenRedirect: true])

        then: 'the location header includes the params'
        1 * response.setStatus(302)
        1 * response.setHeader(HttpHeaders.LOCATION, "http://localhost/example/my-action?foo=bar&baz=123")

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }

    void "Test that keepParamsWhenRedirect flag redirects also merge params from UrlMappings config with original params"() {

        given: 'a link generator'
        UrlMappings urlMappings = createUrlMappingHolder('''
        mappings {
          '/images'(redirect:[uri:'/v1/images', permanent:true, keepParamsWhenRedirect: true, params: [test: '123']])
          "/v1/$controller"(namespace: "v1")
        }
        ''')
        LinkGenerator linkGenerator = new LinkGeneratorFactory().create(urlMappings)
        ResponseRedirector responseRedirector = new ResponseRedirector(linkGenerator)

        and: 'and the params for the redirect'
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.addParameter('foo', 'bar')

        and: 'mocking request and response'
        HttpServletRequest request = Mock(HttpServletRequest) { lookup() >> webRequest }
        HttpServletResponse response = Mock(HttpServletResponse)

        when:
        UrlMappingInfo[] matchedUrlMappings = urlMappings.matchAll("/images")

        then:
        matchedUrlMappings
        matchedUrlMappings.size() == 1
        matchedUrlMappings[0] instanceof DefaultUrlMappingInfo

        when: 'the response is redirected'
        DefaultUrlMappingInfo urlMappingInfo = (DefaultUrlMappingInfo) matchedUrlMappings[0]
        responseRedirector.redirect(request, response, (Map) urlMappingInfo.redirectInfo)

        then: 'the location header includes the params'
        1 * response.setStatus(301)
        1 * response.setHeader(HttpHeaders.LOCATION, "http://localhost/v1/images?test=123&foo=bar")
    }

    void "Test keepParamsWhenRedirect flag redirects alongside original request parameters"() {

        given: 'a link generator'
        UrlMappings urlMappings = createUrlMappingHolder('''
        mappings {
          '/images'(redirect:[uri:'/v1/images', permanent:true, keepParamsWhenRedirect: true])
          "/v1/$controller"(namespace: "v1")
        }
        ''')
        LinkGenerator linkGenerator = new LinkGeneratorFactory().create(urlMappings)
        ResponseRedirector responseRedirector = new ResponseRedirector(linkGenerator)

        and: 'and the params for the redirect'
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.addParameter('foo', 'bar')

        and: 'mocking request and response'
        HttpServletRequest request = Mock(HttpServletRequest) { lookup() >> webRequest }
        HttpServletResponse response = Mock(HttpServletResponse)

        when:
        UrlMappingInfo[] matchedUrlMappings = urlMappings.matchAll("/images")

        then:
        matchedUrlMappings
        matchedUrlMappings.size() == 1
        matchedUrlMappings[0] instanceof DefaultUrlMappingInfo

        when: 'the response is redirected'
        DefaultUrlMappingInfo urlMappingInfo = (DefaultUrlMappingInfo) matchedUrlMappings[0]
        responseRedirector.redirect(request, response, (Map) urlMappingInfo.redirectInfo)

        then: 'the location header includes the params'
        1 * response.setStatus(301)
        1 * response.setHeader(HttpHeaders.LOCATION, "http://localhost/v1/images?foo=bar")

        when:
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.addParameter('baz', '123')
        responseRedirector.redirect(request, response, (Map) urlMappingInfo.redirectInfo)

        then: 'the location header includes the params'
        1 * response.setStatus(301)
        1 * response.setHeader(HttpHeaders.LOCATION, "http://localhost/v1/images?baz=123")

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }

    @Issue('#10622, #10965')
    void "Test that redirects does not keeps params previously stored in the request because the option is not enabled"() {
        given: 'a link generator'
        def linkGenerator = getLinkGenerator {
            "/example/my-action"(redirect: [uri: "/example/new-foo"])
        }
        def responseRedirector = new ResponseRedirector(linkGenerator)

        and: 'and the params for the redirect'
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.addParameter('foo', 'bar')
        webRequest.request.addParameter('baz', '123')

        and: 'mocking request and response'
        HttpServletRequest request = Mock(HttpServletRequest) { lookup() >> webRequest }
        HttpServletResponse response = Mock(HttpServletResponse)

        when: 'the response is redirected'
        responseRedirector.redirect(request, response, [uri: '/example/my-action'])

        then: 'the location header includes the params'
        1 * response.setStatus(302)
        1 * response.setHeader(HttpHeaders.LOCATION, "http://localhost/example/my-action")

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }
}
