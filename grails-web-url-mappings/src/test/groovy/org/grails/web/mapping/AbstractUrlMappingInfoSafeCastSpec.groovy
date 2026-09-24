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
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo
import org.grails.support.MockApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

/**
 * Verifies that {@link AbstractUrlMappingInfo} and {@link DefaultUrlMappingInfo} do not throw a
 * {@link ClassCastException} when {@link RequestContextHolder} holds a plain
 * {@link ServletRequestAttributes} instead of a {@link org.grails.web.servlet.mvc.GrailsWebRequest}.
 *
 * <p>This scenario arises when {@link org.grails.web.errors.GrailsExceptionResolver} resolves an
 * exception: Spring's {@code DispatcherServlet} may have bound a {@code ServletRequestAttributes}
 * before the Grails filter had a chance to upgrade it to a {@code GrailsWebRequest}. The unconditional
 * cast that previously existed in {@code evaluateNameForValue} and {@code getActionName} would then
 * throw a {@code ClassCastException}, masking the original application exception.
 *
 * @see <a href="https://github.com/apache/grails-core/issues/16129">Issue #16129</a>
 */
class AbstractUrlMappingInfoSafeCastSpec extends Specification {

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    private static UrlMapping closureActionMapping() {
        MockApplicationContext ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            '/book'(controller: 'book', action: { request.method == 'GET' ? 'show' : 'save' })
        }.first()
    }

    private static UrlMapping staticMapping() {
        MockApplicationContext ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            '/book'(controller: 'book', action: 'show')
        }.first()
    }

    void 'evaluateNameForValue does not throw ClassCastException when RequestContextHolder holds a plain ServletRequestAttributes'() {
        given: 'a plain ServletRequestAttributes (not a GrailsWebRequest) is bound'
        def request = new MockHttpServletRequest('GET', '/book')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()))

        and: 'a mapping whose action is a closure (requires a GrailsWebRequest to evaluate)'
        UrlMapping mapping = closureActionMapping()
        UrlMappingInfo info = mapping.match('/book')

        when: 'action name is resolved while only a plain ServletRequestAttributes is bound'
        String actionName = info.actionName

        then: 'no ClassCastException is thrown; the action gracefully returns null'
        noExceptionThrown()
        actionName == null
    }

    void 'getActionName does not throw ClassCastException when RequestContextHolder holds a plain ServletRequestAttributes'() {
        given: 'a plain ServletRequestAttributes (not a GrailsWebRequest) is bound'
        def request = new MockHttpServletRequest('GET', '/book')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()))

        and: 'a mapping whose action is a closure'
        UrlMapping mapping = closureActionMapping()
        UrlMappingInfo info = mapping.match('/book')

        when:
        String actionName = info.actionName

        then:
        noExceptionThrown()
        actionName == null
    }

    void 'evaluateNameForValue does not throw ClassCastException when RequestContextHolder is empty'() {
        given: 'no request attributes are bound at all'
        RequestContextHolder.resetRequestAttributes()

        and: 'a mapping whose action is a closure'
        UrlMapping mapping = closureActionMapping()
        UrlMappingInfo info = mapping.match('/book')

        when:
        String actionName = info.actionName

        then:
        noExceptionThrown()
        actionName == null
    }

    void 'static string action names are resolved correctly regardless of RequestContextHolder state'() {
        given: 'a plain ServletRequestAttributes is bound'
        def request = new MockHttpServletRequest('GET', '/book')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()))

        and: 'a mapping with a static string action name'
        UrlMapping mapping = staticMapping()
        UrlMappingInfo info = mapping.match('/book')

        when:
        String actionName = info.actionName

        then: 'static names are always resolved correctly'
        noExceptionThrown()
        actionName == 'show'
    }

    void 'static string controller names are resolved correctly regardless of RequestContextHolder state'() {
        given: 'a plain ServletRequestAttributes is bound'
        def request = new MockHttpServletRequest('GET', '/book')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()))

        and: 'a mapping with a static string controller name'
        UrlMapping mapping = staticMapping()
        UrlMappingInfo info = mapping.match('/book')

        when:
        String controllerName = info.controllerName

        then:
        noExceptionThrown()
        controllerName == 'book'
    }
}
