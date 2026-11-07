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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification

import grails.core.GrailsApplication
import grails.testing.web.GrailsWebUnitTest
import grails.web.CamelCaseUrlConverter

/**
 * The handshake a browser makes, against what the plugin creates with {@code useDigestAuth}: the challenge from its
 * entry point, and the response sent through its whole filter chain, which is what grants the request.
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
        }
    }

    protected abstract Map digestConfig()

    void cleanup() {
        SecurityContextHolder.clearContext()
    }

    void cleanupSpec() {
        SpringSecurityUtils.resetSecurityConfig()
        ReflectionUtils.application = null
        SpringSecurityUtils.application = null
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

    static class StoredPasswordUserDetailsService implements UserDetailsService {

        GrailsApplication grailsApplication
        String password
        boolean enabled = true

        @Override
        UserDetails loadUserByUsername(String username) {
            new User(username, password, enabled, true, true, true, [new SimpleGrantedAuthority('ROLE_USER')])
        }
    }
}

class EncodedDigestAuthenticationSpec extends AbstractDigestAuthenticationSpec {

    protected Map digestConfig() {
        [realmName: REALM]
    }

    void 'by default what is stored is the hash a browser computes, and not the password'() {
        given:
            store("user:${REALM}:secret".md5())

        expect:
            signedInWith('secret') == 'user'
            signedInWith('wrong') == null
    }

    void 'a password stored as it was typed does not sign anyone in'() {
        given:
            store('secret')

        expect:
            signedInWith('secret') == null
    }

    void 'an account that is disabled is refused, however right the password'() {
        given:
            store("user:${REALM}:secret".md5(), false)

        expect:
            signedInWith('secret') == null
    }

    void 'a form cannot sign in a user with the hash stored'() {
        given:
            store("user:${REALM}:secret".md5())

        when:
            applicationContext.getBean('daoAuthenticationProvider')
                    .authenticate(UsernamePasswordAuthenticationToken.unauthenticated('user', 'secret'))

        then:
            thrown(BadCredentialsException)
    }
}

class CleartextDigestAuthenticationSpec extends AbstractDigestAuthenticationSpec {

    protected Map digestConfig() {
        [realmName: REALM, useCleartextPasswords: true]
    }

    void 'with cleartext passwords what the password encoder stores is what the filter hashes'() {
        given:
            store(applicationContext.getBean('passwordEncoder', PasswordEncoder).encode('secret'))

        expect:
            signedInWith('secret') == 'user'
            signedInWith('wrong') == null
    }

    void 'with cleartext passwords a form still signs a user in'() {
        given:
            store(applicationContext.getBean('passwordEncoder', PasswordEncoder).encode('secret'))

        when:
            Authentication signedIn = applicationContext.getBean('daoAuthenticationProvider')
                    .authenticate(UsernamePasswordAuthenticationToken.unauthenticated('user', 'secret'))

        then:
            signedIn.authenticated
    }
}
