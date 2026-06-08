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
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

@IgnoreIf({ System.getProperty('useBearerToken', 'false').toBoolean() })
class RestAuthenticationFilterSpec extends AbstractRestSpec {

    @Unroll
	void "#httpMethod requests without parameters/JSON generate #statusCode responses"() {

        when:
        def response = sendEmptyRequest(httpMethod)

        then:
        response.status == statusCode

        where:
        httpMethod  | statusCode
        'get'       | 405
        'post'      | 400
        'put'       | 405
        'delete'    | 405
	}


    @Unroll
    void "the filter is only applied to the configured URL when a #httpMethod request is sent"() {
        when:
        def response = restBuilder."${httpMethod}"("${baseUrl}/nothingHere")

        then:
        response.status == status

        where:
        httpMethod  | status
        'get'       | 200   //The client follows redirects in GET requests. In this case, to /login/auth
        'post'      | 302   //In the rest of the cases, 302 to /login/auth
        'put'       | 302
        'delete'    | 302

    }

    void "authentication attempt with wrong credentials returns a failure status code"() {
        when:
        def response = sendWrongCredentials()

        then:
        response.status == 401
    }

    void "authentication attempt with correct credentials returns a valid status code"() {
        when:
        RestResponse response = sendCorrectCredentials() as RestResponse

        then:
        response.status == 200
        response.json.username == 'jimi'
        response.json.access_token
        response.json.roles.size() == 2
    }

    void "the content type header is properly set"() {
        when:
        RestResponse response = sendCorrectCredentials() as RestResponse

        then:
        response.headers.get('Content-Type')?.first() == 'application/json;charset=UTF-8'
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/275")
    void "WWW-Authenticate response header is sent on failed logins"() {
        when:
        def response = sendWrongCredentials()

        then:
        response.headers.getFirst('WWW-Authenticate') == 'Bearer'
    }


    private sendEmptyRequest(httpMethod) {
        if (config.grails.plugin.springsecurity.rest.login.useRequestParamsCredentials == true) {
            restBuilder."${httpMethod}"("${baseUrl}/api/login")
        } else {
            restBuilder."${httpMethod}"("${baseUrl}/api/login") {
                json {  }
            }
        }
    }
}
