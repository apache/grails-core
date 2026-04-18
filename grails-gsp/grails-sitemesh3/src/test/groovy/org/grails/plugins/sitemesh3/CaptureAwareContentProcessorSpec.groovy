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

    private StreamCharBuffer bufferOf(String value) {
        FastStringWriter writer = new FastStringWriter()
        writer.print(value)
        writer.buffer
    }
}
