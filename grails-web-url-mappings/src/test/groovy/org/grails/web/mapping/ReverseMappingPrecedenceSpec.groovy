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

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.LinkGenerator
import org.grails.support.MockApplicationContext
import org.grails.web.util.WebUtils
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

/**
 * Pins which URL mapping a link uses when more than one mapping could produce it: the most specific
 * mapping for the target wins, the {@code $controller} wildcard is only a fallback, a redirect mapping
 * is never used, and the result does not depend on the order the mappings are declared in.
 */
class ReverseMappingPrecedenceSpec extends Specification {

    def setup() {
        WebUtils.clearGrailsWebRequest()
    }

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "a mapping naming the controller and action wins over the wildcard and over a redirect"() {
        given:
        def generator = linkGeneratorFor {
            "/$controller/$action?/$id?"()
            "/login"(controller: 'auth', action: 'login')
            "/signin"(redirect: [controller: 'auth', action: 'login'])
        }

        expect:
        generator.link(controller: 'auth', action: 'login') == '/login'
    }

    void "a mapping naming the action wins over one capturing it"() {
        given:
        def generator = linkGeneratorFor {
            "/$controller/$action?/$id?"()
            "/books/$action/$id"(controller: 'book')
            "/books/show/$id"(controller: 'book', action: 'show')
        }

        expect:
        generator.link(controller: 'book', action: 'show', id: 1) == '/books/show/1'
    }

    void "the mapping that consumes the supplied parameters wins, whichever is declared first"() {
        given:
        def generator = linkGeneratorFor(mappings)

        expect: 'every supplied parameter lands in the path rather than the query string'
        generator.link(controller: 'book', action: 'show', id: 1, params: [slug: 'x']) == '/books/1/x'

        and: 'a mapping needing a parameter that was not supplied is not used'
        generator.link(controller: 'book', action: 'show', id: 1) == '/books/1'

        where:
        mappings << [
                {
                    "/$controller/$action?/$id?"()
                    "/books/$id"(controller: 'book', action: 'show')
                    "/books/$id/$slug"(controller: 'book', action: 'show')
                },
                {
                    "/$controller/$action?/$id?"()
                    "/books/$id/$slug"(controller: 'book', action: 'show')
                    "/books/$id"(controller: 'book', action: 'show')
                }
        ]
    }

    void "a mapping for the link's method wins over one for any method, whichever is declared first"() {
        given:
        def generator = linkGeneratorFor(mappings)

        expect: 'a GET link uses the GET mapping'
        generator.link(controller: 'book', action: 'show', id: 1, method: 'GET') == '/books/1'

        and: 'a link naming no method, outside a request, uses the mapping for any method'
        generator.link(controller: 'book', action: 'show', id: 1) == '/books/view/1'

        where:
        mappings << [
                {
                    "/$controller/$action?/$id?"()
                    get "/books/$id"(controller: 'book', action: 'show')
                    "/books/view/$id"(controller: 'book', action: 'show')
                },
                {
                    "/$controller/$action?/$id?"()
                    "/books/view/$id"(controller: 'book', action: 'show')
                    get "/books/$id"(controller: 'book', action: 'show')
                }
        ]
    }

    void "the choice between two equally specific mappings does not depend on declaration order"() {
        given:
        def generator = linkGeneratorFor(mappings)

        expect: 'the same mapping is chosen either way; a named mapping selects one explicitly'
        generator.link(controller: 'book', action: 'show', id: 1) == '/books/1'

        where:
        mappings << [
                {
                    "/$controller/$action?/$id?"()
                    "/books/$id"(controller: 'book', action: 'show')
                    "/library/book/$id"(controller: 'book', action: 'show')
                },
                {
                    "/$controller/$action?/$id?"()
                    "/library/book/$id"(controller: 'book', action: 'show')
                    "/books/$id"(controller: 'book', action: 'show')
                }
        ]
    }

    private static LinkGenerator linkGeneratorFor(Closure urlMappings) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def generator = new DefaultLinkGenerator('http://localhost', '')
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings(urlMappings))
        generator
    }
}
