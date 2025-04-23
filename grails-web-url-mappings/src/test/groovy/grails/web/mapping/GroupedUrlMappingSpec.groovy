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
package grails.web.mapping

import grails.util.GrailsWebMockUtil
import grails.web.http.HttpHeaders
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Issue

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * @author graemerocher
 */
class GroupedUrlMappingSpec extends AbstractUrlMappingsSpec {

    @Issue('#10308')
    void 'Test mapping with group and nested collection'() {
        given:
        def linkGenerator = getLinkGenerator {
            '/foos'(resources: 'foo') {
                collection {
                    '/baz'(controller: 'foo', action: 'baz')
                }
            }

            group('/g') {
                '/bars'(resources: 'bar') {
                    collection {
                        '/baz'(controller: 'bar', action: 'baz')
                    }
                }
            }
        }

        expect:
        linkGenerator.link(controller: 'foo', action: 'baz', params: [fooId: 1]) == 'http://localhost/foos/baz?fooId=1'
        linkGenerator.link(controller: 'bar', action: 'baz', params: [barId: 1]) == 'http://localhost/g/bars/baz?barId=1'

        /*
        This was the original assertion pre Grails 7, but according to the issue discussion (#10308) it is wrong.
        The assertion also started failing when upgrading to Groovy 4 where the `this` reference is not the same in
        DefaultUrlMappingEvaluator.UrlMappingBuilder#collection for Groovy 3 and Groovy 4.
        */
        // linkGenerator.link(controller: 'bar', action: 'baz', params: [barId: 1]) == 'http://localhost/g/bars/1/baz'
    }

    @Issue('#9417')
    void "Test that redirects to grouped resource mappings work when the method is specified"() {
        given:
        def linkGenerator = getLinkGenerator {
            group "/admin", {
                "/domains"(resources: 'domain')
            }
        }
        def responseRedirector = new ResponseRedirector(linkGenerator)
        HttpServletRequest request = Mock(HttpServletRequest) { lookup() >> GrailsWebMockUtil.bindMockWebRequest() }
        HttpServletResponse response = Mock(HttpServletResponse)

        when: "The response is redirected"
        responseRedirector.redirect(request, response, [controller: 'domain', action: 'index', method: "GET"])

        then:
        1 * response.setStatus(302)
        1 * response.setHeader(HttpHeaders.LOCATION, "http://localhost/admin/domains")

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }

    @Issue('#9417')
    void "Test that redirects to grouped resource mappings work when the resource is specified"() {
        given:
        def linkGenerator = getLinkGenerator {
            group "/admin", {
                "/domains"(resources: 'domain')
            }
        }
        def responseRedirector = new ResponseRedirector(linkGenerator)
        HttpServletRequest request = Mock(HttpServletRequest) { lookup() >> GrailsWebMockUtil.bindMockWebRequest() }
        HttpServletResponse response = Mock(HttpServletResponse)

        when: "The response is redirected"
        responseRedirector.redirect(request, response, [resource: 'domain', action: 'index'])

        then:
        1 * response.setStatus(302)
        1 * response.setHeader(HttpHeaders.LOCATION, "http://localhost/admin/domains")

        cleanup:
        RequestContextHolder.setRequestAttributes(null)
    }

    @Issue('#9138')
    void "Test that group parameters are included in generated links"() {
        given: "A link generator with a dynamic URL mapping"
        def linkGenerator = getLinkGenerator {
            group "/events/$alias", {
                "/"(controller: 'test', action: 'index')
                "/orders/$id"(controller: 'test', action: 'show')
            }
        }

        expect:
        linkGenerator.link(controller: "test", action: 'index', params: [alias: 'foo']) == 'http://localhost/events/foo'
        linkGenerator.link(controller: "test", action: 'show', id: 1, params: [alias: 'foo']) == 'http://localhost/events/foo/orders/1'
    }

