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
import org.grails.support.MockApplicationContext
import spock.lang.Issue
import spock.lang.Specification

class UrlMappingsWithHttpMethodNotInNamedParametersSpec extends Specification {

    def mappings1 = {
        "/foo"(controller: 'example', action: 'fooGet') {
            method = 'GET'
            arbitrary = 'foo - get'
        }
        "/foo"(controller: 'example', action: 'fooPost') {
            method = 'POST'
            arbitrary = 'foo - post'
        }
    }

    def mappings2 = {
        get "/bar"(controller: 'example', action: 'barGet') {
            arbitrary = 'bar - get'
        }
        post "/bar"(controller: 'example', action: 'barPost') {
            arbitrary = 'bar - post'
        }
    }

    def mappings3 = {
        "/baz" {
            controller = 'example'
            action = 'getBaz'
            method = 'GET'
            arbitrary = 'baz - get'
        }
        "/baz" {
            controller = 'example'
            action = 'postBaz'
            method = 'POST'
            arbitrary = 'baz - post'
        }
    }

    @Issue('10855')
    void 'The http method can be defined as an arbitrary variable with controller and action defined as named parameters'() {
        given: 'a URL mapping evaluator'
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)

        when: 'the mappings are evaluated'
        def mappings = evaluator.evaluateMappings(mappings1)

        then:
        mappings.size() == 2
        mappings[0].httpMethod == 'GET'
        mappings[0].parameterValues.arbitrary == 'foo - get'
        mappings[1].httpMethod == 'POST'
        mappings[1].parameterValues.arbitrary == 'foo - post'
    }

    @Issue('10855')
    void 'It is possible to define arbitrary variables with http method define before the url'() {
        given: 'a URL mapping evaluator'
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)

        when: 'the mappings are evaluated'
        def mappings = evaluator.evaluateMappings(mappings2)

        then:
        mappings.size() == 2
        mappings[0].httpMethod == 'GET'
        mappings[0].parameterValues.arbitrary == 'bar - get'
        mappings[1].httpMethod == 'POST'
        mappings[1].parameterValues.arbitrary == 'bar - post'
    }

    @Issue('10855')
    void 'It is possible to define arbitrary variables and http method with mapping information defined as a closure'() {
        given: 'a URL mapping evaluator'
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)

        when: 'the mappings are evaluated'
        def mappings = evaluator.evaluateMappings(mappings3)

        then:
        mappings.size() == 2
        mappings[0].httpMethod == 'GET'
        mappings[0].parameterValues.arbitrary == 'baz - get'
        mappings[1].httpMethod == 'POST'
        mappings[1].parameterValues.arbitrary == 'baz - post'
    }
}
