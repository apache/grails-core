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

import java.net.http.HttpRequest

import functional.test.app.Application
import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@IgnoreIf({ System.getProperty('TESTCONFIG') != 'putWithParams' })
@Issue('https://github.com/apache/grails-spring-security/issues/554')
@Integration(applicationClass = Application)
class TestFormParamsControllerSpec extends Specification implements HttpClientSupport {

    static final String FORM = 'application/x-www-form-urlencoded'

    @Shared String USERNAME = "Admin"
    @Shared String PASSWORD = "myPassword"

    /**
     * Issues a request with full control over the HTTP method, body, and whether a
     * {@code Content-Type} header is set (a {@code null} contentType sends none).
     */
    private TestHttpResponse exchange(String method, String path, String body = '', String contentType = null) {
        def builder = HttpRequest.newBuilder(URI.create("${httpBaseUrl}${path}"))
        if (contentType) {
            builder.header('Content-Type', contentType)
        }
        builder.method(method, HttpRequest.BodyPublishers.ofString(body ?: ''))
        sendHttpRequest(builder.build())
    }

    void 'PUT request with no parameters'() {
        when: "A PUT request with no parameters is made"
        TestHttpResponse response = exchange('PUT', '/testFormParams/permitAll', '', FORM)

        then: "the controller responds with the correct status and parameters are null"
        response.statusCode() == 200
        response.body() == "username: null, password: null"
    }

    void 'PUT request with parameters in the URL'() {
        when: "A PUT request with parameters in the URL is made"
        TestHttpResponse response = exchange('PUT', "/testFormParams/permitAll?username=${USERNAME}&password=${PASSWORD}", '', FORM)

        then: "the controller responds with the correct status and parameters are extracted"
        response.statusCode() == 200
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PUT request with parameters as x-www-form-urlencoded'() {
        when: "A PUT request with form params is made"
        TestHttpResponse response = exchange('PUT', '/testFormParams/permitAll', "username=${USERNAME}&password=${PASSWORD}", FORM)

        then: "the controller responds with the correct status and parameters are extracted"
        response.statusCode() == 200
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PUT request with NULL Content-Type and parameters in the URL'() {
        when: "A PUT request with no Content-Type and parameters in the URL is made"
        TestHttpResponse response = exchange('PUT', "/testFormParams/permitAll?username=${USERNAME}&password=${PASSWORD}", '', null)

        then: "the controller responds with the correct status and parameters are extracted"
        response.statusCode() == 200
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PUT request with NULL Content-Type'() {
        when: "A PUT request with NULL Content-Type is made"
        TestHttpResponse response = exchange('PUT', '/testFormParams/permitAll', '', null)

        then: "the controller responds with the correct status and parameters are null"
        response.statusCode() == 200
        response.body() == "username: null, password: null"
    }

    void 'PATCH request with no parameters'() {
        when: "A PATCH request with no parameters is made"
        TestHttpResponse response = exchange('PATCH', '/testFormParams/permitAll', '', FORM)

        then: "the controller responds with the correct status and parameters are null"
        response.statusCode() == 200
        response.body() == "username: null, password: null"
    }

    void 'PATCH request with parameters in the URL'() {
        when:
        TestHttpResponse response = exchange('PATCH', "/testFormParams/permitAll?username=${USERNAME}&password=${PASSWORD}", '', FORM)

        then: "the controller responds with the correct status and parameters are extracted"
        response.statusCode() == 200
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PATCH request with parameters as x-www-form-urlencoded'() {
        when: "A PATCH request with form params is made"
        TestHttpResponse response = exchange('PATCH', '/testFormParams/permitAll', "username=${USERNAME}&password=${PASSWORD}", FORM)

        then: "the controller responds with the correct status and parameters are extracted"
        response.statusCode() == 200
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PUT request to secured endpoint with parameters as x-www-form-urlencoded'() {
        when: "A PUT request with form params is made to a secured endpoint"
        TestHttpResponse response = exchange('PUT', '/testFormParams/permitAdmin', "username=${USERNAME}&password=${PASSWORD}", FORM)

        then: "the request is not processed by the controller (redirected to login)"
        response.statusCode() == 200
        response.body() != "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PATCH request to secured endpoint with parameters as x-www-form-urlencoded'() {
        when: "A PATCH request with form params is made to a secured endpoint"
        TestHttpResponse response = exchange('PATCH', '/testFormParams/permitAdmin', "username=${USERNAME}&password=${PASSWORD}", FORM)

        then: "the request is not processed by the controller (redirected to login)"
        response.statusCode() == 200
        response.body() != "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PATCH request with NULL Content-Type and parameters in the URL'() {
        when:
        TestHttpResponse response = exchange('PATCH', "/testFormParams/permitAll?username=${USERNAME}&password=${PASSWORD}", '', null)

        then: "the controller responds with the correct status and parameters are extracted"
        response.statusCode() == 200
        response.body() == "username: ${USERNAME}, password: ${PASSWORD}"
    }

    void 'PATCH request with NULL Content-Type'() {
        when: "A PATCH request with NULL Content-Type is made"
        TestHttpResponse response = exchange('PATCH', '/testFormParams/permitAll', '', null)

        then: "the controller responds with the correct status and parameters are null"
        response.statusCode() == 200
        response.body() == "username: null, password: null"
    }
}
