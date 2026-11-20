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
package functionaltests

import java.net.http.HttpRequest

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Functional coverage that {@code application/x-www-form-urlencoded} bodies of non-POST requests are
 * parsed into request parameters in a default Grails application (no {@code @EnableWebMvc}), where
 * Spring Boot's {@code OrderedFormContentFilter} is active. This replaces the unit-level parsing that
 * used to live in {@code GrailsParameterMapTests}, now that {@code GrailsParameterMap} relies on the
 * filter rather than parsing the body itself.
 */
@Integration
@Tag('http-client')
class FormContentFunctionalSpec extends Specification implements HttpClientSupport {

    private static final String FORM_CONTENT_TYPE = 'application/x-www-form-urlencoded'
    private static final String FORM_BODY = 'title=The%20Stand&pages=1153'

    @Unroll
    void 'a form-encoded #httpMethod body is parsed into request parameters'() {
        when: 'a form-encoded body is sent with a non-POST method'
        def response = sendHttpRequest(newHttpRequestWith('/formContent/echo') {
            header('Content-Type', FORM_CONTENT_TYPE)
            method(httpMethod, HttpRequest.BodyPublishers.ofString(FORM_BODY))
        })

        then: 'the fields are visible as request parameters via the servlet-level form-content filter'
        response.assertStatus(200)
        def json = response.json()
        json.method == httpMethod
        json.title == 'The Stand'
        json.pages == '1153'

        where:
        httpMethod << ['PUT', 'PATCH', 'DELETE']
    }

    void 'a form-encoded POST body is parsed into request parameters'() {
        when:
        def response = httpPostForm('/formContent/echo', [title: 'The Stand', pages: 1153])

        then:
        response.assertStatus(200)
        def json = response.json()
        json.method == 'POST'
        json.title == 'The Stand'
        json.pages == '1153'
    }
}
