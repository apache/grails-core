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
package org.grails.web.mapping.mvc

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.Action
import grails.web.mapping.AbstractUrlMappingsSpec
import spock.lang.Issue

class EncodePathFromURISpec extends AbstractUrlMappingsSpec {

    @Issue('#10936')
    void 'The id parameter in a Path is not encoded as a URL but a URI'() {
        given: 'a URL Mapping'
        def grailsApplication = new DefaultGrailsApplication(BarController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/bar/$id"(controller:"bar", action:"bar")
        }

        holder = new GrailsControllerUrlMappings(grailsApplication, holder)
        def handler = new UrlMappingsHandlerMapping(holder)

        when: 'a request'
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request
        request.setRequestURI("/bar/$uriId")
        def handlerChain = handler.getHandler(request)

        def handlerAdapter = new UrlMappingsInfoHandlerAdapter()
        def result = handlerAdapter.handle(request, webRequest.response, handlerChain.handler)

        then: 'the id parameter is not encoded as a URL'
        webRequest.parameterMap.id == '1+1'

        where:
        uriId << ['1+1', '1%2B1']
    }
}

@Artefact('Controller')
class BarController {

    static defaultAction = 'bar'

    @Action
    def bar() {
    }
}
