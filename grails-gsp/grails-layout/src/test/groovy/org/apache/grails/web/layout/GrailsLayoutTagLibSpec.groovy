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

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.gsp.compiler.GrailsLayoutPreprocessor
import org.grails.plugins.web.taglib.GrailsLayoutTagLib
import spock.lang.Specification

class GrailsLayoutTagLibSpec extends Specification implements TagLibUnitTest<GrailsLayoutTagLib> {

    GSPGrailsLayoutPage page

    void setup() {
        page = new GSPGrailsLayoutPage()
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, page)
    }

    void 'capture tags render HTML and store head body and title on the page'() {
        when:
        def output = applyTemplate('<grailsLayout:captureHead><grailsLayout:captureTitle>Title</grailsLayout:captureTitle>' +
                '<meta name="layout" content="main"></grailsLayout:captureHead>' +
                '<grailsLayout:captureBody class="content">Body</grailsLayout:captureBody>')

        then:
        output == '<head><title>Title</title><meta name="layout" content="main"></head><body class="content">Body</body>'
        page.title == 'Title'
        page.isTitleCaptured()
        page.head.contains('<meta name="layout" content="main">')
        page.body == 'Body'
        page.getProperty('body.class') == 'content'
        page.isUsed()
    }

    void 'wrapped title is captured in its own buffer so the layout head can drop it without reparsing'() {
        when:
        def output = applyTemplate('<grailsLayout:captureHead><grailsLayout:wrapTitleTag>' +
                '<grailsLayout:captureTitle>Title</grailsLayout:captureTitle></grailsLayout:wrapTitleTag>' +
                '<meta name="layout" content="main"></grailsLayout:captureHead>')
        def layoutHead = new StringWriter()
        page.writeHead(layoutHead)

        then:
        output == '<head><title>Title</title><meta name="layout" content="main"></head>'
        page.title == 'Title'
        page.isTitleCaptured()
        page.titleBuffer != null
        layoutHead.toString() == '<meta name="layout" content="main">'
    }

    void 'empty capture tags honour the preprocessor XML closing marker'() {
        when:
        def output = applyTemplate('<grailsLayout:captureMeta name="viewport" content="width" ' +
                GrailsLayoutPreprocessor.XML_CLOSING_FOR_EMPTY_TAG_ATTRIBUTE_NAME + '="/"/>')

        then:
        output == '<meta name="viewport" content="width"/>'
        page.getProperty('meta.viewport') == 'width'
    }

    void 'meta capture records both original and lowercase property names'() {
        when:
        def output = applyTemplate('<grailsLayout:captureMeta name="Description" content="Details"/>' +
                '<grailsLayout:captureMeta http-equiv="Content-Type" content="text/html"/>')

        then:
        output == '<meta name="Description" content="Details"><meta http-equiv="Content-Type" content="text/html">'
        page.getProperty('meta.Description') == 'Details'
        page.getProperty('meta.description') == 'Details'
        page.getProperty('meta.http-equiv.Content-Type') == 'text/html'
        page.getProperty('meta.http-equiv.content-type') == 'text/html'
    }

    void 'parameter and content capture populate the page without writing the captured component'() {
        when:
        def output = applyTemplate('<grailsLayout:parameter name="theme" value="dark"/>' +
                '<grailsLayout:captureContent tag="sidebar">Sidebar</grailsLayout:captureContent>')

        then:
        output == ''
        page.getProperty('page.theme') == 'dark'
        page.getContentBuffer('page.sidebar').toString() == 'Sidebar'
    }

    void 'capture tags still render without a layout page'() {
        given:
        request.removeAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE)

        expect:
        applyTemplate('<grailsLayout:captureTitle/><grailsLayout:captureBody>Body</grailsLayout:captureBody>') ==
                '<title></title><body>Body</body>'
        applyTemplate('<grailsLayout:wrapTitleTag><grailsLayout:captureTitle>Title</grailsLayout:captureTitle>' +
                '</grailsLayout:wrapTitleTag>') == '<title>Title</title>'
    }
}
