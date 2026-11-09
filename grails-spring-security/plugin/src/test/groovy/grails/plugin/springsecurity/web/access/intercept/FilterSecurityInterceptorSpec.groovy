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
package grails.plugin.springsecurity.web.access.intercept

import jakarta.servlet.FilterChain
import jakarta.servlet.Servlet

import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDecisionManager
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.SecurityConfig
import org.springframework.security.access.vote.RoleVoter
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.savedrequest.NullRequestCache

import grails.plugin.springsecurity.AbstractUnitSpec
import grails.plugin.springsecurity.access.vote.AuthenticatedVetoableDecisionManager
import grails.plugin.springsecurity.web.access.expression.WebExpressionVoter

class FilterSecurityInterceptorSpec extends AbstractUnitSpec {

    void 'doFilter delegates to the chain for public invocations when allowed'() {
        given:
        def interceptor = new FilterSecurityInterceptor(
            rejectPublicInvocations: false,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> null
            }
        )
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
    }

    void 'doFilter consults the access decision manager before continuing the chain'() {
        given:
        def authentication = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'password', AuthorityUtils.createAuthorityList('ROLE_USER'))
        SecurityContextHolder.context.authentication = authentication
        def attributes = [new SecurityConfig('ROLE_USER')]
        def interceptor = new FilterSecurityInterceptor(
            rejectPublicInvocations: false,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> attributes
            }
        )
        interceptor.accessDecisionManager = Mock(AccessDecisionManager) {
            1 * decide(authentication, _ as FilterInvocation, attributes)
        }
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
    }

    void 'doFilter rejects missing authentication before consulting the decision manager'() {
        given:
        def interceptor = new FilterSecurityInterceptor(
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> [new SecurityConfig('ROLE_USER')]
            },
            accessDecisionManager: Mock(AccessDecisionManager)
        )
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        def exception = thrown(AuthenticationCredentialsNotFoundException)
        exception.message == 'An Authentication object was not found in the SecurityContext'
        0 * interceptor.accessDecisionManager._
        0 * chain._
    }

    void 'what a filter left unauthenticated is authenticated before the access decision'() {
        given:
        def left = UsernamePasswordAuthenticationToken.unauthenticated('user', 'stored')
        def authenticated = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'stored', AuthorityUtils.createAuthorityList('ROLE_USER'))
        SecurityContextHolder.context.authentication = left
        def interceptor = interceptorFor(Mock(AuthenticationManager), Mock(AccessDecisionManager))
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        1 * interceptor.authenticationManager.authenticate(left) >> authenticated

        then:
        1 * interceptor.accessDecisionManager.decide(authenticated, _ as FilterInvocation, _)

        then:
        1 * chain.doFilter(request, response)
        SecurityContextHolder.context.authentication.is(authenticated)
    }

    void 'what the authentication manager refuses never reaches the access decision'() {
        given:
        SecurityContextHolder.context.authentication = UsernamePasswordAuthenticationToken.unauthenticated('user', 'stored')
        def interceptor = interceptorFor(
            Stub(AuthenticationManager) { authenticate(_) >> { throw new BadCredentialsException('refused') } },
            Mock(AccessDecisionManager))
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        thrown(BadCredentialsException)
        0 * interceptor.accessDecisionManager._
        0 * chain._
    }

    void 'what is authenticated already is left alone unless alwaysReauthenticate is set'() {
        given:
        def authentication = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'password', AuthorityUtils.createAuthorityList('ROLE_USER'))
        SecurityContextHolder.context.authentication = authentication
        def interceptor = interceptorFor(Mock(AuthenticationManager), Mock(AccessDecisionManager))
        interceptor.alwaysReauthenticate = always

        when:
        interceptor.doFilter(request, response, Stub(FilterChain))

        then:
        times * interceptor.authenticationManager.authenticate(authentication) >> authentication

        where:
        always | times
        false  | 0
        true   | 1
    }

    void 'with alwaysReauthenticate a user whose credentials were erased once signed in reaches a secured page'() {
        given: 'a user signed in through an authentication manager that erases the credentials, as it does by default'
        def provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(user('secret')))
        ProviderManager manager = Spy(constructorArgs: [[provider]])
        Authentication signedIn = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated('user', 'secret'))
        SecurityContextHolder.context.authentication = signedIn
        def interceptor = securedBy(manager)
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then: 'what was signed in is used as it is, since it has nothing left to be authenticated with'
        0 * manager.authenticate(_)
        1 * chain.doFilter(request, response)
        signedIn.credentials == null
        SecurityContextHolder.context.authentication.is(signedIn)
    }

    void 'with alwaysReauthenticate a user who still holds their password is authenticated again on every request'() {
        given: 'a user signed in through an authentication manager that keeps the credentials'
        def users = new InMemoryUserDetailsManager(user('secret'))
        def manager = new ProviderManager([new DaoAuthenticationProvider(users)])
        manager.eraseCredentialsAfterAuthentication = false
        Authentication signedIn = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated('user', 'secret'))
        SecurityContextHolder.context.authentication = signedIn
        def interceptor = securedBy(manager)
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        !SecurityContextHolder.context.authentication.is(signedIn)
        SecurityContextHolder.context.authentication.authenticated

        when: 'the password is changed before the next request'
        users.updateUser(user('changed'))
        def next = new MockHttpServletRequest('GET', '/secure')
        interceptor.doFilter(next, response, chain)

        then:
        thrown(BadCredentialsException)
        0 * chain._
    }

    void 'what a filter left unauthenticated is authenticated when alwaysReauthenticate is #always'() {
        given:
        def manager = new ProviderManager([new DaoAuthenticationProvider(new InMemoryUserDetailsManager(user('secret')))])
        SecurityContextHolder.context.authentication = UsernamePasswordAuthenticationToken.unauthenticated('user', 'secret')
        def interceptor = securedBy(manager)
        interceptor.alwaysReauthenticate = always
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)
        SecurityContextHolder.context.authentication.authenticated
        SecurityContextHolder.context.authentication.name == 'user'

        where:
        always << [false, true]
    }

    void 'what a filter left unauthenticated without credentials is refused when alwaysReauthenticate is #always'() {
        given:
        def manager = new ProviderManager([new DaoAuthenticationProvider(new InMemoryUserDetailsManager(user('secret')))])
        SecurityContextHolder.context.authentication = UsernamePasswordAuthenticationToken.unauthenticated('user', null)
        def interceptor = securedBy(manager)
        interceptor.alwaysReauthenticate = always
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        thrown(BadCredentialsException)
        0 * chain._

        where:
        always << [false, true]
    }

    void 'a request without security attributes authenticates nothing, even with alwaysReauthenticate'() {
        given:
        def left = UsernamePasswordAuthenticationToken.unauthenticated('user', 'secret')
        SecurityContextHolder.context.authentication = left
        AuthenticationManager manager = Mock()
        def interceptor = new FilterSecurityInterceptor(
            authenticationManager: manager,
            alwaysReauthenticate: true,
            rejectPublicInvocations: false,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> null
            }
        )
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        0 * manager._
        1 * chain.doFilter(request, response)
        SecurityContextHolder.context.authentication.is(left)
    }

    private FilterSecurityInterceptor interceptorFor(AuthenticationManager manager, AccessDecisionManager decisions) {
        new FilterSecurityInterceptor(
            authenticationManager: manager,
            accessDecisionManager: decisions,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> [new SecurityConfig('ROLE_USER')]
            }
        )
    }

    /** An interceptor that re-authenticates, in front of a URL a user with {@code ROLE_USER} may reach. */
    private FilterSecurityInterceptor securedBy(AuthenticationManager manager) {
        new FilterSecurityInterceptor(
            authenticationManager: manager,
            alwaysReauthenticate: true,
            accessDecisionManager: new AuthenticatedVetoableDecisionManager([new WebExpressionVoter(), new RoleVoter()]),
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> [new SecurityConfig('ROLE_USER')]
            }
        )
    }

    private static UserDetails user(String password) {
        User.withUsername('user').password("{noop}${password}").roles('USER').build()
    }

    void 'secured filter chain responds with #status for #scenario'() {
        given:
        if (role != null) {
            SecurityContextHolder.context.authentication = UsernamePasswordAuthenticationToken.authenticated(
                'user', 'password', AuthorityUtils.createAuthorityList(role))
        }
        def interceptor = new FilterSecurityInterceptor(
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> [new SecurityConfig('ROLE_USER')]
            },
            accessDecisionManager: new AuthenticatedVetoableDecisionManager([
                new WebExpressionVoter(), new RoleVoter()
            ])
        )
        def exceptionTranslationFilter = new ExceptionTranslationFilter(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new NullRequestCache())
        Servlet servlet = Mock()
        def chain = new MockFilterChain(servlet, exceptionTranslationFilter, interceptor)

        when:
        chain.doFilter(request, response)

        then:
        response.status == status
        invocations * servlet.service(request, response)

        where:
        scenario                 | role         | status | invocations
        'missing authentication' | null         | 401    | 0
        'authorized user'        | 'ROLE_USER'  | 200    | 1
        'unauthorized user'      | 'ROLE_OTHER' | 403    | 0
    }

    void 'doFilter rejects public invocations when configured'() {
        given:
        def interceptor = new FilterSecurityInterceptor(
            rejectPublicInvocations: true,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> null
            },
            accessDecisionManager: Mock(AccessDecisionManager)
        )
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        thrown(IllegalStateException)
        0 * interceptor.accessDecisionManager._
        0 * chain._
    }

    void 'doFilter does not continue the chain after access is denied'() {
        given:
        SecurityContextHolder.context.authentication = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'password', AuthorityUtils.NO_AUTHORITIES)
        def interceptor = new FilterSecurityInterceptor(
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> [new SecurityConfig('ROLE_USER')]
            },
            accessDecisionManager: Stub(AccessDecisionManager) {
                decide(_, _, _) >> { throw new AccessDeniedException('Access is denied') }
            }
        )
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        thrown(AccessDeniedException)
        0 * chain._
    }

    void 'doFilter does not continue the chain when the decision manager commits the response'() {
        given:
        SecurityContextHolder.context.authentication = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'password', AuthorityUtils.createAuthorityList('ROLE_USER'))
        def interceptor = new FilterSecurityInterceptor(
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> [new SecurityConfig('ROLE_USER')]
            },
            accessDecisionManager: Stub(AccessDecisionManager) {
                decide(_, _, _) >> { response.sendError(403) }
            }
        )
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)

        then:
        response.committed
        0 * chain._
    }

    void 'doFilter authorizes a request once and passes its later dispatches through'() {
        given:
        def authentication = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'password', AuthorityUtils.createAuthorityList('ROLE_USER'))
        SecurityContextHolder.context.authentication = authentication
        def attributes = [new SecurityConfig('ROLE_USER')]
        def interceptor = new FilterSecurityInterceptor(
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> attributes
            },
            accessDecisionManager: Mock(AccessDecisionManager)
        )
        FilterChain chain = Mock()

        when: 'the request is dispatched'
        interceptor.doFilter(request, response, chain)

        then: 'it is authorized'
        1 * interceptor.accessDecisionManager.decide(authentication, _ as FilterInvocation, attributes)
        1 * chain.doFilter(request, response)

        when: 'the same request is dispatched again, as it is to render its error page'
        interceptor.doFilter(request, response, chain)

        then: 'it is not authorized again'
        0 * interceptor.accessDecisionManager._
        1 * chain.doFilter(request, response)
    }

    void 'doFilter passes the later dispatches of a public request through without authorizing them'() {
        given:
        SecurityContextHolder.context.authentication = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'password', AuthorityUtils.NO_AUTHORITIES)
        def interceptor = new FilterSecurityInterceptor(
            rejectPublicInvocations: false,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >>> [null, [new SecurityConfig('ROLE_USER')]]
            },
            accessDecisionManager: Mock(AccessDecisionManager)
        )
        FilterChain chain = Mock()

        when: 'a request without a rule is dispatched'
        interceptor.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response)

        when: 'the same request is dispatched to a URL that has a rule'
        interceptor.doFilter(request, response, chain)

        then: 'the rule is not applied'
        0 * interceptor.accessDecisionManager._
        1 * chain.doFilter(request, response)
    }

    void 'doFilter does not authorize the error dispatch of a request it denied'() {
        given:
        SecurityContextHolder.context.authentication = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'password', AuthorityUtils.NO_AUTHORITIES)
        def interceptor = new FilterSecurityInterceptor(
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> [new SecurityConfig('ROLE_USER')]
            },
            accessDecisionManager: Mock(AccessDecisionManager)
        )
        FilterChain chain = Mock()

        when: 'the request is denied'
        interceptor.doFilter(request, response, chain)

        then:
        1 * interceptor.accessDecisionManager.decide(_, _, _) >> { throw new AccessDeniedException('Access is denied') }
        thrown(AccessDeniedException)
        0 * chain._

        when: 'the error page for the denial is dispatched on the same request'
        interceptor.doFilter(request, response, chain)

        then: 'the dispatch is not authorized'
        0 * interceptor.accessDecisionManager._
        1 * chain.doFilter(request, response)
    }

    void 'doFilter authorizes every dispatch of a request when observeOncePerRequest is false'() {
        given:
        def authentication = UsernamePasswordAuthenticationToken.authenticated(
            'user', 'password', AuthorityUtils.createAuthorityList('ROLE_USER'))
        SecurityContextHolder.context.authentication = authentication
        def attributes = [new SecurityConfig('ROLE_USER')]
        def interceptor = new FilterSecurityInterceptor(
            observeOncePerRequest: false,
            securityMetadataSource: Stub(FilterInvocationSecurityMetadataSource) {
                getAttributes(_ as FilterInvocation) >> attributes
            },
            accessDecisionManager: Mock(AccessDecisionManager)
        )
        FilterChain chain = Mock()

        when:
        interceptor.doFilter(request, response, chain)
        interceptor.doFilter(request, response, chain)

        then:
        2 * interceptor.accessDecisionManager.decide(authentication, _ as FilterInvocation, attributes)
        2 * chain.doFilter(request, response)
    }
}
