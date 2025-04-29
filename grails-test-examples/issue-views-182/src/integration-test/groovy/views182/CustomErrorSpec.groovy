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

package views182

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException

@Integration
@Rollback
class CustomErrorSpec extends HttpClientCommonSpec {

    @OnceBefore
    void init() {
        this.baseUrl = "http://localhost:$serverPort"
        this.client = HttpClient.create(new URL(baseUrl))
    }

    void 'it is possible to use gson views for handling exception errors'() {
        when: 'executing get to custom error'
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/customError"), Argument.of(String), Argument.of(String))

        then:
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.body() == '{"message":"My custom exception handler","error":500}'
    }
}
