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
package grails.plugin.springsecurity.web

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.web.util.ServletRequestPathUtils
import spock.lang.Specification

class SavedRequestMatcherSpec extends Specification {

    private static final List<String> DEFAULT_PATTERNS = new ConfigSlurper()
            .parse(new GroovyClassLoader(SavedRequestMatcherSpec.classLoader).loadClass('DefaultSecurityConfig'))
            .security.requestCache.ignoredPatterns as List<String>

    void 'the request cache does not save #path, with the default patterns'() {
        expect:
        savedRequest(path, new SavedRequestMatcher(ignoredPatterns: DEFAULT_PATTERNS)) == null

        where:
        path << [
                '/favicon.ico',
                '/favicon.png',
                '/apple-touch-icon.png',
                '/apple-touch-icon-precomposed.png',
                '/apple-touch-icon-152x152.png',
                '/manifest.json',
                '/manifest.webmanifest',
                '/browserconfig.xml',
                '/.well-known/appspecific/com.chrome.devtools.json'
        ]
    }

    void 'the request cache saves #path, with the default patterns'() {
        expect:
        savedRequest(path, new SavedRequestMatcher(ignoredPatterns: DEFAULT_PATTERNS)).redirectUrl == "http://localhost$path"

        where:
        path << ['/', '/book/show/1', '/images/favicon.ico', '/manifest', '/manifest.json/1', '/.well-known/security.txt']
    }

    void 'the patterns are relative to the context path'() {
        given:
        def request = request('/manifest.json')
        request.contextPath = '/app'
        request.requestURI = '/app/manifest.json'

        expect:
        !new SavedRequestMatcher(ignoredPatterns: ['/manifest.json']).matches(request)
    }

    void 'the application names its own patterns'() {
        given:
        def matcher = new SavedRequestMatcher(ignoredPatterns: ['/poll/**'])

        expect:
        savedRequest('/poll/status', matcher) == null
        savedRequest('/manifest.json', matcher) != null
    }

    void 'no patterns save every request'() {
        expect:
        savedRequest('/manifest.json', new SavedRequestMatcher(ignoredPatterns: [])) != null
        savedRequest('/manifest.json', new SavedRequestMatcher()) != null
    }

    void 'a request parsed for Spring MVC keeps its parsed path'() {
        given:
        def request = request('/manifest.json')
        def parsed = ServletRequestPathUtils.parseAndCache(request)

        when:
        def matched = new SavedRequestMatcher(ignoredPatterns: ['/manifest.json']).matches(request)

        then:
        !matched
        ServletRequestPathUtils.getParsedRequestPath(request).is(parsed)
    }

    private static savedRequest(String path, SavedRequestMatcher matcher) {
        def cache = new HttpSessionRequestCache(requestMatcher: matcher, matchingRequestParameterName: null)
        def request = request(path)
        def response = new MockHttpServletResponse()
        cache.saveRequest(request, response)
        cache.getRequest(request, response)
    }

    private static MockHttpServletRequest request(String path) {
        new MockHttpServletRequest('GET', path)
    }
}
