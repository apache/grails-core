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

import java.nio.CharBuffer

import org.grails.buffer.FastStringWriter
import org.grails.buffer.StreamCharBuffer

import spock.lang.Specification

class Sitemesh3CapturedPageSpec extends Specification {

    void "strips the captured title element from the head"() {
        given:
        Sitemesh3CapturedPage page = pageWithHead('<meta charset="UTF-8"><title>My Page</title><link rel="x">')

        expect:
        headOf(page) == '<meta charset="UTF-8"><link rel="x">'
    }

    void "strips a title element that carries attributes"() {
        given:
        Sitemesh3CapturedPage page = pageWithHead('<title data-x="1">My Page</title><meta charset="UTF-8">')

        expect:
        headOf(page) == '<meta charset="UTF-8">'
    }

    void "elements whose names merely start with title are not treated as the title"() {
        given: 'a custom element before the real title'
        Sitemesh3CapturedPage page = pageWithHead('<titlebar>tools</titlebar><title>My Page</title><meta charset="UTF-8">')

        expect: 'only the real title is removed'
        headOf(page) == '<titlebar>tools</titlebar><meta charset="UTF-8">'
    }

    void "a head with only title-prefixed custom elements is left untouched"() {
        given:
        Sitemesh3CapturedPage page = pageWithHead('<title-x>tools</title-x><meta charset="UTF-8">')

        expect:
        headOf(page) == '<title-x>tools</title-x><meta charset="UTF-8">'
    }

    void "a new page is unused, has no buffers and no title captured"() {
        given:
        def page = new Sitemesh3CapturedPage()

        expect:
        !page.isUsed()
        !page.isTitleCaptured()
        page.getHeadBuffer() == null
        page.getBodyBuffer() == null
        page.getTitleBuffer() == null
        page.getPageBuffer() == null
        Sitemesh3CapturedPage.REQUEST_ATTRIBUTE == Sitemesh3CapturedPage.name
    }

    void "head, body, content buffers, properties and rendered content mark the page used"() {
        when:
        def page = new Sitemesh3CapturedPage()
        action(page)

        then:
        page.isUsed()

        where:
        action << [
                { Sitemesh3CapturedPage p -> p.setHeadBuffer(new StreamCharBuffer()) },
                { Sitemesh3CapturedPage p -> p.setBodyBuffer(new StreamCharBuffer()) },
                { Sitemesh3CapturedPage p -> p.setRenderedContent('x') },
                { Sitemesh3CapturedPage p -> p.addContentBuffer('side', new StreamCharBuffer()) },
                { Sitemesh3CapturedPage p -> p.addProperty('a', 'b') },
                { Sitemesh3CapturedPage p -> p.markUsed() }
        ]
    }

    void "title and page buffers are stored without marking the page used"() {
        given:
        def page = new Sitemesh3CapturedPage()
        def title = bufferOf('t')
        def whole = bufferOf('p')

        when:
        page.setTitleBuffer(title)
        page.setPageBuffer(whole)

        then:
        !page.isUsed()
        page.getTitleBuffer().is(title)
        page.getPageBuffer().is(whole)
    }

    void "properties with a null name or value are ignored"() {
        given:
        def page = new Sitemesh3CapturedPage()

        when:
        page.addProperty(null, 'v')
        page.addProperty('n', null)

        then:
        !page.isUsed()
    }

    void "the title can be flagged as captured"() {
        given:
        def page = new Sitemesh3CapturedPage()

        when:
        page.setTitleCaptured(true)

        then:
        page.isTitleCaptured()
    }

    void "writeOriginal writes the page buffer to a writer"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setPageBuffer(bufferOf('<html>original</html>'))
        def out = new StringWriter()

        when:
        page.writeOriginal(out)

