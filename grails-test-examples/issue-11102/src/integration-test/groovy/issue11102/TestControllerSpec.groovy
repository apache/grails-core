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

package issue11102

import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient

@Integration
class TestControllerSpec extends HttpClientCommonSpec {

    @OnceBefore
    void init() {
        this.baseUrl = "http://localhost:$serverPort"
        this.client = HttpClient.create(new URL(baseUrl))
    }

    void 'can forward a request from a GET to another GET action'() {
        when: 'getting the get1 action'
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/get1"), String)

        then: 'it is executed correctly'
        response.status == HttpStatus.OK
        response.body() == 'GET1'

        when: 'executing an action with a forward to the other one'
        HttpResponse<String> response2 = client.toBlocking().exchange(HttpRequest.GET("/get2"), String)

        then: 'the request is forwarded'
        response2.status == HttpStatus.OK
        response.body() == 'GET1'
    }
}
