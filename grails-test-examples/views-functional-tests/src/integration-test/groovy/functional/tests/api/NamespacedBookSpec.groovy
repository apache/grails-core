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

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import spock.lang.Issue
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import grails.web.http.HttpHeaders
import org.apache.grails.testing.httpclient.HttpClientSupport

@Integration
class NamespacedBookSpec extends Specification implements HttpClientSupport {

    @Autowired
    ObjectMapper objectMapper

    void 'test view rendering with a namespace'() {
        when: 'A request is sent to a controller with a namespace'
        def rsp = httpClient.exchange('/api/book', Map)

        then: 'The rsponse is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().api == 'version 1.0 (Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    void 'test nested template rendering with a namespace'() {
        when: 'A request is sent to a controller with a namespace'
        def rsp = httpClient.exchange('/api/book/nested', Map)

        then: 'The response contains the child template'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().foo == 'bar'
    }

    void 'test the correct content type is chosen (json)'() {
        when: 'A request is sent to a controller with a namespace'
        def rsp = httpClient.exchange('/api/book', Map)

        then: 'The response contains the child template'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        !rsp.body()['_links']
        rsp.body().api == 'version 1.0 (Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    void 'test the correct content type is chosen (hal)'() {
        when: 'A request is sent to a controller with a namespace'
        def rsp = httpClient.exchange(
                HttpRequest.GET('/api/book').accept(MediaType.APPLICATION_HAL_JSON_TYPE),
                String
        )
        def body = objectMapper.readValue(rsp.body(), Map)

        then: 'The response contains the child template'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/hal+json;charset=UTF-8'
        body['_links']
        body.api == 'version 1.0 (Namespaced HAL)'
        body.title == 'API - The Shining'
    }

    void 'test render(view: "..", model: ..) in controllers with namespaces works'() {
        when: 'A request is sent to a controller with a namespace'
        def rsp = httpClient.exchange('/api/book/testRender', Map)

        then: 'The rsponse is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().api == 'version 1.0 (Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    void 'test rspond(foo, view: ..) in controllers with namespaces works'() {
        when: 'A request is sent to a controller with a namespace'
        def rsp = httpClient.exchange('/api/book/testRespond', Map)

        then: 'The response is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().api == 'version 1.0 (Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    void 'test respond(foo, view: ..) in controllers with namespaces works, view outside of namespace'() {
        when: 'A request is sent to a controller with a namespace'
        def rsp = httpClient.exchange('/api/book/testRespondOutsideNamespace', Map)

        then: 'The response is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().api == 'version 1.0 (Non-Namespaced)'
        rsp.body().title == 'API - The Shining'
    }

    @Issue('https://github.com/apache/grails-views/issues/186')
    void 'test view rendering with a namespace from a map'() {
        when: 'A request is sent to a controller with a namespace'
        def rsp = httpClient.exchange('/api/book/message', Map)

        then: 'The response is correct'
        rsp.status() == HttpStatus.OK
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        rsp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        rsp.body().message == 'Controller says Hello API'
    }
}
