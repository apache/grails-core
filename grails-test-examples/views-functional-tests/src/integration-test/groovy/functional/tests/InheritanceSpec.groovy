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
import grails.web.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach

@Integration(applicationClass = Application)
class InheritanceSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "Test template is found for proxy instance that is initialized"() {
        when:
        HttpRequest request = HttpRequest.GET('/inheritance/multiNested')
        HttpResponse<String> resp = client.toBlocking().exchange(request, String)


        then:"The response is correct"
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'

        // Note current behaviour is that the captain is not rendered twice
        resp.body() == '{"levelTwo":true,"levelOne":true,"entry":true}'
    }
}
