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

import grails.plugin.springsecurity.rest.RestTokenValidationFilter
import grails.plugins.rest.client.RestResponse
import grails.util.Holders
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Subject

@IgnoreIf({ System.getProperty('useBearerToken', 'false').toBoolean() })
@Subject(RestTokenValidationFilter)
class JwtRestTokenValidationFilterSpec extends AbstractRestSpec {

    void "the claims are available to the controller"() {
        given:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String token = authResponse.json.access_token

        when:
        def response = restBuilder.get("${baseUrl}/jwt/claims") {
            header 'X-Auth-Token', token
        }

        then:
        response.status == 200
        response.json.sub == 'jimi'
        response.json.exp
        response.json.iat
        response.json.roles.size() == 2
    }

}
