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
import org.sitemesh.SiteMeshContext
import org.sitemesh.content.Content
import org.sitemesh.content.ContentProcessor
import org.sitemesh.content.memory.InMemoryContent
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle
import org.sitemesh.webapp.WebAppContext

import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

class CaptureAwareContentProcessorSpec extends Specification {

    ContentProcessor fallback
    CaptureAwareContentProcessor processor
    MockHttpServletRequest request
    WebAppContext context

    def setup() {
        fallback = Mock(ContentProcessor)
        processor = new CaptureAwareContentProcessor(fallback)
        request = new MockHttpServletRequest()
        context = Stub(WebAppContext) {
            getRequest() >> request
            getContentToMerge() >> null
        }
    }

    void 'returns captured page when populated'() {
        given:
        Sitemesh3CapturedPage page = new Sitemesh3CapturedPage()
        page.setBodyBuffer(bufferOf('<p>hi</p>'))
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, page)

        when:
        Content content = processor.build(CharBuffer.wrap('<html></html>'), context)

        then:
        content.is(page)
        0 * fallback._
    }

    void 'falls back to the default processor when no captured page'() {
        given:
        Content fallbackContent = new InMemoryContent()

        when:
        Content content = processor.build(CharBuffer.wrap('<html></html>'), context)

        then:
        1 * fallback.build(_ as CharBuffer, _ as SiteMeshContext) >> fallbackContent
        content.is(fallbackContent)
    }

    void 'falls back when captured page is present but unused'() {
        given:
        Sitemesh3CapturedPage page = new Sitemesh3CapturedPage()
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, page)
        Content fallbackContent = new InMemoryContent()

        when:
        Content content = processor.build(CharBuffer.wrap('<html></html>'), context)

        then:
        1 * fallback.build(_ as CharBuffer, _ as SiteMeshContext) >> fallbackContent
        content.is(fallbackContent)
    }

    void 'decoration phase reuses the layout captured page with rendered content attached'() {
        given:
        Sitemesh3CapturedPage layoutPage = new Sitemesh3CapturedPage()
        layoutPage.setBodyBuffer(bufferOf('<body>from layout</body>'))
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, layoutPage)
        Content decorateContext = new InMemoryContent()
        WebAppContext decorating = Stub(WebAppContext) {
            getRequest() >> request
            getContentToMerge() >> decorateContext
        }
        String layoutOutput = '<html><body>from layout</body></html>'

        when:
        Content content = processor.build(CharBuffer.wrap(layoutOutput), decorating)

        then:
        0 * fallback._
        content.is(layoutPage)
        layoutPage.getData().getValue() == layoutOutput
    }

    void 'decoration phase falls back to parser when no capture happened'() {
        given:
        Content decorateContext = new InMemoryContent()
        Content fallbackContent = new InMemoryContent()
        WebAppContext decorating = Stub(WebAppContext) {
            getRequest() >> request
            getContentToMerge() >> decorateContext
        }

        when:
        Content content = processor.build(CharBuffer.wrap('<plain/>'), decorating)

        then:
        1 * fallback.build(_ as CharBuffer, _ as SiteMeshContext) >> fallbackContent
        content.is(fallbackContent)
    }

    void 'falls back when the context is not a web app context'() {
        given:
        Content fallbackContent = new InMemoryContent()
        SiteMeshContext plain = Stub(SiteMeshContext) {
            getContentToMerge() >> null
        }

        when:
        Content content = processor.build(CharBuffer.wrap('<html></html>'), plain)

        then:
        1 * fallback.build(_ as CharBuffer, plain) >> fallbackContent
        content.is(fallbackContent)
    }

    void 'falls back when the web app context has no request'() {
        given:
        Content fallbackContent = new InMemoryContent()
        WebAppContext noRequest = Stub(WebAppContext) {
            getRequest() >> null
            getContentToMerge() >> null
        }

        when:
        Content content = processor.build(CharBuffer.wrap('<html></html>'), noRequest)

        then:
        1 * fallback.build(_ as CharBuffer, noRequest) >> fallbackContent
        content.is(fallbackContent)
    }

    void 'falls back when the request attribute is not a captured page'() {
        given:
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, 'not a page')
        Content fallbackContent = new InMemoryContent()

        when:
        Content content = processor.build(CharBuffer.wrap('<html></html>'), context)

        then:
        1 * fallback.build(_ as CharBuffer, _ as SiteMeshContext) >> fallbackContent
        content.is(fallbackContent)
    }

    void 'a used captured page keeps the rendered original page as its data'() {
        given:
        Sitemesh3CapturedPage page = new Sitemesh3CapturedPage()
        page.setBodyBuffer(bufferOf('<p>hi</p>'))
        request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, page)
        String original = '<html><body><p>hi</p></body></html>'

        when:
        processor.build(CharBuffer.wrap(original), context)
        def out = new StringWriter()
        page.getData().writeValueTo(out)

        then:
        page.getData().getValue() == original
        out.toString() == original
    }

    void 'the default processor parses html itself when nothing was captured'() {
        given:
        def defaultProcessor = new CaptureAwareContentProcessor()

        when:
        Content content = defaultProcessor.build(CharBuffer.wrap('<html><head><title>Parsed</title></head><body>text</body></html>'), context)

        then:
        content.getExtractedProperties().getChild('title').value == 'Parsed'
        content.getExtractedProperties().getChild('body').value == 'text'
    }

    void 'a processor can be built from tag rule bundles'() {
        given:
        def bundled = new CaptureAwareContentProcessor(new CoreHtmlTagRuleBundle())

        when:
        Content content = bundled.build(CharBuffer.wrap('<html><head><title>Bundled</title></head><body>x</body></html>'), context)

        then:
        content.getExtractedProperties().getChild('title').value == 'Bundled'
    }

    private StreamCharBuffer bufferOf(String value) {
        FastStringWriter writer = new FastStringWriter()
        writer.print(value)
        writer.buffer
    }
}
