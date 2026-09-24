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

import jakarta.servlet.RequestDispatcher

import grails.artefact.Artefact
import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil
import grails.util.Holders
import grails.web.Action
import grails.web.mapping.AbstractUrlMappingsSpec
import grails.web.mapping.UrlMappingInfo

import org.grails.config.PropertySourcesConfig
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.HiddenHttpMethod

/**
 * With the hidden HTTP method filter disabled, the handler mapping resolves the '_method' parameter itself
 * while matching, so a browser form POST still reaches the PUT, PATCH and DELETE routes of a 'resources'
 * mapping -- without the request method being rewritten ahead of the dispatcher.
 */
class HiddenHttpMethodHandlerMappingSpec extends AbstractUrlMappingsSpec {

    void 'a form POST carrying _method reaches the DELETE route'() {
        given:
        def handler = bookHandlerMapping(true)

        when: 'a plain form POST to the member URL, naming DELETE'
        UrlMappingInfo info = match(handler, 'POST', '/books/1', 'DELETE')

        then:
        info.actionName == 'delete'
        info.controllerName == 'book'
    }

    void 'a form POST carrying _method reaches the PUT route'() {
        given:
        def handler = bookHandlerMapping(true)

        when:
        UrlMappingInfo info = match(handler, 'POST', '/books/1', 'PUT')

        then:
        info.actionName == 'update'
    }

    void 'a form POST carrying _method reaches the PATCH route'() {
        given:
        def handler = bookHandlerMapping(true)

        when:
        UrlMappingInfo info = match(handler, 'POST', '/books/1', 'PATCH')

        then:
        info.actionName == 'patch'
    }

    void 'the parameter does not select a route for an internal dispatch'() {
        given: 'the filter is off, so the override would be resolved for a request arriving from outside'
        def handler = bookHandlerMapping(true)

        when: 'the dispatch is one the dispatcher leaves the override alone for, inheriting the parameter'
        UrlMappingInfo info = matchInternalDispatch(handler, '/books/1', 'DELETE', attribute, value)

        then: 'it routes as the POST it is, rather than as the method the original request asked for'
        info.actionName == 'update'

        where: 'an error dispatch is not among them - it is matched by status code and never gets this far'
        attribute                             | value
        RequestDispatcher.FORWARD_REQUEST_URI | '/books/1'
        RequestDispatcher.INCLUDE_REQUEST_URI | '/books/1'
    }

    void 'an override the dispatcher resolved survives an internal dispatch'() {
        given: 'the state after a form POST naming PUT was routed and the action forwarded'
        def handler = bookHandlerMapping(true)

        when:
        UrlMappingInfo info = matchInternalDispatch(handler, '/books/1', 'PUT', attribute, value, true)

        then: 'the forward routes as PUT, the same answer the servlet filter wrapper gives for the whole request'
        info.actionName == 'update'

        and: 'and matches the method-keyed reader, so nothing disagrees within one dispatch'
        HiddenHttpMethod.effectiveMethod(GrailsWebRequest.lookup().request) == 'PUT'

        where:
        attribute                             | value
        RequestDispatcher.FORWARD_REQUEST_URI | '/books/1'
        RequestDispatcher.INCLUDE_REQUEST_URI | '/books/1'
    }

    void 'an override the mapping resolved itself is published for allowedMethods to read'() {
        given: 'no dispatcher resolved it first - a stock DispatcherServlet, or this mapping on its own'
        def handler = bookHandlerMapping(true)

        when:
        UrlMappingInfo info = match(handler, 'POST', '/books/1', 'DELETE')

        then: 'it routes as DELETE'
        info.actionName == 'delete'

        and: 'and everything reading the effective method agrees, so the action is not then refused a 405'
        HiddenHttpMethod.effectiveMethod(GrailsWebRequest.lookup().request) == 'DELETE'
    }

    void 'a method-keyed action name is selected by the overridden method'() {
        given: 'a mapping naming a different action per HTTP method'
        def handler = methodKeyedHandlerMapping()

        when: 'a form POST names PUT and the dispatcher has resolved the override'
        UrlMappingInfo info = matchWithResolvedOverride(handler, '/books', 'PUT')

        then: 'the name is the one the overridden method keys, as it is under the servlet filter'
        info.actionName == 'save'
    }

    void 'a method-keyed action name falls back to the method the request arrived as'() {
        given: 'nothing asked for an override'
        def handler = methodKeyedHandlerMapping()

        when:
        UrlMappingInfo info = match(handler, 'POST', '/books', null)

        then:
        info.actionName == 'update'
    }

    void 'the parameter is ignored while the filter is doing the rewriting'() {
        given: 'the default, where the servlet filter has already rewritten the method'
        def handler = bookHandlerMapping(false)

        when: 'a POST still carrying the parameter reaches the mapping unrewritten'
        UrlMappingInfo info = match(handler, 'POST', '/books/1', 'DELETE')

        then: 'nothing matches, because no POST route exists for the member URL'
        info == null
    }

