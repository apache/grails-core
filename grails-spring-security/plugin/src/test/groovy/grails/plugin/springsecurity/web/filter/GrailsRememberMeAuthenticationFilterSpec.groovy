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
package grails.plugin.springsecurity.web.filter

import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.RememberMeAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import spock.lang.Issue
import spock.lang.Unroll

import grails.plugin.springsecurity.AbstractUnitSpec

/**
 * A remember-me login redirects to the page that was asked for, through the authentication success handler, and Spring
 * Security then does not run the rest of the filter chain. That is right for a page that is fetched with a GET, and wrong
 * for a request that carries data (POST, PUT, PATCH, DELETE) or comes from Ajax: a redirect turns it into a GET, or into
 * the Ajax success URL, and the action is never run. Such a request goes on down the chain as the remembered user.
 */
@Issue(['#15879', '#15873', '#15874'])
class GrailsRememberMeAuthenticationFilterSpec extends AbstractUnitSpec {

    private final Authentication rememberMe = new RememberMeAuthenticationToken('key', 'admin', AuthorityUtils.createAuthorityList('ROLE_ADMIN'))

    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache()

    private GrailsRememberMeAuthenticationFilter filterWith(AuthenticationSuccessHandler handler, HttpSessionSecurityContextRepository repository = null) {
        RememberMeServices rememberMeServices = Stub(RememberMeServices) {
            autoLogin(_, _) >> rememberMe
        }
        AuthenticationManager authenticationManager = Stub(AuthenticationManager) {
            authenticate(_) >> rememberMe
        }
        GrailsRememberMeAuthenticationFilter filter = new GrailsRememberMeAuthenticationFilter(authenticationManager, rememberMeServices, requestCache)
        if (handler) {
            filter.authenticationSuccessHandler = handler
        }
        if (repository) {
            filter.securityContextRepository = repository
        }
        filter.afterPropertiesSet()
        filter
    }

    void 'a page fetched with a GET is redirected to by the success handler, and the filter chain stops'() {
        given:
        AuthenticationSuccessHandler handler = Mock(AuthenticationSuccessHandler)
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/secure/page')
        MockHttpServletResponse response = new MockHttpServletResponse()
        MockFilterChain chain = new MockFilterChain()

        when:
        filterWith(handler).doFilter(request, response, chain)

        then:
        1 * handler.onAuthenticationSuccess(request, response, rememberMe)
        chain.request == null

        and: 'the request is saved so that the handler can redirect to it'
        requestCache.getRequest(request, response)?.redirectUrl?.contains('/secure/page')
    }

