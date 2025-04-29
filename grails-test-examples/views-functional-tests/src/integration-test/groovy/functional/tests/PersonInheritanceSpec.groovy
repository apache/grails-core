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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.BeforeEach
import spock.lang.Issue

@Integration(applicationClass = Application)
class PersonInheritanceSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void 'test template inheritance produces correct json'() {
        when:
        HttpRequest request = HttpRequest.GET('/person-inheritance')
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then: 'the response is correct'
        rsp.status() == HttpStatus.OK
        rsp.body() == '{"dob":"01/01/1970","lastName":"Doe","firstName":"John"}'
    }

    @Issue("https://github.com/grails/grails-views/issues/234")
    void 'test template inheritance does not produce NPE when model variable is null'() {
        when:
        HttpRequest request = HttpRequest.GET('/person-inheritance/npe')
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then: 'the response is correct'
        noExceptionThrown()
        rsp.status() == HttpStatus.OK

    }
}