    void 'only the methods a browser form cannot send are overridable'() {
        given: 'unlike the servlet filter, which applies any method name it is given'
        def handler = bookHandlerMapping(true)

        expect: 'the override is refused and the request stays a POST, so it can never become a read'
        match(handler, 'POST', '/books/1', 'GET').actionName == 'update'
        match(handler, 'POST', '/books/1', 'TRACE').actionName == 'update'

        and: 'and a GET to the member URL still reaches show, not anything the parameter asked for'
        match(handler, 'GET', '/books/1', 'DELETE').actionName == 'show'
    }

    void 'a POST to the member URL reaches update without any _method parameter'() {
        given: 'the shape AngularJS $resource and the clients modelled on it use to save an existing object'
        def handler = bookHandlerMapping(true)

        when:
        UrlMappingInfo info = match(handler, 'POST', '/books/1', null)

        then: 'RestfulController has permitted POST for update since #9926; this is the route for it'
        info.actionName == 'update'
        info.controllerName == 'book'
        info.id == '1'
    }

    void 'the collection URL still routes a POST to save'() {
        given:
        def handler = bookHandlerMapping(true)

        expect: 'create and update stay separated by URL, exactly as $resource separates them'
        match(handler, 'POST', '/books', null).actionName == 'save'
    }

    void 'no POST route is added to the member URL while the filter is enabled'() {
        given: 'with the filter on, every path to update is rewritten to PUT before Spring Security sees it'
        def handler = bookHandlerMapping(false)

        expect: 'so the route is not generated, and the security posture of existing applications is unchanged'
        match(handler, 'POST', '/books/1', null) == null
    }

    void 'a real DELETE is unaffected'() {
        given:
        def handler = bookHandlerMapping(true)

        expect: 'an API client sending the real method routes as it always did'
        match(handler, 'DELETE', '/books/1', null).actionName == 'delete'
    }

    void cleanup() {
        // setConfig publishes to the static Holders, which would otherwise leak into sibling tests
        Holders.clear()
    }

    /**
     * @param filterDisabled whether the application has switched the hidden HTTP method filter off, which
     *                       both moves the override into the request path and generates the POST update route
     */
    private UrlMappingsHandlerMapping bookHandlerMapping(boolean filterDisabled) {
        def config = new PropertySourcesConfig()
        config.merge([(Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED): !filterDisabled])

        def grailsApplication = new DefaultGrailsApplication(BookController)
        grailsApplication.config = config
        grailsApplication.initialise()

        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, grailsApplication)
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def holder = new DefaultUrlMappingsHolder(evaluator.evaluateMappings {
            "/books"(resources: 'book')
        })

        def handler = new UrlMappingsHandlerMapping(new GrailsControllerUrlMappings(grailsApplication, holder))
        handler.resolveHiddenHttpMethod = filterDisabled
        handler
    }

    /**
     * Matches with the override already published, which is the state the dispatcher leaves behind before
     * the handler mapping runs. Under the servlet filter the request itself reports the overridden method
     * and the attribute is absent, so both modes reach the same answer through
     * {@link HiddenHttpMethod#effectiveMethod}.
     */
    private static UrlMappingInfo matchWithResolvedOverride(UrlMappingsHandlerMapping handler, String uri,
                                                            String override) {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request
        request.method = 'POST'
        request.requestURI = uri
        request.addParameter('_method', override)
        request.setAttribute(HiddenHttpMethod.OVERRIDDEN_METHOD_ATTRIBUTE, override)
        handler.getHandler(request)?.handler as UrlMappingInfo
    }

    private UrlMappingsHandlerMapping methodKeyedHandlerMapping() {
        def config = new PropertySourcesConfig()
        config.merge([(Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED): false])

        def grailsApplication = new DefaultGrailsApplication(BookController)
        grailsApplication.config = config
        grailsApplication.initialise()

        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, grailsApplication)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            '/books' {
                controller = 'book'
                action = [GET: 'index', POST: 'update', PUT: 'save']
            }
        })

        def handler = new UrlMappingsHandlerMapping(new GrailsControllerUrlMappings(grailsApplication, holder))
        handler.resolveHiddenHttpMethod = true
        handler
    }

    private static UrlMappingInfo matchInternalDispatch(UrlMappingsHandlerMapping handler, String uri,
                                                        String override, String attribute, Object value,
                                                        boolean dispatcherResolved = false) {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request
        request.method = 'POST'
        request.requestURI = uri
        request.addParameter('_method', override)
        if (dispatcherResolved) {
            request.setAttribute(HiddenHttpMethod.OVERRIDDEN_METHOD_ATTRIBUTE, override)
        }
        request.setAttribute(attribute, value)
        handler.getHandler(request)?.handler as UrlMappingInfo
    }

    private static UrlMappingInfo match(UrlMappingsHandlerMapping handler, String method, String uri, String override) {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request
        request.method = method
        request.requestURI = uri
        if (override) {
            request.addParameter('_method', override)
        }
        handler.getHandler(request)?.handler as UrlMappingInfo
    }
}

@Artefact('Controller')
class BookController {
    @Action def index() {}
    @Action def show() {}
    @Action def create() {}
    @Action def save() {}
    @Action def edit() {}
    @Action def update() {}
    @Action def patch() {}
    @Action def delete() {}
}
