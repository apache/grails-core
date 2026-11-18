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

import spock.lang.Specification

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsFactory
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datamapping.core.querytail.QueryTailRequestAttribution
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.web.mapping.mvc.GrailsControllerUrlMappings
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.grails.web.mapping.mvc.UrlMappingsInfoHandlerAdapter
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils

/**
 * Attributes statements to the pages that URL mapping, as a running application does it, resolves requests to: no
 * value a mapping reads from a path reaches the capture, whatever names URL mapping leaves on the request.
 */
class QueryTailUrlMappingAttributionSpec extends Specification {

    /** The request attribute an action keeps the record of the statement it ran in. */
    static final String RECORD = QueryTailUrlMappingAttributionSpec.name + '.RECORD'

    /** The attribution of a capture, which numbers the requests it records apart from those of every other. */
    static final QueryTailRequestAttribution ATTRIBUTION = new QueryTailRequestAttribution()

    /**
     * Records a statement as an action runs one, with the attribution of a capture, and keeps the record on the request.
     */
    static void record() {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup()
        webRequest.request.setAttribute(RECORD, ATTRIBUTION.attribute(new QueryTailRecord(sql: 'select 1')))
    }

    void cleanup() {
        WebUtils.clearGrailsWebRequest()
    }

    void "a request a view serves, after a mapping read a controller and action from its path, is attributed to no page"() {
        given: 'a mapping that reads its controller and action from the path, and a controller of that name without that action'
        GrailsApplication grailsApplication = application(ResetController)
        UrlMappingsHandlerMapping handlerMapping = handlerMapping(grailsApplication) {
            "/$controller/$token" {
                action = { params.token }
            }
            "/reset/$token"(view: '/reset')
        }
        GrailsWebRequest webRequest = bind('/reset/k9fQ2xSecret')

        when: 'the view serves the request, and the controller named by the name the first mapping read is looked up'
        UrlMappingInfo info = (UrlMappingInfo) handlerMapping.getHandler(webRequest.request).handler
        webRequest.request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS,
                grailsApplication.getArtefactByLogicalPropertyName(ControllerArtefactHandler.TYPE, 'reset'))
        QueryTailRecord record = attribute()

        then: 'the names the request was left with are values of its path, which reach neither the page nor the request'
        info.viewName == '/reset'
        webRequest.controllerName == 'reset'
        webRequest.actionName == 'k9fQ2xSecret'
        record.page == null
        record.request == 'GET /?/?'
        !record.toLine().contains('k9fQ2xSecret')
    }

    void "a statement the controller a status is mapped to runs counts for the page whose action failed, without its values"() {
        given: 'a request served by an action'
        UrlMappingsHandlerMapping handlerMapping = handlerMapping(application(ShelfController, ErrorsController)) {
            "/shelf/show/$id"(controller: 'shelf', action: 'show')
            '404'(controller: 'errors', action: 'notFound')
        }
        GrailsWebRequest webRequest = bind('/shelf/show/42')
        QueryTailRecord served = attribute(handlerMapping.getHandler(webRequest.request).handler)

        when: 'the action sends a 404, which the container dispatches to the controller the status is mapped to'
        webRequest.request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, '404')
        QueryTailRecord errorDispatch = attribute(handlerMapping.getHandler(webRequest.request).handler)

        then: 'the action is recorded on its page, and the error dispatch on that page too, read against no mapping of the path'
        served.page == 'shelf/show'
        served.request == 'GET /shelf/show/?'
        errorDispatch.page == 'shelf/show'
        errorDispatch.request == 'GET /?/?/?'
        errorDispatch.requestId == served.requestId
        !errorDispatch.toLine().contains('42')
    }

    void "a path a greedy extension mapping matched is read against its tokens: #path"() {
        given:
        UrlMappingsHandlerMapping handlerMapping = handlerMapping(application(FileController)) {
            "/files/$filename+(.$format)?"(controller: 'file', action: 'download')
            "/docs/$name+?(.$format)?"(controller: 'file', action: 'docs')
        }
        GrailsWebRequest webRequest = bind(path)

        when:
        QueryTailRecord record = attribute(handlerMapping.getHandler(webRequest.request).handler)

        then:
        record.page == page
        record.request == recorded

        where:
        path                                    || page            | recorded
        '/files/document.final.v2.pdf'          || 'file/download' | 'GET /files/?'
        '/files/customer-secret'                || 'file/download' | 'GET /files/?'
        '/docs/user.service.v1.json'            || 'file/docs'     | 'GET /docs/?'
        '/docs'                                 || 'file/docs'     | 'GET /docs'
    }

    /**
     * Attributes a statement as the action a request is dispatched to runs it.
     */
    private static QueryTailRecord attribute(Object handler) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup()
        new UrlMappingsInfoHandlerAdapter().handle(webRequest.request, webRequest.response, handler)
        (QueryTailRecord) webRequest.request.getAttribute(RECORD)
    }

    /**
     * Attributes a statement run where nothing has been dispatched to an action.
     */
    private static QueryTailRecord attribute() {
        new QueryTailRequestAttribution().attribute(new QueryTailRecord(sql: 'select 1'))
    }

    private static GrailsApplication application(Class<?>... controllers) {
        new DefaultGrailsApplication(controllers).tap { initialise() }
    }

    private static UrlMappingsHandlerMapping handlerMapping(GrailsApplication grailsApplication, Closure<?> mappings) {
        new UrlMappingsHandlerMapping(new GrailsControllerUrlMappings(grailsApplication, new UrlMappingsFactory().create(mappings)))
    }

    private static GrailsWebRequest bind(String path) {
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.method = 'GET'
        webRequest.request.requestURI = path
        webRequest
    }
}

@Artefact('Controller')
class ResetController {

    def index() {
    }
}

@Artefact('Controller')
class ShelfController {

    def show() {
        QueryTailUrlMappingAttributionSpec.record()
    }
}

@Artefact('Controller')
class ErrorsController {

    def notFound() {
        QueryTailUrlMappingAttributionSpec.record()
    }
}

@Artefact('Controller')
class FileController {

    def download() {
        QueryTailUrlMappingAttributionSpec.record()
    }

    def docs() {
        QueryTailUrlMappingAttributionSpec.record()
    }
}
