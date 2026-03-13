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

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
class TestControllerSpec extends Specification implements HttpClientSupport {

    void 'can forward a request from a GET to another GET action'() {
        when: 'getting the get1 action'
        def response = http('/get1')

        then: 'it is executed correctly'
        response.expect(200, 'GET1')

        when: 'executing an action with a forward to the other one'
        def response2 = http('/get2')

        then: 'the request is forwarded'
        response2.expect(200, 'GET1')
    }
}
