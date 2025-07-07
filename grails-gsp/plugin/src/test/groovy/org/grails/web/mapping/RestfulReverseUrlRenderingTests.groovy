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
package org.grails.web.mapping

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
class RestfulReverseUrlRenderingTests extends Specification implements UrlMappingsUnitTest<RestfulReverseUrlMappings> {


    def testLinkTagRendering() {
        when:
        def template = '<g:link controller="restfulCar">create</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/car">create</a>'
    }

    def testFormTagRendering() {
        when:
        def template = '<g:form controller="restfulCar" name="myForm" method="POST">save</g:form>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/car" method="post" name="myForm" id="myForm" >save</form>'
    }


    def testFormTagRenderGETRequest() {
        when:
        def template = '<g:form controller="restfulCar" name="myForm" method="GET">create</g:form>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/car" method="get" name="myForm" id="myForm" >create</form>'
    }
}

@Artefact('UrlMappings')
class RestfulReverseUrlMappings {
    static mappings = {
        '/car' (controller: 'restfulCar', action: [GET: 'create', POST: 'save'])
    }
}

@Artefact('Controller')
class RestfulCarController {
    def create = {}
    def save = {}
}
