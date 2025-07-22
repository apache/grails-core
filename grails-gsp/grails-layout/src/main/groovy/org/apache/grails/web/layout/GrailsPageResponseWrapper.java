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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.grails.buffer.GrailsRoutablePrintWriter;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.buffer.GrailsPrintWriterAdapter;
import org.grails.buffer.StreamByteBuffer;
import org.grails.buffer.StreamCharBuffer;
import org.grails.web.util.WebUtils;

import com.opensymphony.module.sitemesh.Page;
import com.opensymphony.module.sitemesh.PageParser;
import com.opensymphony.module.sitemesh.PageParserSelector;
import com.opensymphony.module.sitemesh.filter.HttpContentType;
import com.opensymphony.module.sitemesh.filter.RoutableServletOutputStream;
import com.opensymphony.module.sitemesh.filter.TextEncoder;

/**
 * @author Graeme Rocher
 * @since 1.0.4
 */
public class GrailsPageResponseWrapper extends HttpServletResponseWrapper {

    private final GrailsRoutablePrintWriter routablePrintWriter;
    private final RoutableServletOutputStream routableServletOutputStream;
    private final PageParserSelector parserSelector;
    private final HttpServletRequest request;

    private GrailsBuffer buffer;
    private boolean parseablePage = false;
    private GSPGrailsLayoutPage gspGrailsLayoutPage;

    public GrailsPageResponseWrapper(final HttpServletRequest request, final HttpServletResponse response,
                                     PageParserSelector parserSelector) {
        super(response);
        this.parserSelector = parserSelector;

        routablePrintWriter = GrailsRoutablePrintWriter.newInstance(new GrailsRoutablePrintWriter.DestinationFactory() {
            public PrintWriter activateDestination() throws IOException {
                return response.getWriter();
            }
        });
        routableServletOutputStream = new RoutableServletOutputStream(new RoutableServletOutputStream.DestinationFactory() {
            public ServletOutputStream create() throws IOException {
                return response.getOutputStream();
            }
        });

        this.request = request;

        gspGrailsLayoutPage = (GSPGrailsLayoutPage) request.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE);

