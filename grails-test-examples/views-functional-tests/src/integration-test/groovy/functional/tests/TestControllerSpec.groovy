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

package functional.tests

import grails.testing.mixin.integration.Integration
import grails.testing.spock.RunOnce
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientException
import org.junit.jupiter.api.BeforeEach
import spock.lang.Issue

@Integration(applicationClass = Application)
class TestControllerSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    @Issue('https://github.com/grails/grails-core/issues/10582')
    void 'test responding after an action triggered by a HTTP 401 response is possible'() {
        when:
        HttpRequest request = HttpRequest.GET("/test/triggerUnauthorized")
        HttpResponse<String> resp = client.toBlocking().exchange(request, Argument.of(String), Argument.of(String))

        then: 'the response is correct'
        HttpClientException e = thrown()
        e.response.status == HttpStatus.UNAUTHORIZED
        e.response.body() == '{"message":"Unauthorized GSON"}'
    }
}
