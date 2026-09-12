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
package org.grails.plugins.web.rest.render;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * Decodes bytes into a {@link Writer} as they are written.
 *
 * <p>Response renderers hand Spring's {@code HttpMessageConverter} an {@link OutputStream} but must
 * ultimately write characters to the render context's writer. Buffering the whole encoded body just
 * to decode it in one go holds the entire response in memory twice; this streams it instead.
 * Multi-byte sequences split across writes are carried over rather than corrupted.</p>
 *
 * <p>Not thread safe, and intended to wrap a single response body. {@link #close()} flushes the
 * decoder but deliberately leaves the underlying writer open, since the container owns it.</p>
 *
 * @since 8.0
 */
public final class WriterOutputStream extends OutputStream {

    private static final int BUFFER_SIZE = 8192;

    private final Writer writer;
    private final CharsetDecoder decoder;
    private final ByteBuffer input = ByteBuffer.allocate(BUFFER_SIZE);
    private final CharBuffer output = CharBuffer.allocate(BUFFER_SIZE);
    private boolean closed;

    public WriterOutputStream(Writer writer, Charset charset) {
        this.writer = writer;
        this.decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        int position = offset;
        int remaining = length;
        while (remaining > 0) {
            int chunk = Math.min(remaining, input.remaining());
            input.put(bytes, position, chunk);
            position += chunk;
            remaining -= chunk;
            decode(false);
        }
    }

    @Override
    public void flush() throws IOException {
        decode(false);
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        decode(true);
        CoderResult result;
        do {
            result = decoder.flush(output);
            drain();
        }
        while (result.isOverflow());
        writer.flush();
    }

    private void decode(boolean endOfInput) throws IOException {
        input.flip();
        CoderResult result;
        do {
            result = decoder.decode(input, output, endOfInput);
            drain();
        }
        while (result.isOverflow());
        // Retain any trailing partial multi-byte sequence for the next write.
        input.compact();
    }

    private void drain() throws IOException {
        output.flip();
        if (output.hasRemaining()) {
            writer.write(output.array(), output.arrayOffset() + output.position(), output.remaining());
        }
        output.clear();
    }

    /**
     * Runs the given body against a stream that decodes into the writer, closing it so the decoder
     * is flushed. {@link IOException} is rethrown unchecked so callers in rendering code, which
     * cannot declare it, still fail loudly rather than truncating a response.
     */
    public static void writeThrough(Writer writer, Charset charset, ThrowingConsumer body) {
        try (WriterOutputStream stream = new WriterOutputStream(writer, charset)) {
            body.accept(stream);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A body that writes to the stream and may fail with an {@link IOException}. */
    @FunctionalInterface
    public interface ThrowingConsumer {

        void accept(OutputStream stream) throws IOException;
    }
}