    @Issue('#15877')
    void 'the remembered login is saved by the repository the filter was given, with its own settings'() {
        given: 'a repository that keeps the context under a key of its own'
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository()
        repository.springSecurityContextKey = 'PLUGIN_CONTEXT'
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/secure/page')
        MockHttpServletResponse response = new MockHttpServletResponse()

        when:
        filterWith(Mock(AuthenticationSuccessHandler), repository).doFilter(request, response, new MockFilterChain())

        then: 'the login is under that key, and not under the key of the default repository of Spring Security'
        request.session.getAttribute('PLUGIN_CONTEXT').authentication == rememberMe
        request.session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) == null
    }

    @Issue('#15877')
    @Unroll
    void 'with the login form at #formUrl, a GET of #scheme://#host:#port#uri is #outcome'() {
        given:
        AuthenticationSuccessHandler handler = Mock(AuthenticationSuccessHandler)
        MockHttpServletRequest request = new MockHttpServletRequest('GET', uri)
        request.contextPath = contextPath
        request.scheme = scheme
        request.serverName = host
        request.serverPort = port
        MockHttpServletResponse response = new MockHttpServletResponse()
        MockFilterChain chain = new MockFilterChain()
        GrailsRememberMeAuthenticationFilter filter = filterWith(handler)
        filter.loginFormUrl = formUrl

        when:
        filter.doFilter(request, response, chain)

        then: 'the form itself goes down the chain, any other page is redirected to by the handler'
        (chain.request != null) == isForm

        where:
        formUrl                                   | uri                 | contextPath | scheme  | host           | port || isForm
        '/login/auth'                             | '/app/login/auth'   | '/app'      | 'http'  | 'localhost'    | 8080 || true
        '/login/auth'                             | '/app/other'        | '/app'      | 'http'  | 'localhost'    | 8080 || false
        'https://example.test/login/auth'         | '/login/auth'       | ''          | 'https' | 'example.test' | 443  || true
        'https://example.test/app/login/auth'     | '/app/login/auth'   | '/app'      | 'https' | 'example.test' | 443  || true
        'https://example.test:8443/login/auth'    | '/login/auth'       | ''          | 'https' | 'example.test' | 8443 || true
        'http://example.test/login/auth'          | '/login/auth'       | ''          | 'http'  | 'example.test' | 80   || true
        'https://Example.Test/login/auth'         | '/login/auth'       | ''          | 'https' | 'example.test' | 443  || true
        'https://example.test/login/auth?x=1'     | '/login/auth'       | ''          | 'https' | 'example.test' | 443  || true
        'https://example.test/login/auth'         | '/login/auth'       | ''          | 'https' | 'other.test'   | 443  || true
        'https://example.test/login/auth'         | '/login/auth'       | ''          | 'http'  | 'app-internal' | 8080 || true
        'https://example.test/login/auth'         | '/login/auth'       | ''          | 'https' | 'example.test' | 8443 || true
        '/login/auth?lang=en'                     | '/login/auth'       | ''          | 'http'  | 'localhost'    | 8080 || true
        '/login/auth?lang=en'                     | '/app/login/auth'   | '/app'      | 'http'  | 'localhost'    | 8080 || true
        '/login/auth#top'                         | '/login/auth'       | ''          | 'http'  | 'localhost'    | 8080 || true
        '/login/auth?lang=en'                     | '/login/other'      | ''          | 'http'  | 'localhost'    | 8080 || false
        'https://example.test/app/../login/auth'  | '/login/auth'       | ''          | 'https' | 'example.test' | 443  || true
        'https://example.test/login/./auth'       | '/login/auth'       | ''          | 'https' | 'example.test' | 443  || true
        '/app/../login/auth'                      | '/login/auth'       | ''          | 'http'  | 'localhost'    | 8080 || true
        '/login/../other'                         | '/login/auth'       | ''          | 'http'  | 'localhost'    | 8080 || false
        'https://example.test/login/auth'         | '/other'            | ''          | 'https' | 'example.test' | 443  || false
        'https://example.test/login/auth'         | '/app/login/auth'   | '/app'      | 'https' | 'example.test' | 443  || false
        outcome = isForm ? 'the login form, passed down the chain' : 'another page, redirected to'
    }

    @Issue('#15877')
    @Issue('#15877')
    @Unroll
    void 'with the login form at #formUrl, a GET of #uri is #outcome, whatever way the dot segments of the address are spelled'() {
        given:
        AuthenticationSuccessHandler handler = Mock(AuthenticationSuccessHandler)
        MockHttpServletRequest request = new MockHttpServletRequest('GET', uri)
        request.contextPath = contextPath
        MockHttpServletResponse response = new MockHttpServletResponse()
        MockFilterChain chain = new MockFilterChain()
        GrailsRememberMeAuthenticationFilter filter = filterWith(handler)
        filter.loginFormUrl = formUrl

        when:
        filter.doFilter(request, response, chain)

        then: 'a browser reads %2e as a dot, so the path it asks for has no dot segment, however the address spells it'
        (chain.request != null) == isForm

        where:
        formUrl                                          | uri                   | contextPath || isForm
        '/app/%2e%2e/login/auth'                         | '/login/auth'         | ''          || true
        '/app/%2E%2E/login/auth'                         | '/login/auth'         | ''          || true
        '/app/.%2e/login/auth'                           | '/login/auth'         | ''          || true
        '/app/%2e./login/auth'                           | '/login/auth'         | ''          || true
        '/app/%2E./login/auth'                           | '/login/auth'         | ''          || true
        '/login/%2e/auth'                                | '/login/auth'         | ''          || true
        '/login/%2E/auth'                                | '/login/auth'         | ''          || true
        '/login/auth/%2e'                                | '/login/auth/'        | ''          || true
        '/login/auth/x/%2e%2e'                           | '/login/auth/'        | ''          || true
        '/login/auth/x/..'                               | '/login/auth/'        | ''          || true
        '/login/auth/.'                                  | '/login/auth/'        | ''          || true
        '/../login/auth'                                 | '/login/auth'         | ''          || true
        '/%2e%2e/login/auth'                             | '/login/auth'         | ''          || true
        '/%2e%2e/%2e%2e/login/auth'                      | '/login/auth'         | ''          || true
        '/a/b/../../login/auth'                          | '/login/auth'         | ''          || true
        '/a/b/%2e%2e/%2e%2e/login/auth'                  | '/login/auth'         | ''          || true
        '/a/%2e%2e/b/%2e%2e/login/auth'                  | '/login/auth'         | ''          || true
        '/app/%2e%2e/login/auth'                         | '/app/login/auth'     | '/app'      || true
        '/x/%2e%2e/login/auth?lang=en'                   | '/app/login/auth'     | '/app'      || true
        'https://example.test/app/%2e%2e/login/auth'     | '/login/auth'         | ''          || true
        'https://example.test/app/%2E%2E/login/auth'     | '/login/auth'         | ''          || true
        'https://example.test/login/%2e/auth'            | '/login/auth'         | ''          || true
        'https://example.test/%2e%2e/login/auth'         | '/login/auth'         | ''          || true
        'https://example.test/a/b/%2e%2e/%2e%2e/login/auth' | '/login/auth'      | ''          || true
        'https://example.test/app/%2e%2e/login/auth#top' | '/login/auth'         | ''          || true
        '/login/%2e%2e/other'                            | '/login/auth'         | ''          || false
        '/login/auth/%2e%2e/other'                       | '/login/auth'         | ''          || false
        '/app/%2e%2e/login/auth'                         | '/app/login/auth'     | ''          || false
        '/login/%2e/auth'                                | '/login/other'        | ''          || false
        'https://example.test/login/%2e%2e/other'        | '/login/auth'         | ''          || false
        '/login/%2f../auth'                              | '/login/auth'         | ''          || false
        outcome = isForm ? 'the login form, passed down the chain' : 'another page, redirected to'
    }

    void 'the request for the login form goes on down the filter chain, so the controller can send a remembered user to the full login page'() {
        given:
        AuthenticationSuccessHandler handler = Mock(AuthenticationSuccessHandler)
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/app/login/auth')
        request.contextPath = '/app'
        MockHttpServletResponse response = new MockHttpServletResponse()
        MockFilterChain chain = new MockFilterChain()
        GrailsRememberMeAuthenticationFilter filter = filterWith(handler)
        filter.loginFormUrl = '/login/auth'

        when:
        filter.doFilter(request, response, chain)

        then:
        0 * handler._
        chain.request != null
        SecurityContextHolder.context.authentication == rememberMe
        !requestCache.getRequest(request, response)
    }

    @Unroll
    void 'a #method request goes on down the filter chain as the remembered user, with no redirect'() {
        given:
        AuthenticationSuccessHandler handler = Mock(AuthenticationSuccessHandler)
        MockHttpServletRequest request = new MockHttpServletRequest(method, '/secure/save')
        MockHttpServletResponse response = new MockHttpServletResponse()
        MockFilterChain chain = new MockFilterChain()

        when:
        filterWith(handler).doFilter(request, response, chain)

        then:
        0 * handler._
        chain.request != null
        SecurityContextHolder.context.authentication == rememberMe
        !requestCache.getRequest(request, response)

        where:
        method << ['POST', 'PUT', 'PATCH', 'DELETE']
    }

    void 'an Ajax request goes on down the filter chain as the remembered user, with no redirect'() {
        given:
        AuthenticationSuccessHandler handler = Mock(AuthenticationSuccessHandler)
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/secure/data')
        request.addHeader('X-Requested-With', 'XMLHttpRequest')
        MockHttpServletResponse response = new MockHttpServletResponse()
        MockFilterChain chain = new MockFilterChain()

        when:
        filterWith(handler).doFilter(request, response, chain)

        then:
        0 * handler._
        chain.request != null
        SecurityContextHolder.context.authentication == rememberMe
    }

    void 'without a success handler a GET goes on down the filter chain'() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/secure/page')
        MockHttpServletResponse response = new MockHttpServletResponse()
        MockFilterChain chain = new MockFilterChain()

        when:
        filterWith(null).doFilter(request, response, chain)

        then:
        chain.request != null
        SecurityContextHolder.context.authentication == rememberMe
    }
}
