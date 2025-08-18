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

package org.apache.grails.views.gsp.layout

import com.opensymphony.module.sitemesh.RequestConstants
import org.apache.grails.web.layout.GSPGrailsLayoutPage
import org.apache.grails.web.layout.EmbeddedGrailsLayoutView
import org.grails.buffer.FastStringWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.context.request.RequestContextHolder

import static org.junit.jupiter.api.Assertions.assertEquals

class GSPGrailsLayoutPageTests extends AbstractGrailsTagTests {

    @Test
    void testCaptureContent() {
        def template = '<grailsLayout:captureContent tag=\"testtag\">this is the captured content</grailsLayout:captureContent>'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def result = applyTemplate(template, [:])
        println("RESULT IS $result")
        assertEquals('this is the captured content', gspGrailsLayoutPage.getContentBuffer('page.testtag').toString())
    }

    @Test
    void testCaptureContent2() {
        def template = '<grailsLayout:captureContent tag=\"testtag\">this is the <g:if test="${true}">captured</g:if> content</grailsLayout:captureContent>'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def result = applyTemplate(template, [:])
        assertEquals('this is the captured content', gspGrailsLayoutPage.getContentBuffer('page.testtag').toString())
    }

    @Test
    void testCaptureContent3() {
        def template = '<content tag=\"testtag\">this is the <g:if test="${true}">captured</g:if> content</content>'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def result = applyTemplate(template, [:])
        assertEquals('this is the captured content', gspGrailsLayoutPage.getContentBuffer('page.testtag').toString())
    }

    @Test
    void testCaptureTitleAndBody() {
        def template = '<html><head><title>This is the title</title></head><body onload="somejs();">body here</body></html>'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def result = applyTemplate(template, [:])
        assertEquals('This is the title', gspGrailsLayoutPage.getProperty('title'))
        FastStringWriter writer = new FastStringWriter()
        gspGrailsLayoutPage.writeBody(writer)
        assertEquals('body here', writer.toString())
        assertEquals('somejs();', gspGrailsLayoutPage.getProperty('body.onload'))
    }

    @Test
    void testMetaObjectValues() {
        // GRAILS-5603 test case
        def template = '<html><head><meta name="intval" content="${123}"/><meta name="dateval" content="${new Date(0)}"/><title>This is the title</title></head><body onload="somejs();">body here</body></html>'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def result = applyTemplate(template, [:])
        assertEquals('123', gspGrailsLayoutPage.getProperty('meta.intval'))
        assertEquals(new Date(0).toString(), gspGrailsLayoutPage.getProperty('meta.dateval'))
    }

    @Test
    void testLayoutTags() {
        def template = '<html><head><title>This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head><body onload="somejs();">body here</body></html>'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def result = applyTemplate(template, [:])
        assertEquals 'This is the title', gspGrailsLayoutPage.getProperty('title')

        def gspGrailsLayoutPage2 = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage2)
        webRequest.currentRequest.setAttribute(RequestConstants.PAGE, gspGrailsLayoutPage)
        def template2 = '<html><head><title><g:layoutTitle/></title><g:layoutHead/></head><body onload=\"${pageProperty(name:\'body.onload\')}\"><g:layoutBody/></body></html>'
        def result2 = applyTemplate(template2, [:])

        assertEquals '<html><head><title>This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head><body onload="somejs();">body here</body></html>', result2
    }

    @Test
    void testLayoutTagsBodyIsWholePage() {
        def template = 'body here'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def target1 = new FastStringWriter()
        gspGrailsLayoutPage.setPageBuffer(target1.buffer)
        def result = applyTemplate(template, [:], target1)

        def gspGrailsLayoutPage2 = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage2)
        webRequest.currentRequest.setAttribute(RequestConstants.PAGE, gspGrailsLayoutPage)
        def target2 = new FastStringWriter()
        gspGrailsLayoutPage2.setPageBuffer(target2.buffer)
        def template2 = '<body><g:layoutBody/></body>'
        def result2 = applyTemplate(template2, [:], target2)

        assertEquals('<body>body here</body>', result2)
    }

    @Test
    void testLayoutcontent() {
        def template = '<html><head><title>This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body onload="somejs();">body here</body><content tag="nav">Navigation content</content></html>'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def result = applyTemplate(template, [:])
        assertEquals('This is the title', gspGrailsLayoutPage.getProperty('title'))

        def gspGrailsLayoutPage2 = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage2)
        webRequest.currentRequest.setAttribute(RequestConstants.PAGE, gspGrailsLayoutPage)
        def template2 = '<html><head><title><g:layoutTitle/></title><g:layoutHead/></head><body onload=\"${pageProperty(name:\'body.onload\')}\"><g:layoutBody/> <g:pageProperty name="page.nav"/></body></html>'
        def result2 = applyTemplate(template2, [:])

        assertEquals('<html><head><title>This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body onload="somejs();">body here Navigation content</body></html>', result2)
    }

    @Test
    void testEmptyTitle() {
        // GRAILS-7510 , GRAILS-7736
        def template = '<html><head><title></title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body onload="somejs();">body here</body><content tag="nav">Navigation content</content></html>'
        def gspGrailsLayoutPage = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
        def result = applyTemplate(template, [:])
        assertEquals('', gspGrailsLayoutPage.getProperty('title'))

        def gspGrailsLayoutPage2 = new GSPGrailsLayoutPage()
        webRequest.currentRequest.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage2)
        webRequest.currentRequest.setAttribute(RequestConstants.PAGE, gspGrailsLayoutPage)
        def template2 = '<html><head><title><g:layoutTitle/></title><g:layoutHead/></head><body onload=\"${pageProperty(name:\'body.onload\')}\"><g:layoutBody/> <g:pageProperty name="page.nav"/></body></html>'
        def result2 = applyTemplate(template2, [:])

        assertEquals('<html><head><title></title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/></head><body onload="somejs();">body here Navigation content</body></html>', result2)
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }
}

