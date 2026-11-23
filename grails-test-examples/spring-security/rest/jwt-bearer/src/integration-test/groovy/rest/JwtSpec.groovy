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

package rest

import com.nimbusds.jwt.JWT
import spock.lang.Issue
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager

import grails.plugin.springsecurity.rest.JwtService
import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * Encrypted JWTs that expire after 30 seconds, and their refresh tokens.
 */
@Integration
class JwtSpec extends AbstractRestSpec {

    private static final String REFRESH_ENDPOINT = '/oauth/access_token'

    @Autowired
    JwtService jwtService

    @Autowired
    InMemoryUserDetailsManager userDetailsManager

    void "token expiration applies"() {
        given:
        String accessToken = login()

        when:
        TestHttpResponse response = validate(accessToken)

        then:
        response.statusCode() == 200
        response.json().username == 'jimi'
        response.json().access_token
        response.json().roles.size() == 2

        expect: 'the token is refused once its 30 seconds are over'
        // the minute beyond them only bounds a token that is never refused
        new PollingConditions(timeout: 90, delay: 1).eventually {
            assert validate(accessToken).statusCode() == 401
        }
    }

    void "remaining time to expiration is reflected when validating a token"() {
        given:
        String accessToken = login()

        when:
        sleep(1000)
        TestHttpResponse response = validate(accessToken)

        then:
        response.statusCode() == 200
        response.json().expires_in
        response.json().expires_in < 30
    }

    void "refresh tokens are generated"() {
        when:
        TestHttpResponse authResponse = sendCorrectCredentials()

        then:
        authResponse.json().access_token
        authResponse.json().refresh_token
    }

    void "refresh tokens can be used to obtain access tokens"() {
        given:
        String refreshToken = sendCorrectCredentials().json().refresh_token

        when:
        TestHttpResponse response = refresh("grant_type=refresh_token&refresh_token=${refreshToken}")

        then:
        response.json().access_token

        and:
        response.json().refresh_token == refreshToken
    }

    void "refresh token is required to send the refresh token"() {
        when:
        TestHttpResponse response = refresh('grant_type=refresh_token')

        then:
        response.statusCode() == 400
    }

    void "grant_type is required to send the refresh token"() {
        when:
        TestHttpResponse response = refresh('refresh_token=whatever')

        then:
        response.statusCode() == 400
    }

    @Unroll
    void "#method.toUpperCase() HTTP method produces a #status response code when requesting the refresh token endpoint"() {
        given:
        String refreshToken = sendCorrectCredentials().json().refresh_token

        when:
        TestHttpResponse response = request(method, REFRESH_ENDPOINT, [:], "grant_type=refresh_token&refresh_token=${refreshToken}", FORM)

        then:
        response.statusCode() == status

        where:
        method   | status
        'get'    | 405
        'post'   | 200
        'put'    | 405
        'delete' | 405
    }

    void "an invalid refresh token is rejected as forbidden"() {
        when:
        TestHttpResponse response = refresh('grant_type=refresh_token&refresh_token=thisIsNotAJWT')

        then:
        response.statusCode() == 403
    }

    void "issuer is set via a custom claim provider"() {
        given:
        String accessToken = login()

        when:
        JWT jwt = jwtService.parse(accessToken)

        then:
        jwt.JWTClaimsSet.issuer == 'Spring Security REST Grails Plugin'
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/pull/344')
    void "if the user no longer exists, token can't be refreshed"() {
        given:
        userDetailsManager.createUser(new User('foo', '{noop}password', []))
        String refreshToken = sendCorrectCredentials('foo', 'password').json().refresh_token
        userDetailsManager.deleteUser('foo')

        when:
        TestHttpResponse response = refresh("grant_type=refresh_token&refresh_token=${refreshToken}")

        then:
        response.statusCode() == 403

        cleanup:
        userDetailsManager.deleteUser('foo')
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/pull/344')
    @Unroll
    void "if the user is #status, token can't be refreshed"(User updatedUser, String status) {
        given:
        userDetailsManager.createUser(new User('foo', '{noop}password', []))
        String refreshToken = sendCorrectCredentials('foo', 'password').json().refresh_token
        userDetailsManager.updateUser(updatedUser)

        when:
        TestHttpResponse response = refresh("grant_type=refresh_token&refresh_token=${refreshToken}")

        then:
        response.statusCode() == 403

        cleanup:
        userDetailsManager.deleteUser('foo')

        where:
        updatedUser                                                    | status
        new User('foo', '{noop}password', false, true, true, true, []) | 'disabled'
        new User('foo', '{noop}password', true, false, true, true, []) | 'expired'
        new User('foo', '{noop}password', true, true, false, true, []) | 'credentials expired'
        new User('foo', '{noop}password', true, true, true, false, []) | 'locked'
    }

    private TestHttpResponse validate(String accessToken) {
        post('/api/validate', [Authorization: "Bearer ${accessToken}".toString()])
    }

    private TestHttpResponse refresh(String form) {
        post(REFRESH_ENDPOINT, [:], form, FORM)
    }
}
