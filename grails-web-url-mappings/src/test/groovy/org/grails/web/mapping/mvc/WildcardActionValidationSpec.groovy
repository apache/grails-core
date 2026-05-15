/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.web.mapping.mvc

import org.springframework.mock.web.MockHttpServletRequest

import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.Controller
import grails.web.HyphenatedUrlConverter
import grails.web.mapping.AbstractUrlMappingsSpec
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder

class WildcardActionValidationSpec extends AbstractUrlMappingsSpec {

    void 'resolves wildcard action mappings to real controller actions'() {
        when: 'evaluating a request for a declared action segment'
        def mappingInfo = evaluateRequestFor('/gallery', {
            "/$action"(controller: 'topic')
            "/$id"(controller: 'topic', action: 'show') {
                constraints {
                    id(matches: /\d+/)
                }
            }
        }, TopicController)

        then: 'the wildcard action mapping resolves the real controller and action'
        with(mappingInfo) {
            controllerName == 'topic'
            actionName == 'gallery'
        }
    }

    void 'falls through invalid wildcard action mappings to the next match'() {
        when: 'evaluating a request for a numeric segment that is not a real action'
        def mappingInfo = evaluateRequestFor('/42', {
            "/$action"(controller: 'topic')
            "/$id"(controller: 'topic', action: 'show') {
                constraints {
                    id(matches: /\d+/)
                }
            }
        }, TopicController)

        then: 'the id mapping is chosen instead of the invalid action mapping'
        with(mappingInfo) {
            controllerName == 'topic'
            actionName == 'show'
        }
    }

    void 'validates wildcard controller and action tokens against registered controllers'() {
        when: 'evaluating a request for a registered controller and action path'
        def mappingInfo = evaluateRequestFor('/topic/gallery', {
            "/$controller/$action?"()
        }, TopicController)

        then: 'the wildcard controller and action mapping resolves the requested action'
        with(mappingInfo) {
            controllerName == 'topic'
            actionName == 'gallery'
        }

        when: 'evaluating a request for an unregistered controller path'
        mappingInfo = evaluateRequestFor('/nonexistent/foo', {
            "/$controller/$action?"()
        }, TopicController)

        then: 'no mapping info is returned'
        !mappingInfo
    }

    void 'retains hard-coded controller and action mappings for unregistered controllers'() {
        given:
        def mappingsHolder = createUrlMappingsHolder({
            '/legacy'(controller: 'legacy', action: 'index')
        }, TopicController) // Note: no legacy controller registered

        when: 'matching all mappings for a hard-coded unregistered controller path'
        def mappingInfos = mappingsHolder.matchAll('/legacy')

        then: 'the hard-coded mapping remains available for backward compatibility'
        mappingInfos.length == 1
        with(mappingInfos.first()) {
            controllerName == 'legacy'
            actionName == 'index'
        }
    }

    void 'wildcard validation can be disabled via config'() {
        given:
        def mappingsHolder = createUrlMappingsHolder(false, {
            "/$action"(controller: 'topic')
            "/$id"(controller: 'topic', action: 'show') {
                constraints {
                    id(matches: /\d+/)
                }
            }
        }, TopicController)
        GrailsWebMockUtil.bindMockWebRequest() // Ensure a web request is bound for matchAll() to avoid NPE

        when: 'matching all mappings for a non-action path with wildcard validation disabled'
        def mappingInfos = mappingsHolder.matchAll('/42')

        then: 'the wildcard action mapping remains in the results for backward compatibility'
        mappingInfos.length == 2
        with(mappingInfos[0]) {
            controllerName == 'topic'
            actionName == 'show'
        }
        with(mappingInfos[1]) {
            controllerName == 'topic'
            actionName == '42'
        }
    }

    void 'applies wildcard filtering to match() results'() {
        given:
        def mappingsHolder = createUrlMappingsHolder({
            "/$action"(controller: 'topic')
            "/$id"(controller: 'topic', action: 'show') {
                constraints {
                    id(matches: /\d+/)
                }
            }
        }, TopicController)

        when: 'matching a non-action path through match()'
        def mappingInfo = mappingsHolder.match('/42')

        then: 'match() falls through to the id mapping'
        with(mappingInfo) {
            controllerName == 'topic'
            actionName == 'show'
        }
    }

    void 'prefers literal path mappings over wildcard controller matches'() {
        when: 'a literal path mapping and a wildcard controller mapping can both match the request'
        def mappingInfo = evaluateRequestFor('/community', {
            '/community'(controller: 'topic', action: 'home')
            "/$controller/$action?"()
        }, TopicController, CommunityController)

        then: 'the literal path mapping is selected'
        with(mappingInfo) {
            controllerName == 'topic'
            actionName == 'home'
        }
    }

    void 'prefers explicit method-specific mappings over wildcard optional action matches'() {
        when: 'an explicit POST mapping and a wildcard optional action mapping can both match the request'
        def mappingInfo = evaluateRequestFor(requestURI: '/invites', method: 'POST', {
            post '/invites'(controller: 'invite', action: 'create')
            "/invites/$action?"(controller: 'invite')
        }, InviteController)

        then: 'the explicit POST mapping is selected'
        with(mappingInfo) {
            controllerName == 'invite'
            actionName == 'create'
        }
    }

