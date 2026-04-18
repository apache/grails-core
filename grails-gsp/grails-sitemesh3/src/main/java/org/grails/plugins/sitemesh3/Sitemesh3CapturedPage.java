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
package org.grails.plugins.sitemesh3;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.sitemesh.content.Content;
import org.sitemesh.content.ContentChunk;
import org.sitemesh.content.ContentProperty;
import org.sitemesh.content.memory.InMemoryContent;
import org.sitemesh.tagprocessor.CharSequenceBuffer;

import org.grails.buffer.StreamCharBuffer;

/**
 * A SiteMesh 3 {@link Content} implementation that is populated by the GSP
 * capture taglib at render time. Because the capture taglib runs during GSP
 * execution, there is no need for SiteMesh to parse the response body; the
 * data is already chunked up.
 *
 * <p>Backed by an {@link InMemoryContent} so that SiteMesh content properties
 * can be traversed in the usual way (e.g. {@code head}, {@code body}, {@code
 * title}, {@code page.<name>}, {@code meta.<name>}).</p>
 */
public class Sitemesh3CapturedPage implements Content {

    public static final String REQUEST_ATTRIBUTE = Sitemesh3CapturedPage.class.getName();

    private final InMemoryContent delegate = new InMemoryContent();

    private StreamCharBuffer headBuffer;
    private StreamCharBuffer bodyBuffer;
    private StreamCharBuffer titleBuffer;
    private StreamCharBuffer pageBuffer;

    private final Map<String, StreamCharBuffer> contentBuffers = new LinkedHashMap<>();
    private final Map<String, String> pageProperties = new HashMap<>();

    private boolean used;
    private boolean titleCaptured;
    private boolean propertiesMaterialized;

    public void setHeadBuffer(StreamCharBuffer buffer) {
        this.headBuffer = buffer;
        markUsed();
    }

    public void setBodyBuffer(StreamCharBuffer buffer) {
        this.bodyBuffer = buffer;
        markUsed();
    }

    public void setTitleBuffer(StreamCharBuffer buffer) {
        this.titleBuffer = buffer;
    }

    public void setPageBuffer(StreamCharBuffer buffer) {
        this.pageBuffer = buffer;
    }

    public StreamCharBuffer getHeadBuffer() {
        return headBuffer;
    }

    public StreamCharBuffer getBodyBuffer() {
        return bodyBuffer;
    }

    public StreamCharBuffer getTitleBuffer() {
        return titleBuffer;
    }

    public StreamCharBuffer getPageBuffer() {
        return pageBuffer;
    }

    public void addContentBuffer(String tag, StreamCharBuffer buffer) {
        contentBuffers.put(tag, buffer);
        markUsed();
    }

    public void addProperty(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        pageProperties.put(name, value);
        markUsed();
    }

    public boolean isUsed() {
        return used;
    }

    public void markUsed() {
        this.used = true;
    }

    public boolean isTitleCaptured() {
        return titleCaptured;
    }

    public void setTitleCaptured(boolean titleCaptured) {
        this.titleCaptured = titleCaptured;
    }

    /**
     * Writes the full original page (unmerged) to the given appendable.
     * Used when decoration is skipped and the caller needs to fall back to
     * the raw response.
     */
    public void writeOriginal(Appendable out) throws IOException {
        if (pageBuffer != null) {
            pageBuffer.writeTo(appendableToWriter(out));
        }
    }

    @Override
    public ContentChunk getData() {
        materializeProperties();
        return delegate.getData();
    }

    @Override
    public ContentProperty getExtractedProperties() {
        materializeProperties();
        return delegate.getExtractedProperties();
    }

    @Override
    public CharSequenceBuffer createDataOnlyBuffer() {
        return delegate.createDataOnlyBuffer();
    }

    private void materializeProperties() {
        if (propertiesMaterialized) {
            return;
        }
        propertiesMaterialized = true;

        ContentProperty root = delegate.getExtractedProperties();

        if (pageBuffer != null) {
            delegate.getData().setValue(pageBuffer.toString());
        }

        if (headBuffer != null) {
            root.getChild("head").setValue(extractHead());
        }
        if (bodyBuffer != null) {
            root.getChild("body").setValue(bodyBuffer.toString());
        }
        if (titleBuffer != null) {
            root.getChild("title").setValue(titleBuffer.toString());
        }

        for (Map.Entry<String, StreamCharBuffer> entry : contentBuffers.entrySet()) {
            root.getChild("page").getChild(entry.getKey()).setValue(entry.getValue().toString());
        }

        for (Map.Entry<String, String> entry : pageProperties.entrySet()) {
            setByDottedName(root, entry.getKey(), entry.getValue());
        }
    }

    private String extractHead() {
        String headAsString = headBuffer.toString();
        if (titleCaptured) {
            return headAsString.replaceFirst("(?is)<title(\\s[^>]*)?>(.*?)</title>", "");
        }
        return headAsString;
    }

    private void setByDottedName(ContentProperty root, String dottedName, String value) {
        String[] parts = dottedName.split("\\.");
        ContentProperty current = root;
        for (String part : parts) {
            current = current.getChild(part);
        }
        current.setValue(value);
    }

    private static java.io.Writer appendableToWriter(Appendable out) {
        if (out instanceof java.io.Writer) {
            return (java.io.Writer) out;
        }
        return new java.io.Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                out.append(java.nio.CharBuffer.wrap(cbuf, off, len));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }
}
