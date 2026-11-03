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
package org.apache.grails.web.layout

import spock.lang.Specification

import org.springframework.core.Ordered
import org.springframework.mock.web.MockServletContext
import org.springframework.web.servlet.SmartView
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver

/**
 * Tests {@link EmbeddedGrailsLayoutViewResolver} view resolution.
 */
class EmbeddedGrailsLayoutViewResolverSpec extends Specification {

    GroovyPageLayoutFinder finder = new GroovyPageLayoutFinder()

    void 'the resolver is ordered before the lowest precedence and the order is configurable'() {
        given:
        def resolver = new EmbeddedGrailsLayoutViewResolver()

        expect:
        resolver.order == Ordered.LOWEST_PRECEDENCE - 30

        when:
        resolver.order = 5

        then:
        resolver.order == 5
    }

    void 'collaborators can be supplied by the constructor or by setters'() {
        given:
        def inner = Stub(ViewResolver)
        def replacement = Stub(ViewResolver)
        def constructed = new EmbeddedGrailsLayoutViewResolver(inner, finder)
        def configured = new EmbeddedGrailsLayoutViewResolver()

        when:
        configured.setInnerViewResolver(inner)
        configured.setGroovyPageLayoutFinder(finder)
        configured.setServletContext(new MockServletContext())

        then:
        constructed.getInnerViewResolver().is(inner)
        configured.getInnerViewResolver().is(inner)
        configured.servletContext != null

        when:
        configured.setInnerViewResolver(replacement)

        then:
        configured.getInnerViewResolver().is(replacement)
    }

    void 'resolving a view that the inner resolver does not know returns null'() {
        given:
        def resolver = new EmbeddedGrailsLayoutViewResolver(Stub(ViewResolver) {
            resolveViewName('missing', Locale.ENGLISH) >> null
        }, finder)

        expect:
        resolver.resolveViewName('missing', Locale.ENGLISH) == null
    }

    void 'redirect views are returned unwrapped'() {
        given:
        def redirect = Stub(SmartView) {
            isRedirectView() >> true
        }
        def resolver = new EmbeddedGrailsLayoutViewResolver(Stub(ViewResolver) {
            resolveViewName('redirect:/x', Locale.ENGLISH) >> redirect
        }, finder)

        expect:
        resolver.resolveViewName('redirect:/x', Locale.ENGLISH).is(redirect)
    }

    void 'other views are wrapped in a layout view that uses the layout finder'() {
        given:
        def inner = Stub(View)
        def smartInner = Stub(SmartView) {
            isRedirectView() >> false
        }
        def resolver = new EmbeddedGrailsLayoutViewResolver(Stub(ViewResolver) {
            resolveViewName('plain', Locale.ENGLISH) >> inner
            resolveViewName('smart', Locale.ENGLISH) >> smartInner
        }, finder)

        when:
        def plain = resolver.resolveViewName('plain', Locale.ENGLISH)
        def smart = resolver.resolveViewName('smart', Locale.ENGLISH)

        then:
        plain instanceof EmbeddedGrailsLayoutView
        ((EmbeddedGrailsLayoutView) plain).getInnerView().is(inner)
        ((EmbeddedGrailsLayoutView) plain).groovyPageLayoutFinder.is(finder)
        smart instanceof EmbeddedGrailsLayoutView
    }
}
