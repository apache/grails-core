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
package grails.plugin.springsecurity

import jakarta.servlet.FilterChain

import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import spock.lang.Specification

import grails.core.GrailsApplication
import grails.testing.web.GrailsWebUnitTest
import grails.web.CamelCaseUrlConverter

/**
 * The handshake a browser makes, against what the plugin creates with {@code useDigestAuth}: the challenge from its
 * entry point, and the response sent through its whole filter chain, which is what grants the request. And what the
 * rest of the application still does with Digest authentication on: a form sign-in, a Basic one, and the user that
 * {@code spring.security.user.*} describes.
 */
abstract class AbstractDigestAuthenticationSpec extends Specification implements GrailsWebUnitTest {

    protected static final String REALM = 'Test Realm'

    Set<String> getIncludePlugins() {
        ['core', 'springSecurityCore'] as Set
    }

    Closure doWithSpring() {
        { ->
            grailsUrlConverter(CamelCaseUrlConverter)
        }
    }

    Closure doWithConfig() {
        { config ->
            config.grails.plugin.springsecurity.securityConfigType = 'InterceptUrlMap'
            config.grails.plugin.springsecurity.interceptUrlMap = [[pattern: '/secure', access: ['isAuthenticated()']]]
            config.grails.plugin.springsecurity.useDigestAuth = true
            config.grails.plugin.springsecurity.userDetailsServiceBeanClass = StoredPasswordUserDetailsService
            config.grails.plugin.springsecurity.digest = digestConfig()
            config.spring.security.user.name = 'admin'
            config.spring.security.user.password = 'admin-secret'
        }
    }

    protected abstract Map digestConfig()

    /** What the application stores for a user with this password. */
    protected abstract String storedFor(String password)

    void cleanup() {
        SecurityContextHolder.clearContext()
    }

    void cleanupSpec() {
        SpringSecurityUtils.resetSecurityConfig()
        ReflectionUtils.application = null
        SpringSecurityUtils.application = null
    }

    void 'a browser signs a user in with their password, and not with another'() {
        given:
            store(storedFor('secret'))

        expect:
            signedInWith('secret') == 'user'
            signedInWith('wrong') == null
    }

    void 'an account that is disabled is refused, however right the password'() {
        given:
            store(storedFor('secret'), false)

        expect:
            signedInWith('secret') == null
    }

    void 'a form signs a user in with their password, and not with another'() {
        given:
            store(storedFor('secret'))

        expect:
            signedInThroughForm('user', 'secret') == 'user'
            signedInThroughForm('user', 'wrong') == null
    }

    void 'what Basic authentication hands the authentication manager signs a user in with their password'() {
        given:
            store(storedFor('secret'))

        when:
            Authentication signedIn = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated('user', 'secret'))

        then:
            signedIn.authenticated
            signedIn.name == 'user'

        when:
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated('user', 'wrong'))

        then:
            thrown(BadCredentialsException)
    }

    void 'the password encoder stays as configured'() {
        when:
            PasswordEncoder encoder = applicationContext.getBean('passwordEncoder', PasswordEncoder)

        then:
            encoder instanceof DelegatingPasswordEncoder
            encoder.encode('secret').startsWith('{bcrypt}')
    }

    void 'the user spring.security.user describes signs in through a form'() {
        expect:
            signedInThroughForm('admin', 'admin-secret') == 'admin'
            signedInThroughForm('admin', 'wrong') == null
    }

    protected AuthenticationManager getAuthenticationManager() {
        applicationContext.getBean('authenticationManager', AuthenticationManager)
    }

    protected void store(String password, boolean enabled = true) {
        def users = applicationContext.getBean('userDetailsService', StoredPasswordUserDetailsService)
        users.password = password
        users.enabled = enabled
    }

    /** Who a browser sending this password reaches the application as, if it reaches it. */
    protected String signedInWith(String password) {
        def challenge = new MockHttpServletResponse()
        applicationContext.getBean('digestAuthenticationEntryPoint')
                .commence(new MockHttpServletRequest(), challenge, new BadCredentialsException('no credentials yet'))
        String header = challenge.getHeader('WWW-Authenticate')
        String realm = (header =~ /realm="([^"]*)"/)[0][1]
        String nonce = (header =~ /nonce="([^"]*)"/)[0][1]

        String ha1 = "user:${realm}:${password}".md5()
        String ha2 = 'GET:/secure'.md5()
        String response = "${ha1}:${nonce}:00000001:cnonce:auth:${ha2}".md5()

        def request = new MockHttpServletRequest('GET', '/secure')
        request.servletPath = '/secure'
        request.addHeader('Authorization', "Digest username=\"user\", realm=\"${realm}\", nonce=\"${nonce}\", " +
                "uri=\"/secure\", qop=auth, nc=00000001, cnonce=\"cnonce\", response=\"${response}\"")
        String reached = null
        FilterChain application = { req, res -> reached = SecurityContextHolder.context.authentication.name } as FilterChain
        SecurityContextHolder.clearContext()
        applicationContext.getBean('springSecurityFilterChainProxy').doFilter(request, new MockHttpServletResponse(), application)
        reached
    }

    /** Who a login form sending this username and password signs in, if anyone. */
    protected String signedInThroughForm(String username, String password) {
        def request = new MockHttpServletRequest('POST', '/login/authenticate')
        request.servletPath = '/login/authenticate'
        request.addParameter('username', username)
        request.addParameter('password', password)
        SecurityContextHolder.clearContext()
        applicationContext.getBean('springSecurityFilterChainProxy')
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain())
        def context = request.getSession(false)?.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
        ((SecurityContext) context)?.authentication?.name
    }

    static class StoredPasswordUserDetailsService implements UserDetailsService {

        GrailsApplication grailsApplication
        String password
        boolean enabled = true

        @Override
        UserDetails loadUserByUsername(String username) {
            if (username != 'user') {
                throw new UsernameNotFoundException(username)
            }
            new User(username, password, enabled, true, true, true, [new SimpleGrantedAuthority('ROLE_USER')])
        }
    }
}

class EncodedDigestAuthenticationSpec extends AbstractDigestAuthenticationSpec {

    protected Map digestConfig() {
        [realmName: REALM]
    }

    protected String storedFor(String password) {
        "user:${REALM}:${password}".md5()
    }

    void 'a password stored as it was typed does not sign anyone in'() {
        given:
            store('secret')

        expect:
            signedInWith('secret') == null
    }

    void 'the hash stored does not sign anyone in through a form'() {
        given:
            String hash = storedFor('secret')
            store(hash)

        expect:
            signedInThroughForm('user', hash) == null
    }

    void 'the hash stored does not sign anyone in through Basic authentication'() {
        given:
            String hash = storedFor('secret')
            store(hash)

        when:
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated('user', hash))

        then:
            thrown(BadCredentialsException)
    }
}

class CleartextDigestAuthenticationSpec extends AbstractDigestAuthenticationSpec {

    protected Map digestConfig() {
        [realmName: REALM, useCleartextPasswords: true]
    }

    protected String storedFor(String password) {
        password
    }

    void 'a password stored by the password encoder does not sign anyone in'() {
        given:
            store(applicationContext.getBean('passwordEncoder', PasswordEncoder).encode('secret'))

        expect:
            signedInWith('secret') == null
    }
}
