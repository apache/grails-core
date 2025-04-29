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
package grails.test.mixin

import grails.artefact.Artefact
import grails.testing.web.GrailsWebUnitTest
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class GroovyPageUnitTestMixinTests extends Specification implements GrailsWebUnitTest {

    void setupSpec() {
        mockTagLib(FooTagLib)
    }

    void testAssertOutputEquals() {
        expect:
        'tag contents good' == applyTemplate('<foo:bar one="${one}"/>', [one:'good'])
    }

    void testAssertOutputMatches() {
        expect:
        applyTemplate('<foo:bar one="${one}"/>', [one:'good']) =~ /.*good.*/
    }

    void testRenderTemplate() {
        given:
        views['/bar/_foo.gsp'] = 'Hello <g:createLink controller="foo" />'

        when:
        def result = render(template:"/bar/foo")

        then:
        result == 'Hello /foo'
    }

    void testRenderView() {
        given:
        views['/foo/bar.gsp'] = 'Hello <g:createLink controller="bar" />'

        when:
        def result = render(view:"/foo/bar")

        then:
        result == 'Hello /bar'
    }
    
    @Issue("GRAILS-10723")
    void testCreateLinkWithoutController() {
        given:
        views['/foo/bar.gsp'] = 'Hello <g:createLink action="bar" />'

        when:
        def result = render(view:"/foo/bar")

        then:
        result == 'Hello /test/bar'
    }

    @Issue("GRAILS-10723")
    void testCreateLinkWithSpecifiedController() {
        given:
        webRequest.controllerName = 'foo'
        views['/foo/bar.gsp'] = 'Hello <g:createLink action="bar" />'

        when:
        def result = render(view:"/foo/bar")

        then:
        result == 'Hello /foo/bar'
    }

    void testThatViewsAreClearedBetweenTests() {
        when:
        def result = render(view:"/foo/bar")

        then:
        result == null
    }

    void testCanCallRenderMultipleTimesInOneTest() {
        given:
        views['/_h1.gsp'] = '<h1>${text}</h1>'
        views['/_h2.gsp'] = '<h2>${text}</h2>'

        expect:
        applyTemplate('<g:render template="/h1" model="[text: text]"/>', [text: 'A main heading']) == '<h1>A main heading</h1>'
        applyTemplate('<g:render template="/h2" model="[text: text]"/>', [text: 'A sub-heading']) == '<h2>A sub-heading</h2>'
    }

    void testMockTagLibrary() {
        when:
        def result = applyTemplate('<foo:bar one="${one}"/>', [one:'good'])

        then:
        result != null
        result == 'tag contents good'
    }
}

@Artefact("TagLibrary")
class FooTagLib {
    static namespace = "foo"

    def bar = { attrs ->
        out << "tag contents ${attrs.one}"
    }
}
