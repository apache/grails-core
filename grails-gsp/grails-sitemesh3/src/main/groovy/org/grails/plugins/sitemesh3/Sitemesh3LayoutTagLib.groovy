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

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import grails.artefact.TagLibrary
import grails.gsp.TagLib
import org.grails.buffer.FastStringWriter
import org.grails.buffer.GrailsPrintWriter
import org.grails.buffer.StreamCharBuffer
import org.grails.encoder.CodecLookup
import org.grails.encoder.Encoder
import org.grails.gsp.compiler.GrailsLayoutPreprocessor

/**
 * SiteMesh 3 counterpart of {@code GrailsLayoutTagLib}: populates a
 * {@link Sitemesh3CapturedPage} at GSP render time so that SiteMesh 3 can
 * decorate without parsing the HTML.
 *
 * <p>Registered under the {@code grailsLayout} namespace because the GSP
 * compile-time preprocessor ({@link GrailsLayoutPreprocessor}) rewrites
 * {@code <head>}, {@code <body>}, etc. to {@code <grailsLayout:capture*>}
 * tags.</p>
 */
@CompileStatic
@TagLib
class Sitemesh3LayoutTagLib implements TagLibrary {

    static String namespace = 'grailsLayout'

    CodecLookup codecLookup

    def captureTagContent(GrailsPrintWriter writer, String tagname, Map attrs, Object body, boolean noEndTagForEmpty = false) {
        Object content = null
        if (body != null) {
            if (body instanceof Closure) {
                content = ((Closure) body).call()
            } else {
                content = body
            }
        }

        if (content instanceof StreamCharBuffer) {
            ((StreamCharBuffer) content).setPreferSubChunkWhenWritingToOtherBuffer(true)
        }
        writer << '<'
        writer << tagname
        boolean useXmlClosingForEmptyTag = false
        if (attrs) {
            Object xmlClosingString = attrs.remove(GrailsLayoutPreprocessor.XML_CLOSING_FOR_EMPTY_TAG_ATTRIBUTE_NAME)
            if (xmlClosingString == '/') {
                useXmlClosingForEmptyTag = true
            }
            Encoder htmlEncoder = codecLookup?.lookupEncoder('HTML')
            attrs.each { k, v ->
                writer << ' '
                writer << k
                writer << '="'
                writer << (htmlEncoder != null ? htmlEncoder.encode(v) : v)
                writer << '"'
            }
        }

        if (content) {
            writer << '>'
            writer << content
            writer << '</'
            writer << tagname
            writer << '>'
        } else {
            if (!useXmlClosingForEmptyTag) {
                writer << '>'
                if (!noEndTagForEmpty) {
                    writer << '</'
                    writer << tagname
                    writer << '>'
                }
            } else {
                writer << '/>'
            }
        }
        content
    }

    StreamCharBuffer wrapContentInBuffer(Object content) {
        if (content instanceof Closure) {
            content = ((Closure) content).call()
        }
        if (!(content instanceof StreamCharBuffer)) {
            FastStringWriter stringWriter = new FastStringWriter()
            stringWriter.print(content)
            StreamCharBuffer newbuffer = stringWriter.buffer
            newbuffer.setPreferSubChunkWhenWritingToOtherBuffer(true)
            return newbuffer
        }
        (StreamCharBuffer) content
    }

    protected Sitemesh3CapturedPage findCapturedPage(HttpServletRequest request) {
        (Sitemesh3CapturedPage) request.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE)
    }

    Closure captureHead = { Map attrs, body ->
        Object content = captureTagContent(out, 'head', attrs, body)
        if (content != null) {
            Sitemesh3CapturedPage page = findCapturedPage(request)
            if (page != null) {
                page.setHeadBuffer(wrapContentInBuffer(content))
            }
        }
    }

    Closure captureBody = { Map attrs, body ->
        Object content = captureTagContent(out, 'body', attrs, body)
        if (content != null) {
            Sitemesh3CapturedPage page = findCapturedPage(request)
            if (page != null) {
                page.setBodyBuffer(wrapContentInBuffer(content))
                if (attrs) {
                    attrs.each { k, v ->
                        page.addProperty("body.${k?.toString()?.toLowerCase()}".toString(), v?.toString())
                    }
                }
            }
        }
    }

    Closure captureTitle = { Map attrs, body ->
        Sitemesh3CapturedPage page = findCapturedPage(request)
        Object content = captureTagContent(out, 'title', attrs, body)
        if (page != null && content != null) {
            page.addProperty('title', content.toString())
            page.setTitleCaptured(true)
        }
    }

    Closure wrapTitleTag = { Map attrs, body ->
        if (body != null) {
            Sitemesh3CapturedPage page = findCapturedPage(request)
            if (page != null) {
                StreamCharBuffer wrapped = wrapContentInBuffer(body)
                page.setTitleBuffer(wrapped)
                out << wrapped
            } else if (body instanceof Closure) {
                out << ((Closure) body).call()
            }
        }
    }

    Closure captureMeta = { Map attrs, body ->
        captureTagContent(out, 'meta', attrs, body, true)
        Sitemesh3CapturedPage page = findCapturedPage(request)
        Object val = attrs?.content
        if (attrs && page != null && val != null) {
            String valueStr = val.toString()
            if (attrs.name) {
                page.addProperty("meta.${attrs.name}".toString(), valueStr)
                page.addProperty("meta.${attrs.name.toString().toLowerCase()}".toString(), valueStr)
            } else if (attrs['http-equiv']) {
                String httpEquiv = attrs['http-equiv'] as String
                List<String> httpEquivFormats = [httpEquiv, httpEquiv.toLowerCase()]
                if (httpEquiv.equalsIgnoreCase('content-type')) {
                    httpEquivFormats << 'Content-Type'
                }
                for (String format : httpEquivFormats) {
                    page.addProperty("meta.http-equiv.${format}".toString(), valueStr)
                }
            }
        }
    }

    Closure captureContent = { Map attrs, body ->
        if (body != null) {
            Sitemesh3CapturedPage page = findCapturedPage(request)
            if (page != null && attrs.tag) {
                page.addContentBuffer(attrs.tag as String, wrapContentInBuffer(body))
            }
        }
    }

    Closure parameter = { Map attrs, body ->
        Sitemesh3CapturedPage page = findCapturedPage(request)
        String name = attrs.name?.toString()
        String val = attrs.value?.toString()
        if (page != null && name && val != null) {
            page.addProperty("page.${name}".toString(), val)
        }
    }
}
