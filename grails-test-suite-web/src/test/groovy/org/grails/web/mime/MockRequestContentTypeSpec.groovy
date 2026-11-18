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
package org.grails.web.mime

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * A unit test request resolves its content type as an application request does.
 */
class MockRequestContentTypeSpec extends Specification implements ControllerUnitTest<MockRequestContentTypeController> {

    void 'a form body of a #method request with a content type in any case binds its parameters'() {
        given:
        request.method = method
        request.contentType = 'Application/X-WWW-Form-Urlencoded'
        request.content = 'a=1'.getBytes('UTF-8')

        expect:
        params.a == '1'

        where:
        method << ['PUT', 'PATCH']
    }

    void 'the request format follows a content type set after it was read'() {
        given:
        request.contentType = 'application/json'
        assert request.format == 'json'

        when:
        request.contentType = 'application/xml'

        then:
        request.format == 'xml'
    }

    void 'a request format a test sets is kept when the content type is set'() {
        given:
        request.format = 'json'

        when:
        request.contentType = 'application/xml'

        then:
        request.format == 'json'
    }
}

@Artefact('Controller')
class MockRequestContentTypeController { }
