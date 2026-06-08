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
import grails.util.Holders
import spock.lang.IgnoreIf

@IgnoreIf({ System.getProperty('useBearerToken', 'false').toBoolean() })
class ValidateEndpointSpec extends AbstractRestSpec {

    void "calling /api/validate with a valid token returns a JSON representation"() {
        given:
        RestResponse authResponse = sendCorrectCredentials()
        String token = authResponse.json.access_token

        when:
        def response = restBuilder.get("${baseUrl}/api/validate") {
            header 'X-Auth-Token', token
        }

        then:
        response.status == 200
        response.json.username == 'jimi'
        response.json.access_token
        response.json.roles.size() == 2
    }

    void "calling /api/validate with an invalid token returns 401"() {
        when:
        def response = restBuilder.get("${baseUrl}/api/validate") {
            header 'X-Auth-Token', 'something-else'
        }

        then:
        response.status == 401
    }

    void "calling /api/validate without token returns 403 (anonymous unauthorized)"() {
        when:
        def response = restBuilder.get("${baseUrl}/api/validate")

        then:
        response.status == 403
    }


}