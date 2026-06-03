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

package gorm

import grails.testing.mixin.integration.Integration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Tag

import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Functional test reproducing issue 15681 end-to-end: a real Grails application binds request parameters
 * (including {@code id} and {@code version}) to a domain class that extends an abstract {@code @DirtyCheck}
 * base. The framework must not bind {@code id} or {@code version} by default.
 */
@Integration
@Tag('http-client')
class DirtyCheckBindingSpec extends Specification implements HttpClientSupport {

    private static final String FORM = 'application/x-www-form-urlencoded'

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'bindData over HTTP does not bind id or version on a domain extending a @DirtyCheck base'() {
        when: 'a form submission posts id, version and a regular property'
        def response = httpPost('/dirtyCheckBinding/bind', 'id=99&version=5&description=Opening+balance', FORM)

        then: 'the request succeeds'
        response.assertStatus(200)

        and: 'the regular property is bound'
        response.assertContains('description=Opening balance')

        and: 'id and version are not bound, matching the documented default behaviour'
        response.assertContains('id=null')
        response.assertContains('version=null')
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'binding only a regular property over HTTP leaves id and version null'() {
        when:
        def response = httpPost('/dirtyCheckBinding/bind', 'description=Closing+balance', FORM)

        then:
        response.assertStatus(200)
        response.assertContains('description=Closing balance')
        response.assertContains('id=null')
        response.assertContains('version=null')
    }
}
