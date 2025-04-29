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

@Integration(applicationClass = Application)
class ProjectSpec extends HttpClientSpec {

    @RunOnce
    @BeforeEach
    void init() {
        super.init()
    }

    void "Test that circular references are correctly rendered for many to many relationship"() {
        given:"A rest client"

        when:"A POST is issued"
        HttpRequest request = HttpRequest.GET('/project')
        HttpResponse<Map> rsp = client.toBlocking().exchange(request, Map)

        then:
        rsp.status() == HttpStatus.OK

        when:
        Map project = rsp.body()

        then:"The correct response is returned"
        project.id == 1
        project.name == "Grails Views"
        project.employees.find { it.id == 1 }.name == "James Kleeh"
        project.employees.find { it.id == 1 }.project == null
        project.employees.find { it.id == 2 }.name == "Iván López"
        project.employees.find { it.id == 2 }.project == null
    }
}
