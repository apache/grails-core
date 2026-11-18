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

import com.opensymphony.module.sitemesh.RequestConstants
import grails.testing.web.taglib.TagLibUnitTest
import org.grails.buffer.FastStringWriter
import org.grails.plugins.web.taglib.RenderGrailsLayoutTagLib
import org.grails.taglib.GrailsTagException
import spock.lang.Specification

class RenderGrailsLayoutTagLibSpec extends Specification implements TagLibUnitTest<RenderGrailsLayoutTagLib> {

    GSPGrailsLayoutPage page

    void setup() {
        page = new GSPGrailsLayoutPage()
        request.setAttribute(RequestConstants.PAGE, page)
    }

    void 'pageProperty renders a value, a default and an entire attribute'() {
        given:
        page.addProperty('body.class', 'content')

        expect:
        applyTemplate('<g:pageProperty name="body.class"/>') == 'content'
        applyTemplate('<g:pageProperty name="body.class" writeEntireProperty="true"/>') == ' class="content"'
        applyTemplate('<g:pageProperty name="missing" default="fallback"/>') == 'fallback'
        applyTemplate('<g:pageProperty name="missing"/>') == ''
    }

    void 'pageProperty requires a name attribute'() {
        when:
        applyTemplate('<g:pageProperty default="fallback"/>')

        then:
        def error = thrown(GrailsTagException)
        error.message.contains('missing required attribute [name]')
    }

    void 'captured component buffers can be rendered and tested as page properties'() {
        given:
        def writer = new FastStringWriter()
        writer.write('Sidebar')
        page.setContentBuffer('sidebar', writer.getBuffer())

        expect:
        applyTemplate('<g:pageProperty name="page.sidebar"/>') == 'Sidebar'
        applyTemplate('<g:ifPageProperty name="page.sidebar">Present</g:ifPageProperty>') == 'Present'
    }

    void 'ifPageProperty requires existing properties and matching values'() {
        given:
        page.addProperty('page.theme', 'dark')
        page.addProperty('page.mode', 'wide')

        expect:
        applyTemplate('<g:ifPageProperty name="page.theme" equals="dark">Yes</g:ifPageProperty>') == 'Yes'
        applyTemplate('<g:ifPageProperty name="page.theme" equals="light">Hidden</g:ifPageProperty>') == ''
        applyTemplate('<g:ifPageProperty name="missing">Hidden</g:ifPageProperty>') == ''
        applyTemplate('<g:ifPageProperty>Hidden</g:ifPageProperty>') == ''
        applyTemplate('<g:ifPageProperty name="${[\'page.theme\', \'page.mode\']}" ' +
                'equals="${[\'dark\', \'wide\']}">Both</g:ifPageProperty>') == 'Both'
    }

    void 'layout tags render captured title head and body with a default title when absent'() {
        given:
        def head = new FastStringWriter()
        head.write('<meta name="layout" content="main">')
        def body = new FastStringWriter()
        body.write('Body')
        page.setHeadBuffer(head.getBuffer())
        page.setBodyBuffer(body.getBuffer())

        expect:
        applyTemplate('<g:layoutTitle default="Default"/>|<g:layoutHead/>|<g:layoutBody/>') ==
                'Default|<meta name="layout" content="main">|Body'

        when:
        page.addProperty('title', 'Title')

        then:
        applyTemplate('<g:layoutTitle default="Default"/>') == 'Title'
    }
}
