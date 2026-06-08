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
package grails.plugin.springsecurity.rest

import grails.plugin.springsecurity.rest.authentication.RestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.reader.TokenReader
import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import spock.lang.Specification
import spock.lang.Subject

import jakarta.servlet.FilterChain

@Subject(RestTokenValidationFilter)
class RestTokenValidationFilterUnitSpec extends Specification {

    def filter = new RestTokenValidationFilter(active: true)

    def request  = new GrailsMockHttpServletRequest()
    def response = new MockHttpServletResponse()
    def chain = new MockFilterChain()

    def setup() {
        filter.authenticationSuccessHandler = Mock(AuthenticationSuccessHandler)
        filter.authenticationFailureHandler = Mock(AuthenticationFailureHandler)
        filter.tokenReader = Mock(TokenReader)
        filter.authenticationEventPublisher = Mock(RestAuthenticationEventPublisher)
        filter.requestMatcher = Stub(SpringSecurityRestFilterRequestMatcher) {
            matches(_) >> true
        }
    }

    void "authentication passes when a valid token is found"() {
        given:
        String token = 'mytokenvalue'
        filter.restAuthenticationProvider = new StubRestAuthenticationProvider(
            validToken: token,
            username: 'user',
            password: 'password'
        )


        when:
        filter.doFilter(request, response, chain)

        then:
        response.status == 200
        1 * filter.tokenReader.findToken(request) >> new AccessToken(token)
        0 * filter.authenticationFailureHandler.onAuthenticationFailure( _, _, _ )
        1 * filter.authenticationEventPublisher.publishAuthenticationSuccess(_ as Authentication)
        notThrown(TokenNotFoundException)
    }

    void "when a token cannot be found, the request continues through the filter chain"() {
        given:
        filter.restAuthenticationProvider = new StubRestAuthenticationProvider(
                validToken: 'mytokenvalue',
                username: 'user',
                password: 'password'
        )
        FilterChain filterChain = GroovyMock(MockFilterChain, global: true)

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * filter.tokenReader.findToken(request) >> null
        1 * filterChain.doFilter(request, response)
    }
}

/**
 * Stubs out the RestAuthenticationProvider so that we can specify what a valid token is,
 * and have it return as if it authentication correctly if the token matches.
 */
class StubRestAuthenticationProvider extends RestAuthenticationProvider {

    String validToken
    String username
    String password

    Authentication authenticate(Authentication authentication) throws AuthenticationException {
        authentication = authentication as AccessToken
        if (authentication.accessToken == validToken) {
            return new AccessToken(new User(username, password, []), null, validToken)
        }

        throw new TokenNotFoundException('Token not found')
    }
}
