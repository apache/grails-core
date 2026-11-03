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

import com.opensymphony.module.sitemesh.HTMLPage
import com.opensymphony.sitemesh.Content
import org.grails.buffer.StreamCharBuffer
import spock.lang.Specification

class GSPGrailsLayoutPageSpec extends Specification {

    void 'empty page writes no content and is unused'() {
        given:
        def page = new GSPGrailsLayoutPage()
        def writer = new StringWriter()

        when:
        page.writeHead(writer)
        page.writeBody(writer)
        page.writePage(writer)

        then:
        writer.toString() == ''
        page.getHead() == null
        page.getBody() == null
        page.getPage() == null
        page.getTitleBuffer() == null
        page.getContentBuffer('page.sidebar') == null
        !page.isUsed()
        !page.isTitleCaptured()
    }

    void 'captured buffers expose head body and original page separately'() {
        given:
        def page = new GSPGrailsLayoutPage(renderingLayout)
        def original = '<html><head>head</head><body>body</body></html>'
        page.setPageBuffer(buffer(original))
        page.setHeadBuffer(buffer('head'))
        page.setBodyBuffer(buffer('body'))
        def head = new StringWriter()
        def body = new StringWriter()
        def full = new StringWriter()

        when:
        page.writeHead(head)
        page.writeBody(body)
        page.writeOriginal(full)

        then:
        page.isUsed()
        page.getHead() == head.toString()
        head.toString() == 'head'
        page.getBody() == body.toString()
        body.toString() == 'body'
        page.getPage() == full.toString()
        full.toString() == original
        page.originalLength() == original.length()

        where:
        renderingLayout << [false, true]
    }

    void 'body output falls back to the whole page when no body has been captured'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setPageBuffer(buffer('fragment without a body tag'))
        def writer = new StringWriter()

        when:
        page.writeBody(writer)

        then:
        writer.toString() == 'fragment without a body tag'
        !page.isUsed()
    }

    void 'captured title is removed from head output but its property is retained'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setHeadBuffer(buffer('<TITLE class="main">Title</TITLE><meta name="layout" content="main">'))
        page.addProperty('title', 'Title')
        page.setTitleCaptured(true)
        def writer = new StringWriter()

        when:
        page.writeHead(writer)

        then:
        writer.toString() == '<meta name="layout" content="main">'
        page.title == 'Title'
        page.isTitleCaptured()
    }

    void 'content and object properties mark the page as used'() {
        given:
        def page = new GSPGrailsLayoutPage()
        def sidebar = buffer('Sidebar')

        when:
        page.addProperty('page.count', Integer.valueOf(3))
        page.setContentBuffer('sidebar', sidebar)

        then:
        page.isUsed()
        page.getProperty('page.count') == '3'
        page.getProperty('page.sidebar') == ''
        page.getContentBuffer('page.sidebar').is(sidebar)
        page.getContentBuffer('page.missing') == null
        GSPGrailsLayoutPage.content2htmlPage(page).is(page)
    }

    void 'reset clears captured content and flags so a page can be reused'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setHeadBuffer(buffer('head'))
        page.setBodyBuffer(buffer('body'))
        page.setPageBuffer(buffer('page'))
        page.setTitleBuffer(buffer('title'))
        page.setTitleCaptured(true)
        page.setContentBuffer('sidebar', buffer('sidebar'))

        when:
        page.reset()

        then:
        !page.isUsed()
        !page.isTitleCaptured()
        page.getHead() == null
        page.getBody() == null
        page.getPage() == null
        page.getTitleBuffer() == null
        page.getContentBuffer('page.sidebar') == null
    }

    void 'adding an object property stringifies it and keeps null as null'() {
        given:
        def page = new GSPGrailsLayoutPage()

        when:
        page.addProperty('count', (Object) 42)
        page.addProperty('nothing', (Object) null)

        then:
        page.getProperty('count') == '42'
        page.getProperty('nothing') == null
        page.isUsed()
    }

    void 'buffers prefer sub chunks unless the page renders a layout'() {
        given:
        def content = new GSPGrailsLayoutPage()
        def layout = new GSPGrailsLayoutPage(true)
        def contentBuffers = (1..4).collect { new StreamCharBuffer() }
        def layoutBuffers = (1..4).collect { new StreamCharBuffer() }

        when:
        content.setHeadBuffer(contentBuffers[0])
        content.setBodyBuffer(contentBuffers[1])
        content.setPageBuffer(contentBuffers[2])
        content.setTitleBuffer(contentBuffers[3])
        layout.setHeadBuffer(layoutBuffers[0])
        layout.setBodyBuffer(layoutBuffers[1])
        layout.setPageBuffer(layoutBuffers[2])
        layout.setTitleBuffer(layoutBuffers[3])

        then:
        contentBuffers.every { it.isPreferSubChunkWhenWritingToOtherBuffer() }
        layoutBuffers.every { !it.isPreferSubChunkWhenWritingToOtherBuffer() }
    }

    void 'null buffers can be assigned'() {
        given:
        def page = new GSPGrailsLayoutPage()

        when:
        page.setHeadBuffer(null)
        page.setBodyBuffer(null)
        page.setPageBuffer(null)
        page.setTitleBuffer(null)

        then:
        page.getHead() == null
        page.getBody() == null
        page.getPage() == null
        page.getTitleBuffer() == null
    }

    void 'writeHead strips a captured title when the title buffer is not a sub chunk of the head'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setHeadBuffer(buffer('<title>T</title><meta>'))
        page.setTitleBuffer(buffer('T'))
        page.setTitleCaptured(true)

        expect:
        written { page.writeHead(it) } == '<meta>'
    }

    void 'content2htmlPage adapts any other content'() {
        given:
        def content = Stub(Content) {
            getTitle() >> 'adapted'
        }

        when:
        HTMLPage htmlPage = GSPGrailsLayoutPage.content2htmlPage(content)

        then:
        htmlPage != null
        htmlPage.getTitle() == 'adapted'
    }

    private static StreamCharBuffer buffer(String text) {
        def buffer = new StreamCharBuffer()
        buffer.getWriter().write(text)
        buffer
    }

    private static String written(Closure writing) {
        def out = new StringWriter()
        writing(out)
        out.toString()
    }
}
