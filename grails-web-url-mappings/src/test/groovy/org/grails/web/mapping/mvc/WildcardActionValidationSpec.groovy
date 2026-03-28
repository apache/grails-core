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
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Issue

class WildcardActionValidationSpec extends AbstractUrlMappingsSpec {

    void "wildcard action that matches a real action resolves to that action"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(TopicController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/$action"(controller: 'topic')
            "/$id"(controller: 'topic', action: 'show') {
                constraints {
                    id(matches: /\d+/)
                }
            }
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)

        when: "requesting a URI that matches a real action name"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/gallery")
        def handler = new UrlMappingsHandlerMapping(holder)
        def handlerChain = handler.getHandler(request)

        then: "the action is resolved via the wildcard action mapping"
        handlerChain != null
        handlerChain.handler instanceof GrailsControllerUrlMappingInfo
        (handlerChain.handler as GrailsControllerUrlMappingInfo).info.actionName == 'gallery'
    }

    void "wildcard action that does not match falls through to next mapping"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(TopicController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/$action"(controller: 'topic')
            "/$id"(controller: 'topic', action: 'show') {
                constraints {
                    id(matches: /\d+/)
                }
            }
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)

        when: "requesting a URI with a numeric id that is not an action name"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/42")
        def handler = new UrlMappingsHandlerMapping(holder)
        def handlerChain = handler.getHandler(request)

        then: "the request falls through to the id mapping and resolves to the show action"
        handlerChain != null
        handlerChain.handler instanceof GrailsControllerUrlMappingInfo
        (handlerChain.handler as GrailsControllerUrlMappingInfo).info.actionName == 'show'
    }

    void "wildcard controller and action are validated against registered controllers"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(TopicController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/$controller/$action?"()
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)

        when: "requesting a valid controller and action"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/topic/gallery")
        def handler = new UrlMappingsHandlerMapping(holder)
        def handlerChain = handler.getHandler(request)

        then: "the mapping resolves"
        handlerChain != null
        handlerChain.handler instanceof GrailsControllerUrlMappingInfo

        when: "requesting a non-existent controller"
        RequestContextHolder.resetRequestAttributes()
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        request = webRequest.request
        request.setRequestURI("/nonexistent/foo")
        handler = new UrlMappingsHandlerMapping(holder)
        handlerChain = handler.getHandler(request)

        then: "no handler is found"
        handlerChain == null
    }

    void "hardcoded controller and action mappings are not filtered even if controller is not registered"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(TopicController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/legacy"(controller: 'legacy', action: 'index')
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)

        when: "requesting a hardcoded mapping to a non-registered controller"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setRequestURI("/legacy")
        def infos = holder.matchAll("/legacy")

        then: "the mapping is still returned (backward compatible — not filtered)"
        infos.length == 1
    }

    void "wildcard validation can be disabled via config"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(TopicController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/$action"(controller: 'topic')
            "/$id"(controller: 'topic', action: 'show') {
                constraints {
                    id(matches: /\d+/)
                }
            }
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)
        holder.validateWildcardMappings = false

        when: "requesting a URI that is not a valid action with validation disabled"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def infos = holder.matchAll("/42")

        then: "the wildcard action mapping is NOT filtered out (old behavior)"
        infos.length == 2
    }

    void "match() also benefits from wildcard filtering"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(TopicController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            "/$action"(controller: 'topic')
            "/$id"(controller: 'topic', action: 'show') {
                constraints {
                    id(matches: /\d+/)
                }
            }
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)

        when: "matching a URI that is not a valid action"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def info = holder.match("/42")

        then: "the result falls through to the id mapping"
        info != null
        info instanceof GrailsControllerUrlMappingInfo
        info.actionName == 'show'
    }

    void "explicit method-specific mapping beats wildcard optional action match"() {
        given:
        def grailsApplication = new DefaultGrailsApplication(InviteController)
        grailsApplication.initialise()
        def holder = getUrlMappingsHolder {
            post "/invites"(controller: 'invite', action: 'create')
            "/invites/$action?"(controller: 'invite')
        }
        holder = new GrailsControllerUrlMappings(grailsApplication, holder)

        when: "posting to /invites"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.renderView = true
        def request = webRequest.request
        request.setMethod("POST")
        request.setRequestURI("/invites")
        def handler = new UrlMappingsHandlerMapping(holder)
        def handlerChain = handler.getHandler(request)

        then: "the explicit POST mapping wins over the wildcard optional action"
        handlerChain != null
        handlerChain.handler instanceof GrailsControllerUrlMappingInfo
        (handlerChain.handler as GrailsControllerUrlMappingInfo).info.actionName == 'create'
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }
}

@Artefact('Controller')
class InviteController {

    static allowedMethods = [create: 'POST']

    @Action
    def manage() {
        [invites: [], stats: [:]]
    }

    @Action
    def create() {
        [success: true]
    }
}

@Artefact('Controller')
class TopicController {

    @Action
    def gallery() {
        [view: 'gallery']
    }

    @Action
    def create() {
        [view: 'create']
    }

    @Action
    def save() {
        [view: 'save']
    }

    @Action
    def show() {
        [view: 'show']
    }
}
