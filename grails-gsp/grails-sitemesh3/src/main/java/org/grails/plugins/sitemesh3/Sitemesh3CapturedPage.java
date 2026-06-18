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
import java.io.Writer;
import java.nio.CharBuffer;
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
    private CharSequence renderedContent;

    private final Map<String, StreamCharBuffer> contentBuffers = new LinkedHashMap<>();
    private final Map<String, String> pageProperties = new HashMap<>();

    // Volatile: a captured page can be passed to an async dispatch thread
    // (Grails 7 supports @Async controller returns and Callable-returning
    // actions). Without volatile, the JMM gives no happens-before guarantee
    // on these flags across threads.
    private volatile boolean used;
    private volatile boolean titleCaptured;
    private volatile boolean propertiesMaterialized;

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

    // Attaches fully-rendered content (e.g. a layout's output after
    // inline-expanded taglibs have run) as the page's data, bypassing the
    // HTML parse step that would otherwise build the data from captured
    // buffers. Held as a CharSequence so callers can pass a CharBuffer
    // straight through without allocating an intermediate String — the
    // RawDataChunk writes via Writer.write(char[], int, int) when possible.
    public void setRenderedContent(CharSequence content) {
        this.renderedContent = content;
        markUsed();
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
        if (renderedContent != null) {
            return new RawDataChunk(renderedContent, this);
        }
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
        // Double-checked locking: two async-dispatch threads could both see
        // propertiesMaterialized == false before either sets it. Without the
        // synchronized block, both would walk and mutate the InMemoryContent
        // delegate concurrently, leaving it in a partially-initialized state.
        synchronized (this) {
            if (propertiesMaterialized) {
                return;
            }
            propertiesMaterialized = true;
            doMaterializeProperties();
        }
    }

    private void doMaterializeProperties() {
        ContentProperty root = delegate.getExtractedProperties();

        // pageBuffer is only set for fallback paths where the full rendered
        // output is wrapped; renderedContent is the hot path (handled by
        // getData() returning a RawDataChunk directly, no setValue needed).
        if (pageBuffer != null) {
            delegate.getData().setValue(pageBuffer);
        }

        if (headBuffer != null) {
            // extractHead() strips the <title> via regex, so it materializes
            // as String — the other captures pass through as CharSequence.
            root.getChild("head").setValue(extractHead());
        }
        if (bodyBuffer != null) {
            root.getChild("body").setValue(bodyBuffer);
        }
        if (titleBuffer != null) {
            root.getChild("title").setValue(titleBuffer);
        }

        for (Map.Entry<String, StreamCharBuffer> entry : contentBuffers.entrySet()) {
            root.getChild("page").getChild(entry.getKey()).setValue(entry.getValue());
        }

        for (Map.Entry<String, String> entry : pageProperties.entrySet()) {
            setByDottedName(root, entry.getKey(), entry.getValue());
        }
    }

    // Returns the head section without the <title>...</title> block when
    // the title was separately captured. Scans the buffer as a CharSequence
    // rather than materializing it to a String and running a regex —
    // saves ~head-size bytes of allocation per decorated request, and
    // avoids regex compilation on the hot path.
    private CharSequence extractHead() {
        CharSequence head = headBuffer;
        if (!titleCaptured) {
            return head;
        }
        int titleStart = indexOfTitleOpenTag(head);
        if (titleStart < 0) {
            return head;
        }
        int openTagEnd = indexOf(head, '>', titleStart + 6);
        if (openTagEnd < 0) {
            return head;
        }
        int closeStart = indexOfIgnoreCase(head, "</title>", openTagEnd + 1);
        if (closeStart < 0) {
            return head;
        }
        int closeEnd = closeStart + 8;
        int len = head.length();
        StringBuilder sb = new StringBuilder(len - (closeEnd - titleStart));
        sb.append(head, 0, titleStart);
        sb.append(head, closeEnd, len);
        return sb;
    }

    // Finds the start of a real <title> open tag. A bare "<title" prefix
    // match is not enough: it would also match elements such as
    // <titlebar> or <title-x> and mis-slice the head, so the character
    // following the prefix must terminate the tag name ('>' or
    // whitespace before attributes).
    private static int indexOfTitleOpenTag(CharSequence head) {
        int len = head.length();
        int from = 0;
        while (true) {
            int i = indexOfIgnoreCase(head, "<title", from);
            if (i < 0) {
                return -1;
            }
            int boundary = i + 6;
            if (boundary < len) {
                char c = head.charAt(boundary);
                if (c == '>' || Character.isWhitespace(c)) {
                    return i;
                }
            }
            from = i + 1;
        }
    }

    private static int indexOfIgnoreCase(CharSequence seq, String needle, int fromIndex) {
        int needleLen = needle.length();
        int max = seq.length() - needleLen;
        outer:
        for (int i = fromIndex; i <= max; i++) {
            for (int j = 0; j < needleLen; j++) {
                char c = seq.charAt(i + j);
                char n = needle.charAt(j);
                if (Character.toLowerCase(c) != Character.toLowerCase(n)) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int indexOf(CharSequence seq, char target, int fromIndex) {
        int len = seq.length();
        for (int i = fromIndex; i < len; i++) {
            if (seq.charAt(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private void setByDottedName(ContentProperty root, String dottedName, String value) {
        String[] parts = dottedName.split("\\.");
        ContentProperty current = root;
        for (String part : parts) {
            current = current.getChild(part);
        }
        current.setValue(value);
    }

    // ContentChunk whose writeValueTo emits the raw rendered content verbatim
    // instead of re-walking the property tree (which is InMemoryContent's
    // default behavior). Used when the captured page carries pre-rendered
    // layout output that has already had its placeholders inlined.
    //
    // Holds the value as CharSequence so no String copy is made at
    // construction time. writeValueTo takes a fast path through
    // Writer.write(char[], int, int) when the sequence is a CharBuffer with
    // an accessible backing array — the common case, since we receive
    // CharBuffers out of BaseSiteMeshContext's CharArrayWriter. Falls back
    // to Appendable.append for any other CharSequence shape.
    private static final class RawDataChunk implements ContentChunk {
        private CharSequence value;
        private final Content owner;

        RawDataChunk(CharSequence value, Content owner) {
            this.value = value;
            this.owner = owner;
        }

        @Override
        public boolean hasValue() {
            return value != null;
        }

        @Override
        public String getValue() {
            return value == null ? null : value.toString();
        }

        @Override
        public String getNonNullValue() {
            return value == null ? "" : value.toString();
        }

        @Override
        public void writeValueTo(Appendable out) throws IOException {
            if (value == null) {
                return;
            }
            if (out instanceof Writer && value instanceof CharBuffer) {
                CharBuffer cb = (CharBuffer) value;
                if (cb.hasArray()) {
                    ((Writer) out).write(cb.array(),
                            cb.arrayOffset() + cb.position(),
                            cb.remaining());
                    return;
                }
            }
            out.append(value);
        }

        @Override
        public void setValue(CharSequence newValue) {
            this.value = newValue;
        }

        @Override
        public Content getOwningContent() {
            return owner;
        }
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
