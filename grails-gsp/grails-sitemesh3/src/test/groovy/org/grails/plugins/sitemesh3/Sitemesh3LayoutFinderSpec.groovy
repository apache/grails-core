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
package org.grails.plugins.sitemesh3

import org.sitemesh.content.Content
import org.sitemesh.content.ContentProperty
import org.sitemesh.content.memory.InMemoryContent
import org.sitemesh.webapp.WebAppContext

import org.grails.gsp.io.GroovyPageScriptSource
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

class Sitemesh3LayoutFinderSpec extends Specification {

    GrailsConventionGroovyPageLocator locator
    Sitemesh3LayoutFinder finder
    WebAppContext context
    MockHttpServletRequest request
    MockHttpServletResponse response
    MockServletContext servletContext

    def setup() {
        locator = Mock(GrailsConventionGroovyPageLocator)
        finder = new Sitemesh3LayoutFinder(locator)
        finder.cacheEnabled = false
        request = new MockHttpServletRequest()
        response = new MockHttpServletResponse()
        servletContext = new MockServletContext()
        context = Stub(WebAppContext) {
            getRequest() >> request
        }
    }

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'request attribute wins over all other sources'() {
        given:
        request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, 'chosen')
        Content content = contentWithMetaLayout('ignored')

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/chosen') >> Mock(GroovyPageScriptSource)
        paths == ['/layouts/chosen'] as String[]
    }

    void 'meta layout is used when request attribute is blank'() {
        given:
        Content content = contentWithMetaLayout('meta-layout')

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/meta-layout') >> Mock(GroovyPageScriptSource)
        paths == ['/layouts/meta-layout'] as String[]
    }

    void 'falls back to configured default when no controller is bound'() {
        given:
        finder.defaultDecoratorName = 'application'
        Content content = emptyContent()

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/application') >> Mock(GroovyPageScriptSource)
        paths == ['/layouts/application'] as String[]
    }

    void 'controller static layout property is used next'() {
        given:
        bindController(new FixedLayoutController(), 'sample', '/sample/index')
        Content content = emptyContent()

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/controller-static') >> Mock(GroovyPageScriptSource)
        paths == ['/layouts/controller-static'] as String[]
    }

    void 'action-specific layout is tried when no static layout'() {
        given:
        bindController(new ConventionController(), 'sample', '/sample/edit')
        Content content = emptyContent()

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/sample/edit') >> Mock(GroovyPageScriptSource)
        0 * locator.findViewByPath(_)
        paths == ['/layouts/sample/edit'] as String[]
    }

    void 'controller-specific layout is tried when action not found'() {
        given:
        bindController(new ConventionController(), 'sample', '/sample/edit')
        Content content = emptyContent()

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/sample/edit') >> null
        1 * locator.findViewByPath('/layouts/sample') >> Mock(GroovyPageScriptSource)
        paths == ['/layouts/sample'] as String[]
    }

    void 'default layout is used when nothing else matches'() {
        given:
        finder.defaultDecoratorName = 'application'
        bindController(new ConventionController(), 'sample', '/sample/edit')
        Content content = emptyContent()

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/sample/edit') >> null
        1 * locator.findViewByPath('/layouts/sample') >> null
        1 * locator.findViewByPath('/layouts/application') >> Mock(GroovyPageScriptSource)
        paths == ['/layouts/application'] as String[]
    }

    void 'returns empty when nothing matches and no default'() {
        given:
        bindController(new ConventionController(), 'sample', '/sample/edit')
        Content content = emptyContent()

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/sample/edit') >> null
        1 * locator.findViewByPath('/layouts/sample') >> null
        paths.length == 0
    }

    void 'NONE layout suppresses decoration'() {
        given:
        request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, WebUtils.NONE_LAYOUT)
        Content content = contentWithMetaLayout('ignored')

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        0 * locator.findViewByPath(_)
        paths.length == 0
    }

    void 'meta layout still applies when the response carries an error status'() {
        given: 'an error-dispatched render (e.g. a custom 404 page) bound to the request'
        GrailsWebRequest webRequest = new GrailsWebRequest(request, response, servletContext)
        RequestContextHolder.setRequestAttributes(webRequest)
        response.setStatus(404)
        Content content = contentWithMetaLayout('main')

        when:
        String[] paths = finder.selectDecoratorPaths(content, context)

        then:
        1 * locator.findViewByPath('/layouts/main') >> Mock(GroovyPageScriptSource)
        paths == ['/layouts/main'] as String[]
    }

    private Content contentWithMetaLayout(String layout) {
        Content content = new InMemoryContent()
        ContentProperty root = content.getExtractedProperties()
        root.getChild('meta').getChild('layout').setValue(layout)
        content
    }

    private Content emptyContent() {
        new InMemoryContent()
    }

    private void bindController(controller, String controllerName, String actionUri) {
        GrailsWebRequest webRequest = new GrailsWebRequest(request, response, servletContext)
        RequestContextHolder.setRequestAttributes(webRequest)
        webRequest.setControllerName(controllerName)
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller)
        controller.actionUri = actionUri
    }

    static class ConventionController {
        String actionUri
    }

    static class FixedLayoutController {
        static String layout = 'controller-static'
        String actionUri
    }
}
