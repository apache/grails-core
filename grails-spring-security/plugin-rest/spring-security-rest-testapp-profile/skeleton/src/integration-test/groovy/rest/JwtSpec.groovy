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
import grails.plugin.springsecurity.rest.JwtService
import grails.plugins.rest.client.RestResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

@IgnoreIf({ !System.getProperty('useBearerToken', 'false').toBoolean() })
class JwtSpec extends AbstractRestSpec {

    @Autowired
    JwtService jwtService

    @Autowired
    InMemoryUserDetailsManager userDetailsManager

    void "token expiration applies"() {
        given:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String accessToken = authResponse.json.access_token

        when:
        def response = restBuilder.post("${baseUrl}/api/validate") {
            header 'Authorization', "Bearer ${accessToken}"
        }

        then:
        response.status == 200
        response.json.username == 'jimi'
        response.json.access_token
        response.json.roles.size() == 2

        when:
        Thread.sleep 5000
        response = restBuilder.post("${baseUrl}/api/validate") {
            header 'Authorization', "Bearer ${accessToken}"
        }

        then:
        response.status == 401
    }

    void "remaining time to expiration is reflected when validating a token"() {
        given:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String accessToken = authResponse.json.access_token

        when:
        Thread.sleep 1000
        def response = restBuilder.post("${baseUrl}/api/validate") {
            header 'Authorization', "Bearer ${accessToken}"
        }

        then:
        response.status == 200
        response.json.expires_in
        response.json.expires_in < 5
    }

    void "refresh tokens are generated"() {
        when:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse

        then:
        authResponse.json.access_token
        authResponse.json.refresh_token
    }

    void "refresh tokens can be used to obtain access tokens"() {
        given:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String refreshToken = authResponse.json.refresh_token

        when:
        def response = restBuilder.post("${baseUrl}/oauth/access_token") {
            contentType "application/x-www-form-urlencoded"
            body "grant_type=refresh_token&refresh_token=${refreshToken}".toString()
        }

        then:
        response.json.access_token

        and:
        response.json.refresh_token == refreshToken
    }

    void "refresh token is required to send the refresh token"() {
        when:
        def response = restBuilder.post("${baseUrl}/oauth/access_token") {
            contentType "application/x-www-form-urlencoded"
            body "grant_type=refresh_token".toString()
        }

        then:
        response.status == 400
    }

    void "grant_type is required to send the refresh token"() {
        when:
        def response = restBuilder.post("${baseUrl}/oauth/access_token") {
            contentType "application/x-www-form-urlencoded"
            body "refresh_token=whatever".toString()
        }

        then:
        response.status == 400
    }

    @Unroll
    void "#method.toUpperCase() HTTP method produces a #status response code when requesting the refresh token endpoint"() {
        given:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String refreshToken = authResponse.json.refresh_token

        when:
        def response = restBuilder."${method}"("${baseUrl}/oauth/access_token") {
            contentType "application/x-www-form-urlencoded"
            body "grant_type=refresh_token&refresh_token=${refreshToken}".toString()
        }

        then:
        response.status == status

        where:
        method      | status
        'get'       | 405
        'post'      | 200
        'put'       | 405
        'delete'    | 405
    }

    void "an invalid refresh token is rejected as forbidden"() {
        when:
        def response = restBuilder.post("${baseUrl}/oauth/access_token") {
            contentType "application/x-www-form-urlencoded"
            body "grant_type=refresh_token&refresh_token=thisIsNotAJWT".toString()
        }

        then:
        response.status == 403
    }

    void "issuer is set via a custom claim provider"() {
        given:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String accessToken = authResponse.json.access_token

        when:
        JWT jwt = jwtService.parse(accessToken)

        then:
        jwt.JWTClaimsSet.issuer == 'Spring Security REST Grails Plugin'
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/pull/344")
    void "if the user no longer exists, token can't be refreshed"() {
        given:
        userDetailsManager.createUser(new User('foo', '{noop}password', []))
        RestResponse authResponse = sendCorrectCredentials('foo', 'password') as RestResponse
        String refreshToken = authResponse.json.refresh_token
        userDetailsManager.deleteUser('foo')

        when:
        def response = restBuilder.post("${baseUrl}/oauth/access_token") {
            contentType "application/x-www-form-urlencoded"
            body "grant_type=refresh_token&refresh_token=${refreshToken}".toString()
        }

        then:
        response.status == 403

        cleanup:
        userDetailsManager.deleteUser('foo')
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/pull/344")
    @Unroll
    void "if the user is #status, token can't be refreshed"(User updatedUser, String status) {
        given:
        userDetailsManager.createUser(new User('foo', '{noop}password', []))
        RestResponse authResponse = sendCorrectCredentials('foo', 'password') as RestResponse
        String refreshToken = authResponse.json.refresh_token
        userDetailsManager.updateUser(updatedUser)

        when:
        def response = restBuilder.post("${baseUrl}/oauth/access_token") {
            contentType "application/x-www-form-urlencoded"
            body "grant_type=refresh_token&refresh_token=${refreshToken}".toString()
        }

        then:
        response.status == 403

        cleanup:
        userDetailsManager.deleteUser('foo')

        where:
        updatedUser                                                 | status
        new User('foo', '{noop}password', false, true, true, true, [])    | "disabled"
        new User('foo', '{noop}password', true, false, true, true, [])    | "expired"
        new User('foo', '{noop}password', true, true, false, true, [])    | "credentials expired"
        new User('foo', '{noop}password', true, true, true, false, [])    | "locked"
    }

}
