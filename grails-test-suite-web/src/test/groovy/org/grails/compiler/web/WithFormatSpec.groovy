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
package org.grails.compiler.web

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class WithFormatSpec extends Specification implements ControllerUnitTest<MimeTypesCompiledController> {

    void "Test withFormat method injected at compile time"() {
        when:
        response.format = 'html'
        def format = controller.index()

        then:
        format == "html"
        
        when:
        response.format = 'xml'
        format = controller.index()
        
        then:
        format == 'xml'
    }

    void 'a known format without a block of its own falls back to the Accept header'() {
        given: 'a format the action has no block for, as on 8.0.x'
        params.format = 'xml'
        request.addHeader('Accept', 'text/html')

        when:
        def format = controller.htmlOrJson()

        then:
        format == 'html'
        response.mimeTypes*.extension == ['html']
    }

    void 'a client that asks for a problem in XML, and next for Atom, is answered in Atom, as in Grails 8'() {
        given:
        request.addHeader('Accept', 'application/problem+xml, application/atom+xml')

        expect:
        response.format == 'atom'
    }
}

@Artefact('Controller')
class MimeTypesCompiledController {
    def index() {
        withFormat {
            html { "html" }
            xml { "xml" }
        }
    }

    def htmlOrJson() {
        withFormat {
            html { 'html' }
            json { 'json' }
        }
    }
}
