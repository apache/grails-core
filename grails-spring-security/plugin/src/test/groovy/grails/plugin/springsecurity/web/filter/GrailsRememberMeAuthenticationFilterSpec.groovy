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
    void 'the remembered login is kept in the session, so the page redirected to does not log in again and loop'() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/secure/page')
        MockHttpServletResponse response = new MockHttpServletResponse()

        when:
        filterWith(Mock(AuthenticationSuccessHandler), new HttpSessionSecurityContextRepository()).doFilter(request, response, new MockFilterChain())

        then: 'the next request of that session finds the remembered authentication and the filter has nothing to do'
        request.session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY).authentication == rememberMe
    }

    @Issue('#15877')
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
