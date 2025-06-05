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

import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.plugin.springsecurity.rest.token.storage.jwt.JwtTokenStorageService
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import spock.lang.Issue
import spock.lang.Specification

class RestAuthenticationProviderSpec extends Specification implements TokenGeneratorSupport {

    RestAuthenticationProvider restAuthenticationProvider
    SignedJwtTokenGenerator tokenGenerator


    void setup() {
        this.tokenGenerator = setupSignedJwtTokenGenerator()
        this.restAuthenticationProvider = new RestAuthenticationProvider(useJwt: true)
        UserDetailsService userDetailsService = new InMemoryUserDetailsManager([])
        UserDetails testUser = new User('testUser', 'testPassword', [])
        userDetailsService.createUser(testUser)

        JwtService jwtService = new JwtService(jwtSecret: this.tokenGenerator.jwtTokenStorageService.jwtService.jwtSecret)
        this.restAuthenticationProvider.jwtService = jwtService
        this.restAuthenticationProvider.tokenStorageService = new JwtTokenStorageService(jwtService: jwtService, userDetailsService: userDetailsService)
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/276")
    void "if the JWT's expiration time is null, it's validated successfully"() {
        given:
        AccessToken accessToken = tokenGenerator.generateAccessToken(new User('testUser', 'testPassword', []), 0)

        when:
        Authentication result = this.restAuthenticationProvider.authenticate(accessToken)

        then:
        result.authenticated
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/391")
    void "refresh tokens should not be usable for authentication"() {
        given:
        AccessToken accessToken = tokenGenerator.generateAccessToken(new User('testUser', 'testPassword', []), 0)
        accessToken.accessToken = accessToken.refreshToken

        when:
        this.restAuthenticationProvider.authenticate(accessToken)

        then:
        def e = thrown(TokenNotFoundException)
        e.message =~ /Token .* is not valid/
    }
}
