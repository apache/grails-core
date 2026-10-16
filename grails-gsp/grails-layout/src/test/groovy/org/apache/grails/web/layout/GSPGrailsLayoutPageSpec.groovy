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
import com.opensymphony.module.sitemesh.parser.TokenizedHTMLPage
import com.opensymphony.module.sitemesh.html.util.CharArray
import com.opensymphony.sitemesh.Content
import spock.lang.Specification

import org.grails.buffer.StreamCharBuffer

/**
 * Tests the buffer bookkeeping and property handling of {@link GSPGrailsLayoutPage}.
 */
class GSPGrailsLayoutPageSpec extends Specification {

    private static StreamCharBuffer bufferOf(String text) {
        def buffer = new StreamCharBuffer()
        buffer.writer.write(text)
        buffer
    }

    private static String written(Closure writing) {
        def out = new StringWriter()
        writing(out)
        out.toString()
    }

    void 'a new page is unused and has no buffers'() {
        given:
        def page = new GSPGrailsLayoutPage()

        expect:
        !page.isUsed()
        !page.isTitleCaptured()
        page.getHeadBuffer() == null
        page.getBodyBuffer() == null
        page.getPageBuffer() == null
        page.getTitleBuffer() == null
        page.getHead() == null
        page.getBody() == null
        page.getPage() == null
        page.getContentBuffer('page.x') == null
    }

    void 'reset discards buffers, flags and content buffers'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setHeadBuffer(bufferOf('h'))
        page.setBodyBuffer(bufferOf('b'))
        page.setPageBuffer(bufferOf('p'))
        page.setTitleBuffer(bufferOf('t'))
        page.setTitleCaptured(true)
        page.setContentBuffer('x', bufferOf('c'))

        when:
        page.reset()

        then:
        !page.isUsed()
        !page.isTitleCaptured()
        page.getHeadBuffer() == null
        page.getBodyBuffer() == null
        page.getPageBuffer() == null
        page.getTitleBuffer() == null
        page.getContentBuffer('page.x') == null
    }

    void 'adding a string property marks the page used and makes it readable'() {
        given:
        def page = new GSPGrailsLayoutPage()

        when:
        page.addProperty('meta.title', 'hello')

        then:
        page.isUsed()
        page.getProperty('meta.title') == 'hello'
        page.getProperty('meta.missing') == null
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

    void 'dynamic property lookups resolve SiteMesh properties by name'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.addProperty('meta.title', 'dynamic')

        expect:
        page.'meta.title' == 'dynamic'
    }

    void 'buffer setters flag head and body as used but not page and title buffers'() {
        given:
        def page = new GSPGrailsLayoutPage()

        when:
        page.setPageBuffer(bufferOf('p'))
        page.setTitleBuffer(bufferOf('t'))

        then:
        !page.isUsed()

        when:
        page.setHeadBuffer(bufferOf('h'))

        then:
        page.isUsed()

        when:
        def other = new GSPGrailsLayoutPage()
        other.setBodyBuffer(bufferOf('b'))

        then:
        other.isUsed()
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

    void 'used and titleCaptured flags can be assigned directly'() {
        given:
        def page = new GSPGrailsLayoutPage()

        when:
        page.setUsed(true)
        page.setTitleCaptured(true)

        then:
        page.isUsed()
        page.isTitleCaptured()
    }

    void 'string accessors return the buffer contents'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setHeadBuffer(bufferOf('<meta>'))
        page.setBodyBuffer(bufferOf('<p>body</p>'))
        page.setPageBuffer(bufferOf('<html>page</html>'))

        expect:
        page.getHead() == '<meta>'
        page.getBody() == '<p>body</p>'
        page.getPage() == '<html>page</html>'
        page.originalLength() == '<html>page</html>'.length()
    }

    void 'writeHead writes nothing without a head buffer'() {
        expect:
        written { new GSPGrailsLayoutPage().writeHead(it) } == ''
    }

    void 'writeHead writes the head unchanged when no title was captured'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setHeadBuffer(bufferOf('<title>T</title><meta>'))

        expect:
        written { page.writeHead(it) } == '<title>T</title><meta>'
    }

    void 'writeHead strips a captured title that was written to the head as plain text'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setHeadBuffer(bufferOf('<meta><TITLE lang="en">Some\nTitle</TITLE><link>'))
        page.setTitleCaptured(true)

        expect:
        written { page.writeHead(it) } == '<meta><link>'
    }

    void 'writeHead strips a captured title when the title buffer is not a sub chunk of the head'() {
        given:
        def page = new GSPGrailsLayoutPage()
        page.setHeadBuffer(bufferOf('<title>T</title><meta>'))
        page.setTitleBuffer(bufferOf('T'))
        page.setTitleCaptured(true)

        expect:
        written { page.writeHead(it) } == '<meta>'
    }

    void 'writeHead removes the title buffer sub chunk from the head when it was captured as a sub buffer'() {
        given:
        def page = new GSPGrailsLayoutPage()
        def title = bufferOf('<title>T</title>')
        page.setTitleBuffer(title)
        def head = new StreamCharBuffer()
        head.writer.write('<meta>')
        title.writeTo(head.writer)
        head.writer.write('<link>')
        page.setHeadBuffer(head)
        page.setTitleCaptured(true)

        expect:
        written { page.writeHead(it) } == '<meta><link>'
    }

    void 'writeBody prefers the body buffer, falls back to the page buffer and tolerates neither'() {
        given:
        def page = new GSPGrailsLayoutPage()

        expect:
        written { page.writeBody(it) } == ''

        when:
        page.setPageBuffer(bufferOf('whole page'))

        then:
        written { page.writeBody(it) } == 'whole page'

        when:
        page.setBodyBuffer(bufferOf('only body'))

        then:
        written { page.writeBody(it) } == 'only body'
    }

    void 'writePage and writeOriginal write the page buffer'() {
        given:
        def page = new GSPGrailsLayoutPage()

        expect:
        written { page.writePage(it) } == ''
        written { page.writeOriginal(it) } == ''

        when:
        page.setPageBuffer(bufferOf('the page'))

        then:
        written { page.writePage(it) } == 'the page'
        written { page.writeOriginal(it) } == 'the page'
    }

    void 'content buffers are stored under the page prefix and mark the page used'() {
        given:
        def page = new GSPGrailsLayoutPage()
        def buffer = bufferOf('side')

        when:
        page.setContentBuffer('sidebar', buffer)

        then:
        page.isUsed()
        page.getContentBuffer('page.sidebar').is(buffer)
        page.getContentBuffer('page.other') == null
        page.getProperty('page.sidebar') == ''

        when:
        page.setContentBuffer('footer', bufferOf('foot'))

        then:
        page.getContentBuffer('page.footer') != null
        page.getContentBuffer('page.sidebar').is(buffer)
    }

    void 'content2htmlPage returns an HTMLPage unchanged'() {
        given:
        def page = new GSPGrailsLayoutPage()

        expect:
        GSPGrailsLayoutPage.content2htmlPage(page).is(page)
    }

    void 'content2htmlPage unwraps a tokenized page content'() {
        given:
        def data = '<html><body>x</body></html>'.toCharArray()
        def tokenized = new TokenizedHTMLPage(data, new CharArray(16), new CharArray(16))
        def content = new TokenizedHTMLPage2Content(tokenized)

        expect:
        GSPGrailsLayoutPage.content2htmlPage(content).is(tokenized)
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
}
