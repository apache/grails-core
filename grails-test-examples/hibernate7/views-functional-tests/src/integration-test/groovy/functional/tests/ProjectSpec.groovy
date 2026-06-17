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

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
@Tag('http-client')
class ProjectSpec extends Specification implements HttpClientSupport {

    void "Test that circular references are correctly rendered for many to many relationship"() {
        when: "A GET is issued"
        def response = http('/project')

        then:
        response.assertStatus(200)

        when:
        def project = response.json()

        then: "The correct response is returned"
        project.id == 1
        project.name == 'Grails Views'
        project.employees.find { it.id == 1 }.name == 'James Kleeh'
        project.employees.find { it.id == 1 }.project == null
        project.employees.find { it.id == 2 }.name == 'Iván López'
        project.employees.find { it.id == 2 }.project == null
    }
}