        applyContentType(response.getContentType());
    }

    @Override
    public void sendError(int sc) throws IOException {
        clearBuffer();
        GrailsWebRequest webRequest = GrailsWebRequest.lookup();
        try {
            super.sendError(sc);
        } finally {
            if (webRequest != null) {
                WebUtils.storeGrailsWebRequest(webRequest);
            }
        }
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        clearBuffer();
        GrailsWebRequest webRequest = GrailsWebRequest.lookup();
        try {
            super.sendError(sc, msg);
        } finally {
            if (webRequest != null) {
                WebUtils.storeGrailsWebRequest(webRequest);
            }
        }
    }

    /**
     * Set the content-type of the request and store it so it can
     * be passed to the {@link com.opensymphony.module.sitemesh.PageParser}.
     */
    @Override
    public void setContentType(String type) {
        super.setContentType(type);
        applyContentType(type);
    }

    protected void applyContentType(String type) {
        if (type == null) {
            return;
        }

        HttpContentType httpContentType = new HttpContentType(type);

        if (parserSelector.shouldParsePage(httpContentType.getType())) {
            activateGrailsLayout(httpContentType.getType(), httpContentType.getEncoding());
        } else {
            deactivateGrailsLayout();
        }
    }

    public void activateGrailsLayout(String contentType, String encoding) {
        if (parseablePage) {
            return; // already activated
        }

        buffer = new GrailsBuffer(parserSelector, contentType, encoding, gspGrailsLayoutPage);
        routablePrintWriter.updateDestination(new GrailsRoutablePrintWriter.DestinationFactory() {
            public PrintWriter activateDestination() {
                return buffer.getWriter();
            }
        });
        routablePrintWriter.blockFlushAndClose();
        routableServletOutputStream.updateDestination(new RoutableServletOutputStream.DestinationFactory() {
            public ServletOutputStream create() {
                return buffer.getOutputStream();
            }
        });
        parseablePage = true;
    }

    public void deactivateGrailsLayout() {
        parseablePage = false;
        buffer = null;
        if (gspGrailsLayoutPage != null) {
            gspGrailsLayoutPage.reset();
        }
        routablePrintWriter.updateDestination(new GrailsRoutablePrintWriter.DestinationFactory() {
            public PrintWriter activateDestination() throws IOException {
                return getResponse().getWriter();
            }
        });
        routablePrintWriter.unBlockFlushAndClose();
        routableServletOutputStream.updateDestination(new RoutableServletOutputStream.DestinationFactory() {
            public ServletOutputStream create() throws IOException {
                return getResponse().getOutputStream();
            }
        });
    }

    /**
     * Prevent content-length being set if page is parseable.
     */
    @Override
    public void setContentLength(int contentLength) {
        if (!parseablePage) super.setContentLength(contentLength);
    }

    /**
     * Prevent buffer from being flushed if this is a page being parsed.
     */
    @Override
    public void flushBuffer() throws IOException {
        if (!parseablePage) super.flushBuffer();
    }

    /**
     * Prevent content-length being set if page is parseable.
     */
    @Override
    public void setHeader(String name, String value) {
        if (name.equalsIgnoreCase("content-type")) { // ensure ContentType is always set through setContentType()
            setContentType(value);
        } else if (!parseablePage || !name.equalsIgnoreCase("content-length")) {
            super.setHeader(name, value);
        }
    }

    /**
     * Prevent content-length being set if page is parseable.
     */
    @Override
    public void addHeader(String name, String value) {
        if (name.equalsIgnoreCase("content-type")) { // ensure ContentType is always set through setContentType()
            setContentType(value);
        } else if (!parseablePage || !name.equalsIgnoreCase("content-length")) {
            super.addHeader(name, value);
        }
    }

    /**
     * If 'not modified' (304) HTTP status is being sent - then abort parsing, as there shouldn't be any body
     */
    @Override
    public void setStatus(int sc) {
        if (sc == HttpServletResponse.SC_NOT_MODIFIED) {
            // route any content back to the original writer.  There shouldn't be any content, but just to be safe
            deactivateGrailsLayout();
        } else if (sc >= 400) {
            clearBuffer();
        }
        super.setStatus(sc);
    }

    protected void clearBuffer() {
        if (buffer != null) {
            buffer.clear();
        }
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return routableServletOutputStream;
    }

    @Override
    public PrintWriter getWriter() {
        return routablePrintWriter;
    }

    public Page getPage() throws IOException {
        if (isGrailsLayoutNotActive()) {
            return null;
        }

        GSPGrailsLayoutPage page = (GSPGrailsLayoutPage) request.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE);
        if (page != null && page.isUsed()) {
            return page;
        }

        return buffer.parse();
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        clearBuffer();
        super.sendRedirect(location);
    }

    public boolean isUsingStream() {
        return buffer != null && buffer.isUsingStream();
    }

    public char[] getContents() throws IOException {
        if (isGrailsLayoutNotActive()) {
            return null;
        }

        return buffer.getContents();
    }

    public boolean isGrailsLayoutActive() {
        return !isGrailsLayoutNotActive();
    }

    public boolean isGspGrailsLayoutActive() {
        return (gspGrailsLayoutPage != null && gspGrailsLayoutPage.isUsed());
    }

    private boolean isGrailsLayoutNotActive() {
        return !parseablePage;
    }

    private static class GrailsBuffer {
        private final PageParserSelector parserSelector;
        private final String contentType;
        private final String encoding;
        private final static TextEncoder TEXT_ENCODER = new TextEncoder();

        private StreamCharBuffer charBuffer;
        private GrailsPrintWriterAdapter exposedWriter;
        private StreamByteBuffer byteBuffer;
        private ServletOutputStream exposedStream;

        private GSPGrailsLayoutPage gspGrailsLayoutPage;

        public GrailsBuffer(PageParserSelector parserSelector, String contentType, String encoding, GSPGrailsLayoutPage gspGrailsLayoutPage) {
            this.parserSelector = parserSelector;
            this.contentType = contentType;
            this.encoding = encoding;
            this.gspGrailsLayoutPage = gspGrailsLayoutPage;
        }

        public void clear() {
            if (charBuffer != null) {
                charBuffer.clear();
            } else if (byteBuffer != null) {
                byteBuffer.clear();
            }
            if (gspGrailsLayoutPage != null) {
                gspGrailsLayoutPage.reset();
            }
        }

        private char[] getContents() throws IOException {
            if (charBuffer != null) {
                if (!charBuffer.isEmpty()) {
                    return charBuffer.toCharArray();
                } else {
                    return null;
                }
            }
            if (byteBuffer != null) {
                return TEXT_ENCODER.encode(byteBuffer.readAsByteArray(), encoding);
            }

            return new char[0];
        }

        public Page parse() throws IOException {
            PageParser pageParser = parserSelector.getPageParser(contentType);
            return pageParser != null ? pageParser.parse(getContents()) : null;
        }

        public PrintWriter getWriter() {
            if (charBuffer == null) {
                if (byteBuffer != null) {
                    throw new IllegalStateException("response.getWriter() called after response.getOutputStream()");
                }
                charBuffer = new StreamCharBuffer();
                charBuffer.setNotifyParentBuffersEnabled(false);
                if (gspGrailsLayoutPage != null) {
                    gspGrailsLayoutPage.setPageBuffer(charBuffer);
                }
                exposedWriter = GrailsPrintWriterAdapter.newInstance(charBuffer.getWriter());
            }
            return exposedWriter;
        }

        public ServletOutputStream getOutputStream() {
            if (byteBuffer == null) {
                if (charBuffer != null) {
                    throw new IllegalStateException("response.getOutputStream() called after response.getWriter()");
                }
                byteBuffer = new StreamByteBuffer();
                final OutputStream out = byteBuffer.getOutputStream();
                exposedStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        throw new UnsupportedOperationException("Method setWriteListener not supported");
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        out.write(b, off, len);
                    }

                    @Override
                    public void write(byte[] b) throws IOException {
                        out.write(b);
                    }

                    @Override
                    public void write(int b) throws IOException {
                        out.write(b);
                    }
                };
            }
            return exposedStream;
        }

        public boolean isUsingStream() {
            return byteBuffer != null;
        }
    }
}
