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
package enablemvccheck

import java.net.http.HttpRequest

import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Verifies the HTTP behavior of a Grails application that declares {@code @EnableWebMvc}
 * (Grails 7 auto-injected this; Grails 8 no longer does, so this app opts back in). The
 * annotation suppresses Spring Boot's {@code WebMvcAutoConfiguration}, so:
 * <ul>
 *   <li>classpath resources such as {@code classpath:/public/*} are NOT served at the
 *       context root (no Boot catch-all static-resource handler)</li>
 *   <li>a static {@code index.html} is NOT served for the unmapped root path
 *       (no Boot {@code WelcomePageHandlerMapping})</li>
 * </ul>
 *
 * This app also sets {@code grails.i18n.localeResolver=acceptHeader}, so locale resolution follows
 * the {@code Accept-Language} header and the {@code ?lang} switch is disabled. That is independent
 * of {@code @EnableWebMvc}: with the default session resolver the {@code ?lang=} switch works even
 * under {@code @EnableWebMvc} (see {@code GrailsLocaleResolverAutoConfiguration}).
 *
 * Form-parameter parsing is <em>not</em> affected by the annotation: Grails contributes its
 * own {@code FormContentFilter} when Boot's {@code WebMvcAutoConfiguration} backs off, so
 * {@code PUT}, {@code PATCH} and {@code DELETE} form bodies are parsed into request
 * parameters either way.
 */
@Integration
@Tag('http-client')
class WebMvcDefaultsFunctionalSpec extends Specification implements HttpClientSupport {

    private static final String FORM_CONTENT_TYPE = 'application/x-www-form-urlencoded'
    private static final String FORM_BODY = 'title=The%20Stand&pages=1153'

    @Unroll
    void 'a form-encoded #httpMethod body is parsed into request parameters'() {
        when: 'a form-encoded body is sent with a non-POST method'
        def response = sendHttpRequest(newHttpRequestWith('/formContent/echo') {
            header('Content-Type', FORM_CONTENT_TYPE)
            method(httpMethod, HttpRequest.BodyPublishers.ofString(FORM_BODY))
        })

        then: 'the fields are visible as request parameters via the Grails-provided form-content filter'
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

    void 'a classpath:/public resource is not served at the context root'() {
        when: 'a static classpath resource that no URL mapping serves is requested at the root'
        def response = http('/boot-default-mapping.txt')

        then: 'the request falls through to URL-mapping error handling'
        response.assertStatus(404)
    }

    void 'no static index.html welcome page is served for the unmapped root path'() {
        when: 'the unmapped root path is requested'
        def response = http('/')

        then: 'the request falls through to URL-mapping error handling'
        response.assertStatus(404)
    }

    void 'locale resolution follows the Accept-Language header and the lang request parameter is ignored'() {
        // This app sets grails.i18n.localeResolver=acceptHeader, so the read-only
        // AcceptHeaderLocaleResolver is used: the locale follows the Accept-Language header and the
        // ?lang= switch is disabled (its interceptor is not registered, so no error is logged).
        // With the default (session) resolver, ?lang=de would win and return "de" — this holds even
        // under @EnableWebMvc (see GrailsLocaleResolverAutoConfiguration).
        when:
        def response = http(['Accept-Language': 'fr-FR'], '/locale/echo?lang=de')

        then:
        response.assertStatus(200)
        response.json().locale == 'fr_FR'
    }
}
