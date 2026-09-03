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

import org.springframework.mock.web.MockHttpServletRequest

import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.Controller
import grails.web.mapping.AbstractUrlMappingsSpec
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappings
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * The controller, action and namespace a mapping captures from the URI are a function of the mapping
 * and the URI that matched it: they resolve from the match itself, whether or not the request has been
 * configured, and a request parameter of the same name does not stand in for a token the URI did not
 * supply. A mapping that computes a name with a closure of its own keeps reading request state, which
 * is the documented way to route on something other than the URI.
 */
class UrlMappingNameResolutionSpec extends AbstractUrlMappingsSpec {

    void 'resolves controller, action and id captured by a dynamic mapping'() {
        when: 'a request matches the default dynamic mapping'
        def mappingInfo = dispatch('/article/show/42', {
            "/$controller/$action?/$id?"()
        }, ArticleController)

        then: 'every name comes from the segment of the URI that captured it'
        mappingInfo != null
        with(mappingInfo) {
            controllerName == 'article'
            actionName == 'show'
            id == '42'
        }
    }

    void 'resolves a namespace captured by a dynamic mapping'() {
        when: 'a request matches a mapping that captures the namespace'
        def mappingInfo = dispatch('/admin/bulletin/list', {
            "/$namespace/$controller/$action?"()
        }, BulletinController)

        then: 'the namespaced controller is selected'
        mappingInfo != null
        with(mappingInfo) {
            namespace == 'admin'
            controllerName == 'bulletin'
            actionName == 'list'
        }
    }

    void 'resolves captured names without configuring the request'() {
        given: 'a bound request that has not been configured for any match'
        UrlMappings mappingsHolder = createUrlMappingsHolder({
            "/$controller/$action?/$id?"()
        }, ArticleController)
        GrailsWebMockUtil.bindMockWebRequest()

        when: 'the candidates for a URI are collected'
        UrlMappingInfo[] mappingInfos = mappingsHolder.matchAll('/article/show/42', 'GET', UrlMapping.ANY_VERSION)

        then: 'the names are answerable from the match alone'
        mappingInfos.length == 1
        with(mappingInfos.first()) {
            controllerName == 'article'
            actionName == 'show'
            !nameResolutionRequestDependent
        }
    }

    void 'a request parameter does not supply a name the URI did not capture'() {
        when: 'a request carries an action parameter and the matched URI has no segment for it'
        def mappingInfo = dispatch('/article', [action: 'gallery'], {
            "/$controller/$action?"()
        }, ArticleController)

        then: 'the mapping routes to the controller with no action, leaving the default action to run'
        mappingInfo != null
        with(mappingInfo) {
            controllerName == 'article'
            actionName != 'gallery'
            !actionName
        }
    }

    void 'a name closure reads a parameter the URI captures'() {
        when: 'a mapping computes its action from a token the URI captures'
        def mappingInfo = dispatch('/articles/gallery', {
            "/articles/$section"(controller: 'article') {
                action = { params.section }
            }
        }, ArticleController)

        then: 'the closure sees the captured value and the action resolves through it'
        mappingInfo != null
        with(mappingInfo) {
            controllerName == 'article'
            actionName == 'gallery'
            nameResolutionRequestDependent
        }
    }

    void 'a name closure reads a request parameter'() {
        when: 'a mapping computes its action from a request parameter, as the guide documents'
        def mappingInfo = dispatch('/article', [goHere: 'gallery'], {
            "/$controller" {
                action = { params.goHere }
            }
        }, ArticleController)

        then: 'the action comes from the request parameter'
        mappingInfo != null
        with(mappingInfo) {
            controllerName == 'article'
            actionName == 'gallery'
        }
    }

    private UrlMappings createUrlMappingsHolder(Closure<?> mappings, Class<?>... controllerClasses) {
        def grailsApplication = new DefaultGrailsApplication(controllerClasses).tap {
            initialise()
        }
        new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder(mappings))
    }

    private UrlMappingInfo dispatch(String requestURI, Closure<?> mappings, Class<?>... controllerClasses) {
        dispatch(requestURI, [:], mappings, controllerClasses)
    }

    private UrlMappingInfo dispatch(String requestURI, Map<String, String> parameters, Closure<?> mappings, Class<?>... controllerClasses) {
        UrlMappings mappingsHolder = createUrlMappingsHolder(mappings, controllerClasses)
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest()
        MockHttpServletRequest request = webRequest.request as MockHttpServletRequest
        request.requestURI = requestURI
        request.method = 'GET'
        parameters.each { String name, String value -> request.addParameter(name, value) }
        new UrlMappingsHandlerMapping(mappingsHolder).getHandler(request)?.handler as UrlMappingInfo
    }
}

@Controller
class ArticleController {

    def index() {}

    def gallery() {}

    def show() {}
}

@Controller
class BulletinController {

    static namespace = 'admin'

    def index() {}

    def list() {}
}
