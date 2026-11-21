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

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.TestHttpResponse

@Integration
@Issue('https://github.com/grails/grails-spring-security-rest/issues/73')
class BearerTokenSpec extends AbstractRestSpec {

    void "access token response is compliant with the specification"() {
        when:
        TestHttpResponse response = sendCorrectCredentials()

        then:
        response.statusCode() == 200
        response.headerValue('Content-Type') == 'application/json;charset=UTF-8'
        response.headerValue('Cache-Control') == 'no-store'
        response.headerValue('Pragma') == 'no-cache'
        response.json().access_token
        response.json().token_type == 'Bearer'
    }

    void "authorisation header is checked to read token value"() {
        given:
        String token = login()

        when:
        TestHttpResponse response = get('/secured', [Authorization: "Bearer ${token}".toString()])

        then:
        response.statusCode() == 200
    }

    void "Form-Encoded body parameter can be used"() {
        given:
        String token = login()

        when:
        TestHttpResponse response = post('/secured', [:], "access_token=${token}", FORM)

        then:
        response.statusCode() == 200
    }

    void "query string can be used"() {
        given:
        String token = login()

        when:
        TestHttpResponse response = get("/secured?access_token=${token}")

        then:
        response.statusCode() == 200
    }

    void "if credentials are required but missing, the response contains WWW-Authenticate header"() {
        when:
        TestHttpResponse response = post('/secured', [:], '', FORM)

        then:
        response.statusCode() == 401
        response.headerValue('WWW-Authenticate') == 'Bearer'
    }

    void "if the token is invalid, it is indicated in the header"() {
        when:
        TestHttpResponse response = get('/secured', [Authorization: 'Bearer wrongTokenValue'])

        then:
        response.statusCode() == 401
        response.headerValue('WWW-Authenticate') == 'Bearer error="invalid_token"'
    }

    void "when accessing a secured object with a non-bearer request, it's considered a unauthorized request"() {
        when:
        TestHttpResponse response = post('/secured', [:], '{hi:777}', 'text/plain')

        then:
        response.statusCode() == 401
        response.headerValue('WWW-Authenticate') == 'Bearer'
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/98')
    void "accessing Anonymous without a token, responds  ok"() {
        when:
        TestHttpResponse response = get('/anonymous', ['Content-Type': 'application/json;charset=UTF-8'])

        then:
        response.statusCode() == 200
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/98')
    void "accessing Secured without a token, responds Unauthorized"() {
        when:
        TestHttpResponse response = post('/secured', [:], '{hi:777}', 'application/json;charset=UTF-8')

        then:
        response.statusCode() == 401
        response.headerValue('WWW-Authenticate') == 'Bearer'
    }

    void "accessing Secured with valid token, but not authorized responds forbidden"() {
        given:
        String token = login()

        when:
        TestHttpResponse response = get('/secured/superAdmin', [Authorization: "Bearer ${token}".toString()])

        then:
        response.statusCode() == 403
        response.headerValue('WWW-Authenticate') == 'Bearer error="insufficient_scope"'
    }
}
