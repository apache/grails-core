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
package grails.plugin.springsecurity.rest.token.storage.jwt

import com.nimbusds.jose.JWSAlgorithm
import grails.plugin.springsecurity.rest.JwtService
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.testing.services.ServiceUnitTest
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import spock.lang.Issue
import spock.lang.Specification

/**
 * Created by @marcos-carceles on 03/03/15.
 */
class JwtTokenStorageServiceSpec extends Specification implements ServiceUnitTest<JwtTokenStorageService> {

    JwtTokenStorageService service
    SignedJwtTokenGenerator tokenGenerator

    void setup() {
        service = new JwtTokenStorageService(jwtService: new JwtService(jwtSecret: 'fooo'*8))
        tokenGenerator = new SignedJwtTokenGenerator(jwtSecret: service.jwtService.jwtSecret, defaultExpiration: 60, jwtTokenStorageService: service, customClaimProviders: [], jwsAlgorithm: JWSAlgorithm.HS256)
        tokenGenerator.afterPropertiesSet()
    }

    def "JWT Token signatures are verified"() {
        given:
        AccessToken accessToken = tokenGenerator.generateAccessToken(new User('testUser', 'testPassword', []))

        when:
        UserDetails user = service.loadUserByToken(accessToken.accessToken)

        then:
        user.username == 'testUser'

        when:
        service.loadUserByToken(accessToken.accessToken.replaceAll(/...$/,'bar'))

        then:
        thrown(TokenNotFoundException)
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/391")
    def "refresh token with optional expiration can be successfully loaded"() {
        given: "an access token that expires"
        AccessToken accessToken = tokenGenerator.generateAccessToken(new User('testUser', 'testPassword', []), true, 3600, 3600)
        service.userDetailsService = Mock(UserDetailsService)

        when:
        UserDetails user = service.loadUserByToken(accessToken.refreshToken)

        then:
        user.username == 'testUser'

        and:
        1 * service.userDetailsService.loadUserByUsername('testUser') >> { new User('testUser', 'testPassword', []) }
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/391")
    def "refresh token with optional expiration fails when expired"() {
        given:
        AccessToken accessToken = tokenGenerator.generateAccessToken(new User('testUser', 'testPassword', []), true, 3600, 1)
        sleep(1000) // Crude, but effective

        when:
        service.loadUserByToken(accessToken.refreshToken)

        then:
        def e = thrown(TokenNotFoundException)
        e.message =~ /Token .* has expired/
    }
}
