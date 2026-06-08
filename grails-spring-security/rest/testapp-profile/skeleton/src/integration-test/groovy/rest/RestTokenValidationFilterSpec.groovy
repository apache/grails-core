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
class RestTokenValidationFilterSpec extends AbstractRestSpec {

    void "accessing a secured controller without token returns 403 (anonymous not authorized)"() {
        when:
        def response = restBuilder.get("${baseUrl}/secured")

        then:
        response.status == 403
    }

    void "accessing a secured controller with wrong token, returns 401"() {
        when:
        def response = restBuilder.get("${baseUrl}/secured") {
            header 'X-Auth-Token', 'whatever'
        }

        then:
        response.status == 401

    }

    void "accessing a public controller without token returns 302"() {
        when:
        def response = restBuilder.post("${baseUrl}/public")

        then:
        response.status == 302
    }

    void "accessing a public controller with wrong token, returns 302"() {
        when:
        def response = restBuilder.post("${baseUrl}/public") {
            header 'X-Auth-Token', 'whatever'
        }

        then:
        response.status == 302

    }

    void "a valid user can access the secured controller"() {
        given:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String token = authResponse.json.access_token

        when:
        def response = restBuilder.get("${baseUrl}/secured") {
            header 'X-Auth-Token', token
        }

        then:
        response.status == 200
        response.text == 'jimi'
    }

    void "role restrictions are applied when user does not have enough credentials"() {
        given:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String token = authResponse.json.access_token

        when:
        def response = restBuilder.get("${baseUrl}/secured/superAdmin") {
            header 'X-Auth-Token', token
        }

        then:
        response.status == 403
    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/67")
    void "JSESSIONID cookie is not created when using the stateless chain"() {
        when:
        RestResponse authResponse = sendCorrectCredentials() as RestResponse
        String token = authResponse.json.access_token

        then:
        !authResponse.headers.getFirst('Set-Cookie')

        when:
        def response = restBuilder.get("${baseUrl}/secured") {
            header 'X-Auth-Token', token
        }

        then:
        !response.headers.getFirst('Set-Cookie')

    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/74")
    void "anonymous access works when enabled"() {
        when:
        def response = restBuilder.get("${baseUrl}/anonymous")

        then:
        response.text == 'Hi'
        response.status == 200

    }

    @Issue("https://github.com/grails/grails-spring-security-rest/issues/74")
    void "in an anonymous chain, if a token is sent, is validated"() {
        when:
        def response = restBuilder.post("${baseUrl}/anonymous") {
            header 'X-Auth-Token', 'whatever'
        }

        then:
        response.status == 401

    }

}
