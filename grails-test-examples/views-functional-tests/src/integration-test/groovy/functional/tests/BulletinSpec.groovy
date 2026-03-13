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

import spock.lang.Issue
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
class BulletinSpec extends Specification implements HttpClientSupport {

    @Issue('https://github.com/apache/grails-views/issues/175')
    void 'test render collections with same objects'() {
        when: 'a GET is issued'
        def response = http('/bulletin')

        then: 'The REST resource is retrieved and the correct JSON is returned'
        response.expectStatus(200)
        def json = response.json()
        json.content == 'Hi everyone!'

        and: 'the username is the same as the publicId'
        json.targetUsers.size() == 2
        json.targetUsers.every { it.username == it.publicId }
        json.contactUsers.size() == 3
        json.contactUsers.every { it.username == it.publicId }
    }
}
