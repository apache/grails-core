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
class CircularSpec extends Specification implements HttpClientSupport {

    void "test deep rendering of circular domain relationships"() {
        when: "A GET is issued"
        def response = http('/circular/show/1')

        then: "The REST resource is retrieved and the correct JSON is returned"
        response.assertHeaders(200, 'Content-Type': 'application/json;charset=UTF-8')
        with(response.json()) {
            id == 1
            name == 'topLevel'
            myEnum == 'BAR'
            circulars.size() == 2
            circulars.find { it.id == 3 }.parent.id == 1
            circulars.find { it.id == 2 }.parent.id == 1
        }
    }

    void "test nested template rendering of circular domain relationships"() {
        when: "A GET is issued"
        def response = http('/circular/circular/1')

        then: "The REST resource is retrieved and the correct JSON is returned"
        response.assertHeaders(200, 'Content-Type': 'application/json;charset=UTF-8')
        with(response.json()) {
            name == 'topLevel'
            children.size() == 2
            children.find { it.name == 'topLevel-3' }
            children.find { it.name == 'topLevel-2' }
        }
    }
}
