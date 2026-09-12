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

import grails.util.GrailsWebMockUtil
import grails.web.mapping.AbstractUrlMappingsSpec
import grails.web.mapping.LinkGenerator

import org.grails.web.util.HiddenHttpMethod

/**
 * A link built without an explicit method takes the method of the request being handled, and reverse
 * mapping is a routing decision, so it takes the overridden one. Under the servlet filter the request
 * itself reports that method; with the override resolved in the dispatcher it arrives as an attribute.
 * Either way the same link comes out, which is the point.
 */
class EffectiveMethodLinkGenerationSpec extends AbstractUrlMappingsSpec {

    private LinkGenerator generator() {
        getLinkGenerator {
            '/books'(controller: 'book', action: 'save', method: 'POST')
            '/books/updated'(controller: 'book', action: 'save', method: 'PUT')
        }
    }

    void 'a link takes the method the dispatcher resolved'() {
        given:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.method = 'POST'
        webRequest.request.addParameter('_method', 'PUT')
        webRequest.request.setAttribute(HiddenHttpMethod.OVERRIDDEN_METHOD_ATTRIBUTE, 'PUT')

        expect: 'the PUT mapping, as under the servlet filter'
        generator().link(controller: 'book', action: 'save') == '/books/updated'
    }

    void 'a link takes the request method when nothing was overridden'() {
        given:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.method = 'POST'

        expect:
        generator().link(controller: 'book', action: 'save') == '/books'
    }

    void 'an explicit method attribute still wins'() {
        given:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.method = 'POST'
        webRequest.request.setAttribute(HiddenHttpMethod.OVERRIDDEN_METHOD_ATTRIBUTE, 'PUT')

        expect:
        generator().link(controller: 'book', action: 'save', method: 'POST') == '/books'
    }
}
