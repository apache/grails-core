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
import com.nimbusds.jwt.JWTClaimsSet
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService

import grails.plugin.springsecurity.rest.JwtService
import grails.plugin.springsecurity.rest.TokenGeneratorSupport
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.AbstractJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.DefaultRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.RSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.testing.services.ServiceUnitTest

/**
 * Created by @marcos-carceles on 03/03/15.
 */
class JwtTokenStorageServiceSpec extends Specification implements TokenGeneratorSupport, ServiceUnitTest<JwtTokenStorageService> {

    @Shared
    RSAKeyProvider keyProvider = new DefaultRSAKeyProvider()

    @Shared
    UserDetails otherUser = new User('other', 'N/A', [new SimpleGrantedAuthority('ROLE_OTHER')])

    JwtTokenStorageService service
    SignedJwtTokenGenerator tokenGenerator

    void setup() {
        service = new JwtTokenStorageService(jwtService: new JwtService(jwtSecret: 'fooo' * 8))
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
        service.loadUserByToken(accessToken.accessToken.replaceAll(/...$/, 'bar'))

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

    void "an encrypted JWT loads the user it was generated for"() {
        given:
        final userDetailsService = Mock(UserDetailsService)
        service.jwtService = new JwtService(keyProvider: keyProvider)
        service.userDetailsService = userDetailsService
        final accessToken = setupEncryptedJwtTokenGenerator(keyProvider).generateAccessToken(new User('testUser', 'testPassword', []), true, 3600, 3600)

        when:
        var user = service.loadUserByToken(accessToken.accessToken)

        then:
        user.username == 'testUser'
        0 * userDetailsService._

        when:
        user = service.loadUserByToken(accessToken.refreshToken)

        then:
        user.username == 'testUser'
        1 * userDetailsService.loadUserByUsername('testUser') >> new User('testUser', 'testPassword', [])
    }

    void "an encrypted JWT whose claims are not signed does not load a user: #description"() {
        given:
        final userDetailsService = Mock(UserDetailsService)
        service.jwtService = new JwtService(keyProvider: keyProvider)
        service.userDetailsService = userDetailsService

        when:
        service.loadUserByToken(encryptClaims(claims, keyProvider.publicKey))

        then:
        final exception = thrown(TokenNotFoundException)
        exception.message =~ /Token .* has an invalid signature/
        0 * userDetailsService._

        where:
        description                      | claims
        'access token with principal'    | accessTokenClaims(otherUser)
        'access token without principal' | new JWTClaimsSet.Builder(accessTokenClaims(otherUser)).claim('principal', null).build()
        'token without expiration'       | new JWTClaimsSet.Builder(accessTokenClaims(otherUser)).expirationTime(null).build()
        'refresh token'                  | new JWTClaimsSet.Builder(accessTokenClaims(otherUser)).claim(AbstractJwtTokenGenerator.REFRESH_ONLY_CLAIM, true).build()
    }

    void "a JWT of a type that is not configured does not load a user: #description"() {
        given:
        final userDetailsService = Mock(UserDetailsService)
        service.jwtService = jwtService
        service.userDetailsService = userDetailsService

        when:
        service.loadUserByToken(token)

        then:
        final exception = thrown(TokenNotFoundException)
        exception.message =~ /Token .* has an invalid signature/
        0 * userDetailsService._

        where:
        description                             | jwtService                               | token
        'signed JWT with encrypted JWTs set up' | new JwtService(keyProvider: keyProvider) | setupSignedJwtTokenGenerator().generateAccessToken(otherUser).accessToken
        'encrypted JWT with signed JWTs set up' | new JwtService(jwtSecret: 'foobar' * 10) | setupEncryptedJwtTokenGenerator(keyProvider).generateAccessToken(otherUser).accessToken
    }
}
