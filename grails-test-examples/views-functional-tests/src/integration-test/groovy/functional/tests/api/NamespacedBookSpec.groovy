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
package functional.tests.api

import spock.lang.Issue
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

@Integration
class NamespacedBookSpec extends Specification implements HttpClientSupport {

    void 'test view rendering with a namespace'() {
        when: 'A request is sent to a controller with a namespace'
        def response = http('/api/book')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', [
                api: 'version 1.0 (Namespaced)',
                title: 'API - The Shining'
        ])
    }

    void 'test nested template rendering with a namespace'() {
        when: 'A request is sent to a controller with a namespace'
        def response = http('/api/book/nested')

        then: 'The response contains the child template'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', [
                foo: 'bar'
        ])
    }

    void 'test the correct content type is chosen (json)'() {
        when: 'A request is sent to a controller with a namespace'
        def response = http('/api/book')

        then: 'The response contains the child template'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', [
                api: 'version 1.0 (Namespaced)',
                title: 'API - The Shining'
        ])
    }

    void 'test the correct content type is chosen (hal)'() {
        when: 'A request is sent to a controller with a namespace'
        def response = http(
                '/api/book',
                'Accept': 'application/hal+json'
        )

        then: 'The response contains the child template'
        response.expectJson(200, 'Content-Type': 'application/hal+json;charset=UTF-8', [
                api: 'version 1.0 (Namespaced HAL)',
                title: 'API - The Shining',
                _links: [
                        self: [
                                href: "$httpClientRootUri/api/book/index",
                                hreflang: 'en_US',
                                type: 'application/hal+json'
                        ]
                ]
        ])
    }

    void 'test render(view: "..", model: ..) in controllers with namespaces works'() {
        when: 'A request is sent to a controller with a namespace'
        def response = http('/api/book/testRender')

        then: 'The responseonse is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', [
                api: 'version 1.0 (Namespaced)',
                title: 'API - The Shining'
        ])
    }

    void 'test responseond(foo, view: ..) in controllers with namespaces works'() {
        when: 'A request is sent to a controller with a namespace'
        def response = http('/api/book/testRespond')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', [
                api: 'version 1.0 (Namespaced)',
                title: 'API - The Shining'

        ])
    }

    void 'test respond(foo, view: ..) in controllers with namespaces works, view outside of namespace'() {
        when: 'A request is sent to a controller with a namespace'
        def response = http('/api/book/testRespondOutsideNamespace')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', [
                api: 'version 1.0 (Non-Namespaced)',
                title: 'API - The Shining'
        ])
    }

    @Issue('https://github.com/apache/grails-views/issues/186')
    void 'test view rendering with a namespace from a map'() {
        when: 'A request is sent to a controller with a namespace'
        def response = http('/api/book/message')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type':'application/json;charset=UTF-8', [
                message: 'Controller says Hello API'
        ])
    }
}