    void 'prefers explicit method-specific mappings inside parameterized group prefix'() {
        when: 'an explicit POST mapping and a wildcard optional action mapping are inside a group with a URL variable'
        def mappingInfo = evaluateRequestFor(requestURI: '/users/john/invites', method: 'POST', {
            group "/users/$username", {
                post '/invites'(controller: 'invite', action: 'create')
                "/invites/$action?"(controller: 'invite')
            }
        }, InviteController)

        then: 'the explicit POST mapping is selected despite the group-level URL variable'
        with(mappingInfo) {
            controllerName == 'invite'
            actionName == 'create'
        }
    }

    void 'GET to parameterized group routes to wildcard action index, not POST-only create'() {
        when: 'a GET request matches both a POST-only mapping and a wildcard optional action mapping inside a group'
        def mappingInfo = evaluateRequestFor(requestURI: '/users/john/invites', method: 'GET', {
            group "/users/$username", {
                post '/invites'(controller: 'invite', action: 'create')
                "/invites/$action?"(controller: 'invite')
            }
        }, InviteController)

        then: 'the wildcard optional action mapping is selected (index), not the POST-only create'
        with(mappingInfo) {
            controllerName == 'invite'
            actionName != 'create'
        }
    }

    void 'literal group path beats parameterized catch-all even with same controller'() {
        when: 'a group with a literal sub-path and a $username catch-all both match, mapping to the same controller'
        def mappingInfo = evaluateRequestFor('/users/gallery', {
            group '/users', {
                group '/gallery', controller: 'topic', {
                    "/$action?"()
                }
            }
            "/users/$username"(controller: 'topic', action: 'home')
        }, TopicController)

        then: 'the literal group path wins because it has a more specific URL pattern'
        with(mappingInfo) {
            controllerName == 'topic'
            actionName != 'home'
        }
    }

    void 'wildcard action match beats explicit memberId match for hyphenated action URL'() {
        given:
        def urlConverter = new HyphenatedUrlConverter()
        def mappingsHolder = createUrlMappingsHolder(urlConverter, {
            group "/users/$username", {
                group '/members', controller: 'member', {
                    "/$action?"()
                    "/$memberId"(action: 'remove')
                }
            }
        }, MemberController)

        when: 'a hyphenated action URL matches both $action? and $memberId(remove) in a parameterized group'
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = (webRequest.request as MockHttpServletRequest).tap {
            requestURI = '/users/john/members/opt-in-prompt'
            method = 'GET'
        }
        def mappingInfo = new UrlMappingsHandlerMapping(mappingsHolder).getHandler(request)?.handler as UrlMappingInfo

        then: 'the $action? mapping wins because it has a more specific URL pattern'
        mappingInfo != null
        with(mappingInfo) {
            controllerName == 'member'
            actionName == 'opt-in-prompt'
        }
    }

    void 'wildcard controller match beats parameterized catch-all for different controllers'() {
        when: 'a wildcard $controller match (feed) and a $username catch-all (topic.home) both match'
        def mappingInfo = evaluateRequestFor('/users/feed', {
            '/users'(controller: 'topic', action: 'home')
            group '/users', {
                "/$controller/$action?"()
            }
            "/users/$username"(controller: 'topic', action: 'home')
        }, TopicController, FeedController)

        then: 'the wildcard controller match wins because it resolved a real controller'
        with(mappingInfo) {
            controllerName == 'feed'
        }
    }

    private UrlMappingsHolder createUrlMappingsHolder(boolean validateWildcardMappings = true, Closure<?> mappings, Class<?>... controllerClasses) {
        createUrlMappingsHolder(null, validateWildcardMappings, mappings, controllerClasses)
    }

    private UrlMappingsHolder createUrlMappingsHolder(grails.web.UrlConverter urlConverter, boolean validateWildcardMappings = true, Closure<?> mappings, Class<?>... controllerClasses) {
        def grailsApplication = new DefaultGrailsApplication(controllerClasses).tap {
            initialise()
        }
        new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder(mappings), urlConverter).tap {
            it.validateWildcardMappings = validateWildcardMappings
        }
    }

    private UrlMappingInfo evaluateRequestFor(String requestURI, boolean validateWildCardMappings = true, Closure<?> mappings, Class<?>... controllerClasses) {
        evaluateRequestFor(requestURI: requestURI, method: 'GET', validateWildCardMappings, mappings, controllerClasses)
    }

    private UrlMappingInfo evaluateRequestFor(Map<String, String> requestInfo, boolean validateWildCardMappings = true, Closure<?> mappings, Class<?>... controllerClasses) {
        def mappingsHolder = createUrlMappingsHolder(validateWildCardMappings, mappings, controllerClasses)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = (webRequest.request as MockHttpServletRequest).tap {
            requestInfo.each { key, value ->
                it[key] = value
            }
        }
        new UrlMappingsHandlerMapping(mappingsHolder).getHandler(request)?.handler as UrlMappingInfo
    }
}

@Controller
class InviteController {

    static allowedMethods = [create: 'POST']

    def create() {}
    def manage() {}
}

@Controller
class CommunityController {

    def index() {}
}

@Controller
class MemberController {

    def index() {}
    def optInPrompt() {}
    def optIn() {}
    def remove() {}
}

@Controller
class FeedController {

    def index() {}
}

@Controller
class TopicController {

    def create() {}
    def gallery() {}
    def home() {}
    def save() {}
    def show() {}
}