        then:
        out.toString() == '<html>original</html>'
    }

    void "writeOriginal adapts a plain appendable"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setPageBuffer(bufferOf('<html>original</html>'))
        def out = new StringBuilder()

        when:
        page.writeOriginal(out)

        then:
        out.toString() == '<html>original</html>'
    }

    void "writeOriginal writes nothing without a page buffer"() {
        given:
        def out = new StringBuilder()

        when:
        new Sitemesh3CapturedPage().writeOriginal(out)

        then:
        out.length() == 0
    }

    void "the extracted properties expose head, body, title and named page content buffers"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setHeadBuffer(bufferOf('<meta>'))
        page.setBodyBuffer(bufferOf('body text'))
        page.setTitleBuffer(bufferOf('My Title'))
        page.addContentBuffer('sidebar', bufferOf('side'))
        page.addContentBuffer('footer', bufferOf('foot'))
        def props = page.getExtractedProperties()

        expect:
        props.getChild('head').value == '<meta>'
        props.getChild('body').value == 'body text'
        props.getChild('title').value == 'My Title'
        props.getChild('page').getChild('sidebar').value == 'side'
        props.getChild('page').getChild('footer').value == 'foot'
    }

    void "dotted page properties become nested content properties"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.addProperty('meta.author', 'someone')
        page.addProperty('html.lang', 'en')
        page.addProperty('simple', 'value')
        def props = page.getExtractedProperties()

        expect:
        props.getChild('meta').getChild('author').value == 'someone'
        props.getChild('html').getChild('lang').value == 'en'
        props.getChild('simple').value == 'value'
    }

    void "properties are only materialized once"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setBodyBuffer(bufferOf('first'))
        def first = page.getExtractedProperties().getChild('body').value

        when:
        page.setBodyBuffer(bufferOf('second'))

        then:
        first == 'first'
        page.getExtractedProperties().getChild('body').value == 'first'
        page.getData() != null
    }

    void "the data defaults to the page buffer content"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setPageBuffer(bufferOf('<html>whole</html>'))

        expect:
        page.getData().getValue() == '<html>whole</html>'
    }

    void "the data is empty without a page buffer or rendered content"() {
        expect:
        !new Sitemesh3CapturedPage().getData().hasValue()
    }

    void "a data only buffer can be created"() {
        expect:
        new Sitemesh3CapturedPage().createDataOnlyBuffer() != null
    }

    void "rendered content replaces the data and is exposed through the content chunk"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setRenderedContent('<html>done</html>')

        when:
        def chunk = page.getData()

        then:
        chunk.hasValue()
        chunk.getValue() == '<html>done</html>'
        chunk.getNonNullValue() == '<html>done</html>'
        chunk.getOwningContent().is(page)
    }

    void "rendered content chunk writes a char buffer backed by an array directly to a writer"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setRenderedContent(CharBuffer.wrap('xx<html/>yy'.toCharArray(), 2, 7))
        def out = new StringWriter()

        when:
        page.getData().writeValueTo(out)

        then:
        out.toString() == '<html/>'
    }

    void "rendered content chunk appends char buffers without an array and non writer targets"() {
        given:
        def direct = CharBuffer.wrap('<html/>').asReadOnlyBuffer()
        def page = new Sitemesh3CapturedPage()
        page.setRenderedContent(direct)
        def writerOut = new StringWriter()
        def builderOut = new StringBuilder()

        when:
        page.getData().writeValueTo(writerOut)
        page.getData().writeValueTo(builderOut)

        then:
        !direct.hasArray()
        writerOut.toString() == '<html/>'
        builderOut.toString() == '<html/>'
    }

    void "rendered content chunk writes plain strings to any appendable"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setRenderedContent('plain')
        def out = new StringBuilder()

        when:
        page.getData().writeValueTo(out)

        then:
        out.toString() == 'plain'
    }

    void "the rendered content chunk value can be replaced and cleared"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setRenderedContent('original')
        def chunk = page.getData()

        when:
        chunk.setValue('replaced')

        then:
        chunk.getValue() == 'replaced'

        when:
        chunk.setValue(null)
        def out = new StringBuilder()
        chunk.writeValueTo(out)

        then:
        !chunk.hasValue()
        chunk.getValue() == null
        chunk.getNonNullValue() == ''
        out.length() == 0
    }

    void "the head is returned unchanged when the title was not captured separately"() {
        given:
        def page = new Sitemesh3CapturedPage()
        page.setHeadBuffer(bufferOf('<title>Keep</title><meta>'))

        expect:
        headOf(page) == '<title>Keep</title><meta>'
    }

    void "the head is returned unchanged when it has no title element"() {
        expect:
        headOf(pageWithHead('<meta charset="UTF-8">')) == '<meta charset="UTF-8">'
    }

    void "a title open tag is matched case insensitively"() {
        expect:
        headOf(pageWithHead('<meta><TITLE>My Page</TiTlE><link>')) == '<meta><link>'
    }

    void "a title open tag followed by whitespace and a head ending in a bare title prefix are handled"() {
        expect:
        headOf(pageWithHead('<meta><title\nlang="en">My Page</title>')) == '<meta>'
        headOf(pageWithHead('<meta><title')) == '<meta><title'
    }

    void "an unterminated title open tag leaves the head unchanged"() {
        expect:
        headOf(pageWithHead('<meta><title lang="en"')) == '<meta><title lang="en"'
    }

    void "a title without a closing tag leaves the head unchanged"() {
        expect:
        headOf(pageWithHead('<meta><title>unclosed')) == '<meta><title>unclosed'
    }

    private Sitemesh3CapturedPage pageWithHead(String head) {
        Sitemesh3CapturedPage page = new Sitemesh3CapturedPage()
        page.setHeadBuffer(bufferOf(head))
        page.setTitleBuffer(bufferOf('My Page'))
        page.setTitleCaptured(true)
        page
    }

    private String headOf(Sitemesh3CapturedPage page) {
        page.getExtractedProperties().getChild('head').value
    }

    private StreamCharBuffer bufferOf(String value) {
        FastStringWriter writer = new FastStringWriter()
        writer.print(value)
        writer.buffer
    }
}