    @Issue('#9394')
    void "Test that nested group parameters are supported"() {
        given:"A link generator with a dynamic URL mapping"
        def linkGenerator = getLinkGenerator {
            group "/events/$alias", {
                group "/orders", {
                    "/$id" (controller: 'test', action: 'show')
                }

                "/"(controller: 'test', action: 'index')
            }
        }

        expect:
        linkGenerator.link(controller:"test", action: 'show', id:1, params:[alias:'foo']) == 'http://localhost/events/foo/orders/1'
        linkGenerator.link(controller:"test", action: 'index', params:[alias:'foo']) == 'http://localhost/events/foo'
    }

    @Issue('#9426')
    void "Test that constraints embedded within groups are properly respected"() {
        given: "A group with a child URL that contains a constraint"
        def urlMappingsHolder = getUrlMappingsHolder {
            group "/group", {
                "/$id/$page?" {
                    controller = "test"
                    action = "index"
                    constraints {
                        id(matches: /\d+/)
                        page(matches: /\d+/)
                    }
                }
            }
        }

        when: 'Attempting to match some urls to this group'
        def goodMatch_full = urlMappingsHolder.matchAll("/group/1/2")
        def goodMatch_optional = urlMappingsHolder.matchAll("/group/1")
        def badMatch = urlMappingsHolder.matchAll("/group/1/char")

        then: 'Should succeed'
        goodMatch_full
        goodMatch_optional
        !badMatch
    }

    void "Test that root mappings in group are properly respected"() {
        given: "A group with multiple root children"
        def urlMappingsHolder = getUrlMappingsHolder {
            group "/group1", {
                "/"(controller: "test1", action: "index")
                "/secondLevel1"(controller: "test1SecondLevel", action: "index")
                "/$id"(controller: "test1", action: "id") {
                    constraints {
                        id(matches: /\d+/)
                    }
                }
            }
            group "/group2", {
                "/"(controller: "test2", action: "index")
                "/secondLevel2"(controller: "test2SecondLevel", action: "index")
                "/$id"(controller: "test2", action: "id") {
                    constraints {
                        id(matches: /\d+/)
                    }
                }
            }
            "/notagroup"(controller: "test3", action: "index")
        }

        when: 'Attempting to match URLs against nested groups'
        def root_1 = urlMappingsHolder.match("/group1")
        def root_2 = urlMappingsHolder.match("/group2")

        def second_level_1 = urlMappingsHolder.match("/group1/secondLevel1")
        def second_level_2 = urlMappingsHolder.match("/group2/secondLevel2")

        def optional_param_1 = urlMappingsHolder.match("/group1/1234")
        def optional_param_2 = urlMappingsHolder.match("/group2/1234")

        def not_a_group = urlMappingsHolder.match("/notagroup")

        then: 'URLs should match appropriately'
        "test1" == root_1.controllerName
        "index" == root_1.actionName

        "test2" == root_2.controllerName
        "index" == root_2.actionName

        "test1SecondLevel" == second_level_1.controllerName
        "test2SecondLevel" == second_level_2.controllerName

        "test1" == optional_param_1.controllerName
        "id" == optional_param_1.actionName

        "test2" == optional_param_2.controllerName
        "id" == optional_param_2.actionName

        "test3" == not_a_group.controllerName
    }

    @Issue('#10842')
    void 'Test constraints in multiple urls in same group are applied'() {
        given: 'a group with two mappings with constraints'
        def urlMappingsHolder = getUrlMappingsHolder {
            group "/v2", {
                "/session/$sessionId(.$format)?"(controller: 'dummy', action: 'bySession') {
                    constraints {
                        sessionId(matches: /\p{XDigit}{16}/)
                    }
                }

                "/$sessionId(.$format)?"(controller: 'dummy', action: 'bySessionOld') {
                    constraints {
                        sessionId(matches: /\p{XDigit}{16}/)
                    }
                }
            }
        }

        expect: 'both url match and the constraint is applied'
        urlMappingsHolder.matchAll('/v2/session/123456789ABCDEF0')
        urlMappingsHolder.matchAll('/v2/123456789ABCDEF0')

        and: 'with a wrong value that does not match the constraint the urls do not match'
        !urlMappingsHolder.matchAll('/v2/session/A')
        !urlMappingsHolder.matchAll('/v2/A')
    }
}
