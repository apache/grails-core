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

import spock.lang.Issue

import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * Token validation in an application that reads the token from the {@code X-Auth-Token} header and allows anonymous
 * access.
 */
abstract class AbstractRestTokenValidationFilterSpec extends AbstractRestSpec {

    void "accessing a secured controller without token returns 403 (anonymous not authorized)"() {
        when:
        TestHttpResponse response = get('/secured')

        then:
        response.statusCode() == 403
    }

    void "accessing a secured controller with wrong token, returns 401"() {
        when:
        TestHttpResponse response = get('/secured', ['X-Auth-Token': 'whatever'])

        then:
        response.statusCode() == 401
    }

    void "accessing a public controller without token returns 302"() {
        when:
        TestHttpResponse response = post('/public')

        then:
        response.statusCode() == 302
    }

    void "accessing a public controller with wrong token, returns 302"() {
        when:
        TestHttpResponse response = post('/public', ['X-Auth-Token': 'whatever'])

        then:
        response.statusCode() == 302
    }

    void "a valid user can access the secured controller"() {
        given:
        String token = login()

        when:
        TestHttpResponse response = get('/secured', ['X-Auth-Token': token])

        then:
        response.statusCode() == 200
        response.body() == 'jimi'
    }

    void "role restrictions are applied when user does not have enough credentials"() {
        given:
        String token = login()

        when:
        TestHttpResponse response = get('/secured/superAdmin', ['X-Auth-Token': token])

        then:
        response.statusCode() == 403
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/67')
    void "JSESSIONID cookie is not created when using the stateless chain"() {
        when:
        TestHttpResponse authResponse = sendCorrectCredentials()
        String token = authResponse.json().access_token

        then:
        !authResponse.hasHeader('Set-Cookie')

        when:
        TestHttpResponse response = get('/secured', ['X-Auth-Token': token])

        then:
        !response.hasHeader('Set-Cookie')
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/74')
    void "anonymous access works when enabled"() {
        when:
        TestHttpResponse response = get('/anonymous')

        then:
        response.statusCode() == 200
        response.body() == 'Hi'
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/74')
    void "in an anonymous chain, if a token is sent, is validated"() {
        when:
        TestHttpResponse response = post('/anonymous', ['X-Auth-Token': 'whatever'])

        then:
        response.statusCode() == 401
    }
}
