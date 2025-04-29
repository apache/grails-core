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

import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingInfo
import grails.web.servlet.mvc.GrailsParameterMap
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.IncludedContent
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.ModelAndView
import spock.lang.Specification
import spock.lang.Unroll

class UrlMappingUtilsSpec extends Specification {

    private MockHttpServletRequest request
    private MockHttpServletResponse response
    private GrailsApplicationAttributes attr
    private GrailsWebRequest webRequest
    private LinkGenerator linkGenerator

    void setup() {
        request = new MockHttpServletRequest()
        response = new MockHttpServletResponse()
        attr = Mock(GrailsApplicationAttributes.class)
        linkGenerator = Mock(LinkGenerator.class)
        webRequest = new GrailsWebRequest(request, response, attr)
        webRequest.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView(), 0)
        request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, webRequest)
    }

    @Unroll
    void "test buildDispatchUrlForMapping"() {
        expect:
        expected == UrlMappingUtils.findAllParamsNotInUrlMappingKeywords(params)

        where:
        params                      | expected
        [id: 1, controller: 'home'] | [id: 1]
        [id: 1, format: 'json']     | [id: 1, format: 'json']
    }

    void "test includeForUrlMappingInfo when linkGenerator is passed in"() {
        given:
            final String retUrl = '/testAction'
            final UrlMappingInfo info = new ForwardUrlMappingInfo(controllerName: 'testController', actionName: 'testAction')
            final Map model = [:]

        when:
            final IncludedContent includedContent = UrlMappingUtils.includeForUrlMappingInfo(request, response, info, model, linkGenerator)
        then:
            1 * linkGenerator.link(_ as Map) >> { Map m -> return retUrl }
        and:
            includedContent
    }
}
