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
import spock.lang.Unroll

import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * The login endpoint, {@code /api/login}, of an application that reads the token from the {@code X-Auth-Token} header.
 */
abstract class AbstractRestAuthenticationFilterSpec extends AbstractRestSpec {

    @Unroll
    void "#httpMethod requests without parameters/JSON generate #statusCode responses"() {
        when:
        TestHttpResponse response = request(httpMethod, '/api/login', [:], '{}', 'application/json')

        then:
        response.statusCode() == statusCode

        where:
        httpMethod | statusCode
        'get'      | 405
        'post'     | 400
        'put'      | 405
        'delete'   | 405
    }

    @Unroll
    void "the filter is only applied to the configured URL when a #httpMethod request is sent"() {
        when:
        TestHttpResponse response = request(httpMethod, '/nothingHere')

        then: 'the stateful chain sends the request to the login page'
        response.statusCode() == 302
        response.headerValue('Location').endsWith('/login/auth')

        where:
        httpMethod << ['get', 'post', 'put', 'delete']
    }

    void "authentication attempt with wrong credentials returns a failure status code"() {
        when:
        TestHttpResponse response = sendWrongCredentials()

        then:
        response.statusCode() == 401
    }

    void "authentication attempt with correct credentials returns a valid status code"() {
        when:
        TestHttpResponse response = sendCorrectCredentials()

        then:
        response.statusCode() == 200
        response.json().username == 'jimi'
        response.json().access_token
        response.json().roles.size() == 2
    }

    void "the content type header is properly set"() {
        when:
        TestHttpResponse response = sendCorrectCredentials()

        then:
        response.headerValue('Content-Type') == 'application/json;charset=UTF-8'
    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/275')
    void "WWW-Authenticate response header is sent on failed logins"() {
        when:
        TestHttpResponse response = sendWrongCredentials()

        then:
        response.headerValue('WWW-Authenticate') == 'Bearer'
    }
}
