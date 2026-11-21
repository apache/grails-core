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

import java.net.http.HttpClient
import java.net.http.HttpRequest

import spock.lang.Shared
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * Base class of the functional specs of the Spring Security REST plugin. The specs talk to the running application
 * over HTTP with a client that follows no redirect and keeps no cookie, so that a redirect to the login page and the
 * absence of an HTTP session can be asserted.
 *
 * <p>Every spec of an application has to extend this class: the dynamic property source is part of what identifies
 * the application context, and the plugin can be started only once in a JVM.</p>
 */
abstract class AbstractRestSpec extends Specification implements HttpClientSupport {

    static final String USERNAME = 'jimi'
    static final String PASSWORD = 'jimispassword'

    @Value('${local.server.port}')
    Integer localServerPort

    @Shared
    private HttpClient statelessClient

    @DynamicPropertySource
    static void mockOauthProvider(DynamicPropertyRegistry registry) {
        MockOauthProvider.register(registry)
    }

    /**
     * The port is injected into a field of this class. The port field of {@code HttpClientSupport} stays unset in
     * these specs, whose base class implements the trait in the test fixtures of another project than the spec.
     */
    @Override
    String getHttpBaseUrl() {
        "http://localhost:${localServerPort}"
    }

    /**
     * @return a client that follows no redirect and keeps no cookie, shared by the features of the specification
     */
    protected HttpClient getClient() {
        if (!statelessClient) {
            statelessClient = newHttpClientWith { followRedirects(HttpClient.Redirect.NEVER) }
        }
        statelessClient
    }

    protected TestHttpResponse get(String path, Map<String, String> requestHeaders = [:]) {
        request('GET', path, requestHeaders)
    }

    protected TestHttpResponse post(String path, Map<String, String> requestHeaders = [:], String body = null, String contentType = null) {
        request('POST', path, requestHeaders, body, contentType)
    }

    protected TestHttpResponse request(String httpMethod, String path, Map<String, String> requestHeaders = [:], String body = null, String contentType = null) {
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)
        HttpRequest request = newHttpRequestWith(path) {
            method(httpMethod.toUpperCase(), publisher)
            if (contentType) {
                header('Content-Type', contentType)
            }
            requestHeaders.each { String name, String value -> header(name, value) }
        }
        sendHttpRequest(request, client)
    }

    protected TestHttpResponse sendWrongCredentials() {
        sendCredentials('foo', 'bar')
    }

    protected TestHttpResponse sendCorrectCredentials(String username = USERNAME, String password = PASSWORD) {
        sendCredentials(username, password)
    }

    /**
     * Logs in and returns the access token.
     */
    protected String login(String username = USERNAME, String password = PASSWORD) {
        TestHttpResponse response = sendCorrectCredentials(username, password)
        assert response.statusCode() == 200
        response.json().access_token as String
    }

    private TestHttpResponse sendCredentials(String username, String password) {
        httpPostJson('/api/login', [username: username, password: password], client)
    }
}
