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

package specs

import functional.test.app.Application
import grails.testing.mixin.integration.Integration
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.uri.UriTemplate
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Shared

@IgnoreIf({ System.getProperty('TESTCONFIG') != 'putWithParams' })
@Issue('https://github.com/apache/grails-spring-security/issues/554')
@Integration(applicationClass = Application)
class TestFormParamsControllerSpec extends HttpClientSpec {

    @Shared String USERNAME = "Admin"
    @Shared String PASSWORD = "myPassword"

    void 'PUT request with no parameters'() {

        when: "A PUT request with no parameters is made"
        HttpResponse<String> response = client.exchange(HttpRequest.PUT("/testFormParams/permitAll", "").contentType("application/x-www-form-urlencoded"), String)

        then: "the controller responds with the correct status and parameters are null"
        response.status == HttpStatus.OK
        response.body() == "username: null, password: null"
    }

    void 'PUT request with parameters in the URL'() {
        when: "A PUT request with no parameters is made"
        String expandUrl = new UriTemplate("/testFormParams/permitAll{?username,password}").expand(["username": USERNAME, "password": PASSWORD])
        HttpResponse<String> response = client.exchange(HttpRequest.PUT(expandUrl, "").contentType("application/x-www-form-urlencoded"), String)

        then: "the controller responds with the correct status and parameters are extracted"
        response.status == HttpStatus.OK
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PUT request with parameters as x-www-form-urlencoded'() {
        given: "a form with username and password params"
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>()
        form.add("username", USERNAME)
        form.add("password", PASSWORD)

        when: "A PUT request with form params is made"
        HttpResponse<String> response = client.exchange(HttpRequest.PUT("/testFormParams/permitAll", form).contentType("application/x-www-form-urlencoded"), String)

        then: "the controller responds with the correct status and parameters are extracted"
        response.status == HttpStatus.OK
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PUT request with NULL Content-Type and parameters in the URL'() {
        when: "A PUT request with no parameters is made"
        String expandUrl = new UriTemplate("/testFormParams/permitAll{?username,password}").expand(["username": USERNAME, "password": PASSWORD])
        HttpResponse<String> response = client.exchange(HttpRequest.PUT(expandUrl, ""), String)

        then: "the controller responds with the correct status and parameters are extracted"
        response.status == HttpStatus.OK
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PUT request with NULL Content-Type'() {
        when: "A PUT request with NULL Content-Type is made"
        HttpResponse<String> response = client.exchange(HttpRequest.PUT("/testFormParams/permitAll", ""), String)

        then: "the controller responds with the correct status and parameters are null"
        response.status == HttpStatus.OK
        response.body() == "username: null, password: null"
    }

    void 'PATCH request with no parameters'() {
        when: "A PATCH request with no parameters is made"
        HttpResponse<String> response = client.exchange(HttpRequest.PATCH("/testFormParams/permitAll", "").contentType("application/x-www-form-urlencoded"), String)

        then: "the controller responds with the correct status and parameters are null"
        response.status == HttpStatus.OK
        response.body() == "username: null, password: null"
    }

    void 'PATCH request with parameters in the URL'() {
        when:
        String expandUrl = new UriTemplate("/testFormParams/permitAll{?username,password}").expand(["username": USERNAME, "password": PASSWORD])
        HttpResponse<String> response = client.exchange(HttpRequest.PATCH(expandUrl, "").contentType("application/x-www-form-urlencoded"), String)

        then: "the controller responds with the correct status and parameters are extracted"
        response.status == HttpStatus.OK
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PATCH request with parameters as x-www-form-urlencoded'() {
        given: "a form with username and password params"
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>()
        form.add("username", USERNAME)
        form.add("password", PASSWORD)

        when: "A PATCH request with form params is made"
        HttpResponse<String> response = client.exchange(HttpRequest.PATCH("/testFormParams/permitAll", form
        ).contentType("application/x-www-form-urlencoded"), String)

        then: "the controller responds with the correct status and parameters are extracted"
        response.status == HttpStatus.OK
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PUT request to secured endpoint with parameters as x-www-form-urlencoded'() {
        given: "a form with username and password params"
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>()
        form.add("username", USERNAME)
        form.add("password", PASSWORD)

        when: "A PUT request with form params is made to a secured endpoint"
        HttpResponse<String> response = client.exchange(HttpRequest.PUT("/testFormParams/permitAdmin", form
        ).contentType("application/x-www-form-urlencoded"), String)

        then: "the request is not processed by the controller"
        response.status == HttpStatus.OK // MN Client isn't exposing this is a 302 to login
        response.body() != "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PATCH request to secured endpoint with parameters as x-www-form-urlencoded'() {
        given:
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>()
        form.add("username", USERNAME)
        form.add("password", PASSWORD)

        when: "A PATCH request with form params is made to a secured endpoint"
        HttpResponse<String> response = client.exchange(HttpRequest.PATCH("/testFormParams/permitAdmin", form
        ).contentType("application/x-www-form-urlencoded"), String)

        then: "the request is not processed by the controller"
        response.status == HttpStatus.OK // MN Client isn't exposing this is a 302 to login
        response.body() != "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PATCH request with NULL Content-Type and parameters in the URL'() {
        when:
        String expandUrl = new UriTemplate("/testFormParams/permitAll{?username,password}").expand(["username": USERNAME, "password": PASSWORD])
        HttpResponse<String> response = client.exchange(HttpRequest.PATCH(expandUrl, ""
        ).contentType("application/x-www-form-urlencoded"), String)

        then: "the controller responds with the correct status and parameters are extracted"
        response.status == HttpStatus.OK
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PATCH request with NULL Content-Type'() {
        when: "A PATCH request with NULL Content-Type is made"
        HttpResponse<String> response = client.exchange(HttpRequest.PATCH("/testFormParams/permitAll", ""
        ), String)

        then: "the controller responds with the correct status and parameters are null"
        response.status == HttpStatus.OK
        response.body() == "username: null, password: null"
    }

}
