/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.plugins.web.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Response wrapper that runs a callback exactly once, immediately before the response
 * is committed. Every commit path is intercepted: redirects, errors, explicit buffer
 * flushes, flushing or closing the writer or output stream, writes that reach a
 * declared {@code Content-Length}, and writes that fill the container's response
 * buffer (which the container flushes, and thereby commits, on its own). If the
 * wrapped chain returns without committing, the owner is expected to invoke
 * {@link #beforeCommit()} itself. A {@link #reset()} discards the headers along with the
 * body, so it re-arms the callback for the response that replaces them.
 *
 * <p>Writing headers this late lets anything further down the filter chain (a
 * controller, an interceptor, Spring Security's header writers, another filter) set
 * its own value first; the callback only fills what is still missing.</p>
 *
 * <p>Body size is counted in bytes, the unit of both the container's buffer and
 * {@code Content-Length}. Output stream writes count as written. Writer output counts as
 * its encoded length in the response's character encoding: exactly for UTF-8 and
 * single-byte encodings, and at the encoding's maximum bytes per character for any
 * other, so the count never falls behind the bytes the container holds. Spring Security's
 * {@code OnCommittedResponseWrapper} counts writer output in characters instead, so for a
 * multi-byte body large enough to fill the buffer this callback can run before Spring
 * Security's, and a header Spring Security only writes when it is absent then keeps the
 * value written here.</p>
 */
final class SecurityHeadersResponseWrapper extends HttpServletResponseWrapper {

    private static final String CONTENT_LENGTH = "Content-Length";

    private final Runnable beforeCommit;

    private boolean fired;

    private long contentLength = -1;

    private long contentWritten;

    /**
     * The container's buffer size, read once content is being written. The servlet API
     * forbids changing it after that point, so the value is cached and only refreshed by
     * {@link #setBufferSize(int)}, {@link #reset()} or {@link #resetBuffer()}.
     */
    private int bufferSize = -1;

    private ServletOutputStream outputStream;

    private PrintWriter writer;

    SecurityHeadersResponseWrapper(HttpServletResponse response, Runnable beforeCommit) {
        super(response);
        this.beforeCommit = beforeCommit;
    }

    /**
     * Runs the callback if it has not run yet. Safe to call repeatedly.
     */
    void beforeCommit() {
        if (!this.fired) {
            this.fired = true;
            this.beforeCommit.run();
        }
    }

    @Override
    public void sendError(int sc) throws IOException {
        beforeCommit();
        super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        beforeCommit();
        super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        beforeCommit();
        super.sendRedirect(location);
    }

    @Override
    public void sendRedirect(String location, int sc) throws IOException {
        beforeCommit();
        super.sendRedirect(location, sc);
    }

    @Override
    public void sendRedirect(String location, boolean clearBuffer) throws IOException {
        beforeCommit();
        super.sendRedirect(location, clearBuffer);
    }

    @Override
    public void sendRedirect(String location, int sc, boolean clearBuffer) throws IOException {
        beforeCommit();
        super.sendRedirect(location, sc, clearBuffer);
    }

    @Override
    public void flushBuffer() throws IOException {
        beforeCommit();
        super.flushBuffer();
    }

    /**
     * Clears the status, headers and body. The headers the callback wrote are gone with
     * it, so the callback is armed again for whatever is written next. The character
     * encoding can change after a reset, so the next {@link #getWriter()} counts in the
     * new one.
     */
    @Override
    public void reset() {
        super.reset();
        this.fired = false;
        this.contentLength = -1;
        this.contentWritten = 0;
        this.bufferSize = -1;
        this.writer = null;
    }

    @Override
    public void resetBuffer() {
        super.resetBuffer();
        this.contentWritten = 0;
        this.bufferSize = -1;
    }

    @Override
    public void setBufferSize(int size) {
        super.setBufferSize(size);
        this.bufferSize = -1;
    }

    @Override
    public void setContentLength(int len) {
        trackContentLength(len);
        super.setContentLength(len);
    }

    @Override
    public void setContentLengthLong(long len) {
        trackContentLength(len);
        super.setContentLengthLong(len);
    }

    @Override
    public void setHeader(String name, String value) {
        trackContentLengthHeader(name, value);
        super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        trackContentLengthHeader(name, value);
        super.addHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        trackContentLengthHeader(name, value);
        super.setIntHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        trackContentLengthHeader(name, value);
        super.addIntHeader(name, value);
    }

    /**
     * Returns the same stream on every call, as the container does.
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (this.outputStream == null) {
            this.outputStream = new CommitAwareOutputStream(super.getOutputStream());
        }
        return this.outputStream;
    }

    /**
     * Returns the same writer on every call, as the container does, until a {@link #reset()}
     * discards it.
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        if (this.writer == null) {
            this.writer = new CommitAwareWriter(super.getWriter(), getCharacterEncoding());
        }
        return this.writer;
    }

    private void trackContentLengthHeader(String name, String value) {
        if (CONTENT_LENGTH.equalsIgnoreCase(name) && value != null) {
            try {
                trackContentLength(Long.parseLong(value.trim()));
            }
            catch (NumberFormatException ignored) {
                // The container rejects or ignores a malformed Content-Length; nothing to track.
            }
        }
    }

    private void trackContentLengthHeader(String name, int value) {
        if (CONTENT_LENGTH.equalsIgnoreCase(name)) {
            trackContentLength(value);
        }
    }

    /**
     * Records the declared body length. Content may already have been written, so the
     * commit check runs immediately as well as on subsequent writes.
     */
    private void trackContentLength(long length) {
        this.contentLength = length;
        trackWritten(0);
    }

    private void trackWritten(long count) {
        if (this.fired) {
            return;
        }
        this.contentWritten += count;
        boolean bodyComplete = this.contentLength >= 0 && this.contentWritten >= this.contentLength;
        int size = bufferSize();
        boolean bufferFull = size > 0 && this.contentWritten >= size;
        if (bodyComplete || bufferFull) {
            beforeCommit();
        }
    }

    private int bufferSize() {
        if (this.bufferSize < 0) {
            this.bufferSize = getBufferSize();
        }
        return this.bufferSize;
    }

    private final class CommitAwareOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        CommitAwareOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            trackWritten(1);
            this.delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            trackWritten(b.length);
            this.delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            trackWritten(len);
            this.delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            beforeCommit();
            this.delegate.flush();
        }

        @Override
        public void close() throws IOException {
            beforeCommit();
            this.delegate.close();
        }

        @Override
        public boolean isReady() {
            return this.delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            this.delegate.setWriteListener(listener);
        }
    }

    private final class CommitAwareWriter extends PrintWriter {

        private final boolean utf8;

        private final float maxBytesPerChar;

        CommitAwareWriter(PrintWriter delegate, String characterEncoding) {
            super(delegate, false);
            Charset charset = encodingCharset(characterEncoding);
            this.utf8 = StandardCharsets.UTF_8.equals(charset);
            this.maxBytesPerChar = this.utf8 ? 0 : charset.newEncoder().maxBytesPerChar();
        }

        @Override
        public void write(int c) {
            if (!fired) {
                trackWritten(this.utf8 ? utf8Length((char) c) : (long) Math.ceil(this.maxBytesPerChar));
            }
            super.write(c);
        }

        @Override
        public void write(char[] buf, int off, int len) {
            trackChars(CharBuffer.wrap(buf), off, len);
            super.write(buf, off, len);
        }

        @Override
        public void write(String s, int off, int len) {
            trackChars(s, off, len);
            super.write(s, off, len);
        }

        /**
         * {@link PrintWriter#println()} writes the line separator straight to the
         * underlying writer rather than through {@code write}, so it has to be counted
         * here. Every other {@code println} overload ends by calling this one.
         */
        @Override
        public void println() {
            String separator = System.lineSeparator();
            trackChars(separator, 0, separator.length());
            super.println();
        }

        @Override
        public void flush() {
            beforeCommit();
            super.flush();
        }

        @Override
        public void close() {
            beforeCommit();
            super.close();
        }

        private void trackChars(CharSequence chars, int off, int len) {
            if (fired) {
                return;
            }
            if (!this.utf8) {
                trackWritten((long) Math.ceil(len * (double) this.maxBytesPerChar));
                return;
            }
            long bytes = 0;
            for (int i = off; i < off + len; i++) {
                bytes += utf8Length(chars.charAt(i));
            }
            trackWritten(bytes);
        }
    }

    private static int utf8Length(char c) {
        if (c < 0x80) {
            return 1;
        }
        // Each half of a surrogate pair counts two bytes: four for the pair.
        if (c < 0x800 || Character.isSurrogate(c)) {
            return 2;
        }
        return 3;
    }

    /**
     * The charset the container encodes writer output with. An encoding the JVM cannot
     * encode to is counted as UTF-8; the container fails such a writer on its own.
     */
    private static Charset encodingCharset(String characterEncoding) {
        if (characterEncoding != null) {
            try {
                Charset charset = Charset.forName(characterEncoding);
                if (charset.canEncode()) {
                    return charset;
                }
            }
            catch (IllegalArgumentException unsupported) {
                // Fall through to UTF-8.
            }
        }
        return StandardCharsets.UTF_8;
    }
}
