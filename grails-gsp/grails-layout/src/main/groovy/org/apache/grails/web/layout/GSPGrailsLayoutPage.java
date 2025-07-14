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
package org.apache.grails.web.layout;

import com.opensymphony.module.sitemesh.HTMLPage;
import com.opensymphony.module.sitemesh.parser.AbstractHTMLPage;
import com.opensymphony.sitemesh.Content;
import com.opensymphony.sitemesh.compatability.Content2HTMLPage;
import org.grails.buffer.StreamCharBuffer;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * Grails/GSP specific implementation of Sitemesh's AbstractHTMLPage
 * <p>
 * g:capture* tags in RenderTagLib are used to capture head, meta, title, component and body contents.
 * No html parsing is required for templating since capture tags are added at GSP compilation time.
 *
 * @author Lari Hotari, Sagire Software Oy
 */
public class GSPGrailsLayoutPage extends AbstractHTMLPage implements Content {

    StreamCharBuffer headBuffer;
    StreamCharBuffer bodyBuffer;
    StreamCharBuffer pageBuffer;
    StreamCharBuffer titleBuffer;
    boolean used;
    boolean titleCaptured;
    Map<String, StreamCharBuffer> contentBuffers;
    private boolean renderingLayout;

    public GSPGrailsLayoutPage() {
        this(false);
    }

    public GSPGrailsLayoutPage(boolean renderingLayout) {
        reset();
        this.renderingLayout = renderingLayout;
    }

    public void reset() {
        headBuffer = null;
        bodyBuffer = null;
        pageBuffer = null;
        titleBuffer = null;
        used = false;
        titleCaptured = false;
        contentBuffers = null;
        renderingLayout = false;
    }

    public void addProperty(String name, Object value) {
        addProperty(name, (value == null ? null : String.valueOf(value)));
    }

    @Override
    public void addProperty(String name, String value) {
        super.addProperty(name, value);
        used = true;
    }

    @Override
    public void writeHead(Writer out) throws IOException {
        if (headBuffer == null) {
            return;
        }

        if (titleCaptured) {
            if (titleBuffer != null) {
                int headlen = headBuffer.length();
                titleBuffer.clear();
                if (headBuffer.length() < headlen) {
                    headBuffer.writeTo(out);
                    return;
                }
            }
            String headAsString = headBuffer.toString();
            // strip out title for Grails Layout version of <head>
            out.write(headAsString.replaceFirst("(?is)<title(\\s[^>]*)?>(.*?)</title>", ""));
        } else {
            headBuffer.writeTo(out);
        }
    }

    @Override
    public void writeBody(Writer out) throws IOException {
        if (bodyBuffer != null) {
            bodyBuffer.writeTo(out);
        } else if (pageBuffer != null) {
            // no body was captured, so write the whole page content
            pageBuffer.writeTo(out);
        }
    }

    @Override
    public void writePage(Writer out) throws IOException {
        if (pageBuffer != null) {
            pageBuffer.writeTo(out);
        }
    }

    public String getHead() {
        if (headBuffer != null) {
            return headBuffer.toString();
        }
        return null;
    }

    @Override
    public String getBody() {
        if (bodyBuffer != null) {
            return bodyBuffer.toString();
        }
        return null;
    }

    @Override
    public String getPage() {
        if (pageBuffer != null) {
            return pageBuffer.toString();
        }
        return null;
    }

    public int originalLength() {
        return pageBuffer.size();
    }

    public void writeOriginal(Writer writer) throws IOException {
        writePage(writer);
    }

    public void setHeadBuffer(StreamCharBuffer headBuffer) {
        this.headBuffer = headBuffer;
        applyStreamCharBufferSettings(headBuffer);
        used = true;
    }

    private void applyStreamCharBufferSettings(StreamCharBuffer buffer) {
        if (!renderingLayout && buffer != null) {
            buffer.setPreferSubChunkWhenWritingToOtherBuffer(true);
        }
    }

    public void setBodyBuffer(StreamCharBuffer bodyBuffer) {
        this.bodyBuffer = bodyBuffer;
        applyStreamCharBufferSettings(bodyBuffer);
        used = true;
    }

    public void setPageBuffer(StreamCharBuffer pageBuffer) {
        this.pageBuffer = pageBuffer;
        applyStreamCharBufferSettings(pageBuffer);
    }

    public void setTitleBuffer(StreamCharBuffer titleBuffer) {
        this.titleBuffer = titleBuffer;
        applyStreamCharBufferSettings(titleBuffer);
    }

    public StreamCharBuffer getTitleBuffer() {
        return titleBuffer;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    /**
     * @param tagName "tagName" name of buffer (without "page." prefix)
     * @param buffer
     */
    public void setContentBuffer(String tagName, StreamCharBuffer buffer) {
        used = true;
        if (contentBuffers == null) {
            contentBuffers = new HashMap<String, StreamCharBuffer>();
        }
        String propertyName = "page." + tagName;
        contentBuffers.put(propertyName, buffer);
        // just mark that the property is set
        super.addProperty(propertyName, "");
    }

    /**
     * @param name propertyName of contentBuffer (with "page." prefix)
     * @return the buffer for the specified name
     */
    public Object getContentBuffer(String name) {
        if (contentBuffers == null) {
            return null;
        }
        return contentBuffers.get(name);
    }

    public static HTMLPage content2htmlPage(Content content) {
        HTMLPage htmlPage = null;
        if (content instanceof HTMLPage) {
            htmlPage = (HTMLPage) content;
        } else if (content instanceof TokenizedHTMLPage2Content) {
            htmlPage = ((TokenizedHTMLPage2Content) content).getPage();
        } else {
            htmlPage = new Content2HTMLPage(content, null);
        }
        return htmlPage;
    }

    public boolean isTitleCaptured() {
        return titleCaptured;
    }

    public void setTitleCaptured(boolean titleCaptured) {
        this.titleCaptured = titleCaptured;
    }
}
