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
import grails.util.GrailsWebMockUtil
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo
import org.grails.support.MockApplicationContext
import org.grails.web.util.WebUtils
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

/**
 * A mapping such as {@code "/$controller/$action?/$id?"} names its controller and action with a token
 * captured from the URI. The mapping is shared by every request it matches, so it holds an evaluator
 * for the token rather than a value, and the {@link UrlMappingInfo} produced by a match resolves it.
 */
class RuntimeConstraintEvaluatorSpec extends Specification {

    def setup() {
        WebUtils.clearGrailsWebRequest()
    }

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    private static UrlMapping defaultMapping() {
        MockApplicationContext ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            "/$controller/$action?/$id?"()
        }.first()
    }

    private static void bindRequestCarrying(Map<String, String> params) {
        MockHttpServletRequest request = new MockHttpServletRequest()
        params.each { name, value -> request.addParameter(name, value) }
        GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), request, new MockHttpServletResponse())
    }

    void 'a captured name is resolved from the URI that matched, not from the current request'() {
        given: 'a request carrying parameters that name a different controller and action'
        bindRequestCarrying(controller: 'fromRequest', action: 'fromRequest')

        when: 'a URI is matched'
        UrlMappingInfo info = defaultMapping().match('/book/show/1')

        then: 'the names come from the URI'
        info.controllerName == 'book'
        info.actionName == 'show'
        info.id == '1'

        and: 'so resolving them does not depend on the request having been configured'
        !info.nameResolutionRequestDependent
    }

    void 'the evaluator the mapping holds for a token resolves it against the current request'() {
        given: 'a mapping that has matched, so it holds an evaluator for its controller token'
        UrlMapping mapping = defaultMapping()
        mapping.match('/book/show/1')
        Closure evaluator = mapping.controllerName as Closure

        and: 'a bound request carrying a controller parameter'
        bindRequestCarrying(controller: 'fromRequest')

        expect: 'calling it reads that parameter - all a shared instance can do on its own'
        evaluator.call() == 'fromRequest'
        evaluator.call('ignored') == 'fromRequest'
        evaluator.clone().call() == 'fromRequest'
    }

    void 'the evaluator answers for whichever request is bound when it is called'() {
        given:
        UrlMapping mapping = defaultMapping()
        mapping.match('/book/show/1')
        Closure evaluator = mapping.controllerName as Closure

        when:
        bindRequestCarrying(controller: 'first')

        then:
        evaluator.call() == 'first'

        when:
        WebUtils.clearGrailsWebRequest()
        bindRequestCarrying(controller: 'second')

        then:
        evaluator.call() == 'second'
    }
}
