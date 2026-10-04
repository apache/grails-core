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
package org.grails.encoder

import groovy.transform.CompileStatic

/**
 * A java.io.Writer implementation that writes to a {@link EncodedAppender} with
 * a certain encoder
 *
 * This class isn't thread-safe.
 *
 * @author Lari Hotari
 * @since 2.3
 */
@CompileStatic
class EncodedAppenderWriter extends Writer implements EncodedAppenderWriterFactory, EncodedAppenderFactory, EncoderAware {

    protected EncodedAppender encodedAppender
    protected Encoder encoder
    protected EncodingStateRegistry encodingStateRegistry
    private char[] singleCharBuffer = new char[1]

    /**
     * Default constructor
     *
     * @param encodedAppender
     *            the EncodedAppender destination
     * @param encoder
     *            the encoder to use
     * @param encodingStateRegistry
     *            the {@link EncodingStateRegistry} to use to lookup encoding
     *            state of CharSequence instances
     */
    EncodedAppenderWriter(EncodedAppender encodedAppender, Encoder encoder,
            EncodingStateRegistry encodingStateRegistry) {
        this.encodedAppender = encodedAppender
        this.encoder = encoder
        this.encodingStateRegistry = encodingStateRegistry
    }

    /*
     * (non-Javadoc)
     * @see java.io.Writer#write(char[], int, int)
     */
    @Override
    void write(char[] cbuf, int off, int len) throws IOException {
        encodedAppender.append(encoder, null, cbuf, off, len)
    }

    /*
     * (non-Javadoc)
     * @see java.io.Writer#flush()
     */
    @Override
    void flush() throws IOException {
        encodedAppender.flush()
    }

    /*
     * (non-Javadoc)
     * @see java.io.Writer#close()
     */
    @Override
    void close() throws IOException {
        encodedAppender.close()
    }

    /*
     * (non-Javadoc)
     * @see java.io.Writer#write(int)
     */
    @Override
    void write(int c) throws IOException {
        append((char) c)
    }

    /*
     * (non-Javadoc)
     * @see java.io.Writer#write(java.lang.String, int, int)
     */
    @Override
    void write(String str, int off, int len) throws IOException {
        encodedAppender.append(
                encoder,
                (encodingStateRegistry != null && off == 0 && len == str.length()) ? encodingStateRegistry
                        .getEncodingStateFor(str) : null, str, off, len)
    }

    /*
     * (non-Javadoc)
     * @see java.io.Writer#append(java.lang.CharSequence)
     */
    @Override
    Writer append(CharSequence csq) throws IOException {
        encodedAppender.append(encoder,
                (encodingStateRegistry != null) ? encodingStateRegistry.getEncodingStateFor(csq) : null, csq, 0,
                csq.length())
        return this
    }

    /*
     * (non-Javadoc)
     * @see java.io.Writer#append(java.lang.CharSequence, int, int)
     */
    @Override
    Writer append(CharSequence csq, int start, int end) throws IOException {
        encodedAppender.append(encoder, null, csq, 0, end - start)
        return this
    }

    /*
     * (non-Javadoc)
     * @see java.io.Writer#append(char)
     */
    @Override
    Writer append(char c) throws IOException {
        singleCharBuffer[0] = (char) c
        encodedAppender.append(encoder, null, singleCharBuffer, 0, 1)
        return this
    }

    /*
     * (non-Javadoc)
     * @see EncodedAppenderFactory#
     * getEncodedAppender()
     */
    EncodedAppender getEncodedAppender() {
        return encodedAppender
    }

    /*
     * (non-Javadoc)
     * @see
     * EncoderAware#getEncoder()
     */
    Encoder getEncoder() {
        return encoder
    }

    /*
     * (non-Javadoc)
     * @see
     * EncodedAppenderWriterFactory
     * #getWriterForEncoder(Encoder,
     * EncodingStateRegistry)
     */
    Writer getWriterForEncoder(Encoder encoder, EncodingStateRegistry encodingStateRegistry) {
        return new EncodedAppenderWriter(encodedAppender, encoder, encodingStateRegistry)
    }
}
