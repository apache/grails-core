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

package pubsub.demo

import grails.testing.mixin.integration.Integration
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 30/05/2017.
 */
@Integration
class TaskControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    HttpClient client

    void setup() {
        client = HttpClient.create("http://localhost:$serverPort".toURL())
    }

    void 'test async error handling'() {

        when: 'we invoke an endpoint that throws an exception'
            def request = HttpRequest.GET('/task/error')
            client.toBlocking().exchange(request, Argument.of(String), Argument.of(String))

        then: 'the response is as expected'
            def e = thrown(HttpClientResponseException)
            e.response.status == HttpStatus.INTERNAL_SERVER_ERROR
            e.response.body() == 'error occurred'
    }
}
