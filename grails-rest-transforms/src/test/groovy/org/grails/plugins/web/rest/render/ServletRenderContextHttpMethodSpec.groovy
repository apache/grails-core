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
package org.grails.plugins.web.rest.render

import grails.util.GrailsWebMockUtil

import org.grails.web.util.HiddenHttpMethod
import org.springframework.http.HttpMethod
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

/**
 * A renderer asks the context which method it is answering, and the answer is the method the action was
 * selected for. Under the servlet filter the request reports it; with the override resolved in the
 * dispatcher it arrives as an attribute, and a renderer sees the same thing either way.
 */
class ServletRenderContextHttpMethodSpec extends Specification {

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'the context reports the method the dispatcher resolved'() {
        given:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.method = 'POST'
        webRequest.request.addParameter('_method', 'PUT')
        webRequest.request.setAttribute(HiddenHttpMethod.OVERRIDDEN_METHOD_ATTRIBUTE, 'PUT')

        expect:
        new ServletRenderContext(webRequest).httpMethod == HttpMethod.PUT
    }

    void 'the context reports the request method when nothing was overridden'() {
        given:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.method = 'POST'

        expect:
        new ServletRenderContext(webRequest).httpMethod == HttpMethod.POST
    }
}
