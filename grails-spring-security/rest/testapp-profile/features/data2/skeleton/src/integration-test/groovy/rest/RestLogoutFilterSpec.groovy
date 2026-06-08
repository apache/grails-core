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

import grails.plugins.rest.client.RestResponse
import spock.lang.Unroll

class RestLogoutFilterSpec extends AbstractRestSpec {

    void "logout filter can remove a token"() {
        given:
        RestResponse authResponse = sendCorrectCredentials()
        String token = authResponse.json.access_token

        when:
        def response = restBuilder.post("${baseUrl}/api/logout") {
            header 'X-Auth-Token', token
        }

        then:
        response.status == 200

        when:
        response = restBuilder.get("${baseUrl}/api/validate") {
            header 'X-Auth-Token', token
        }

        then:
        response.status == 401
    }

    void "logout filter returns 404 if token is not found"() {
        when:
        def response = restBuilder.post("${baseUrl}/api/logout") {
            header 'X-Auth-Token', 'whatever'
        }

        then:
        response.status == 404

    }

    void "calling /api/logout without token returns 400"() {
        when:
        def response = restBuilder.post("${baseUrl}/api/logout")

        then:
        response.status == 400
    }

    @Unroll
    void "#httpMethod requests generate #statusCode responses"() {

        given:
        RestResponse authResponse = sendCorrectCredentials()
        String token = authResponse.json.access_token

        when:
        def response = restBuilder."${httpMethod}"("${baseUrl}/api/logout") {
            header 'X-Auth-Token', token
        }

        then:
        response.status == statusCode

        where:
        httpMethod  | statusCode
        'get'       | 405
        'post'      | 200
        'put'       | 405
        'delete'    | 405
    }


}