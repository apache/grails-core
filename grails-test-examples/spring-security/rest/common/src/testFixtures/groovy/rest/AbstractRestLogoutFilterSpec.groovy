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

import spock.lang.Unroll

import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * The logout endpoint, {@code /api/logout}, which an application has when its token storage can remove a token.
 */
abstract class AbstractRestLogoutFilterSpec extends AbstractRestSpec {

    void "logout filter can remove a token"() {
        given:
        String token = login()

        when:
        TestHttpResponse response = post('/api/logout', ['X-Auth-Token': token])

        then:
        response.statusCode() == 200

        when:
        response = get('/api/validate', ['X-Auth-Token': token])

        then:
        response.statusCode() == 401
    }

    void "logout filter returns 404 if token is not found"() {
        when:
        TestHttpResponse response = post('/api/logout', ['X-Auth-Token': 'whatever'])

        then:
        response.statusCode() == 404
    }

    void "calling /api/logout without token returns 400"() {
        when:
        TestHttpResponse response = post('/api/logout')

        then:
        response.statusCode() == 400
    }

    @Unroll
    void "#httpMethod requests generate #statusCode responses"() {
        given:
        String token = login()

        when:
        TestHttpResponse response = request(httpMethod, '/api/logout', ['X-Auth-Token': token])

        then:
        response.statusCode() == statusCode

        where:
        httpMethod | statusCode
        'get'      | 405
        'post'     | 200
        'put'      | 405
        'delete'   | 405
    }
}
