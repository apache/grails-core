/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.encoder;

import java.io.IOException;

/**
 * This is the input interface to the streaming encoding solution. Methods in
 * this interface encode the given input and append it to the internal buffer
 *
 * @author Lari Hotari
 * @since 2.3
 */
public interface EncodedAppender {
    /**
     * Encodes a portion of a string and appends it to the buffer.
     *
     * @param encoder
     *            the encoder to use
     * @param encodingState
     *            the current encoding state of the string
     * @param str
     *            A String
     * @param off
     *            Offset from which to start encoding characters
     * @param len
     *            Number of characters to encode
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    void append(Encoder encoder, EncodingState encodingState, CharSequence str, int off, int len) throws IOException;

    /**
     * Appends an encoded portion of a string to the buffer
     *
     * @param encoder
     *            the encoder that has been applied
     * @param encodingState
     *            the previous encoding state of the string
     * @param str
     *            A String
     * @param off
     *            Offset from which to start encoding characters
     * @param len
     *            Number of characters to encode
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    void appendEncoded(Encoder encoder, EncodingState encodingState, CharSequence str, int off, int len)
            throws IOException;

    /**
     * Encodes a portion of a char array and appends it to the buffer.
     *
     * @param encoder
     *            the encoder to use
     * @param encodingState
     *            the current encoding state of the string
     * @param b
     *            a char array
     * @param off
     *            Offset from which to start encoding characters
     * @param len
     *            Number of characters to encode
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    void append(Encoder encoder, EncodingState encodingState, char[] b, int off, int len) throws IOException;

    /**
     * Appends an encoded portion of a char array to the buffer.
     *
     * @param encoder
     *            the encoder that has been applied
     * @param encodingState
     *            the previous encoding state of the char array
     * @param b
     *            a char array
     * @param off
     *            Offset from which to start encoding characters
     * @param len
     *            Number of characters to encode
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    void appendEncoded(Encoder encoder, EncodingState encodingState, char[] b, int off, int len) throws IOException;

    /**
     * Encodes a {@link StreamEncodeable} instance and appends it to the buffer.
     *
     * @param encoder the encoder to use
     * @param streamEncodeable the instance to encode
     * @throws IOException Signals that an I/O exception has occurred.
     */
    void append(Encoder encoder, StreamEncodeable streamEncodeable) throws IOException;

    /**
     * Flush the internal buffer and write the buffered input to a possible
     * destination.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    void flush() throws IOException;

    public void close() throws IOException;
    
    
    /**
     * When enabled, will encode all input regardless of it's current state
     * disables double-encoding prevention.
     *  
     * @param ignoreEncodingState
     */
    void setIgnoreEncodingState(boolean ignoreEncodingState);
    
    /**
     * @return current state of ignoreEncodingState setting
     */
    public boolean isIgnoreEncodingState();
    
    /**
     * Check if the encoder should be used to a input with certain encodingState
     *
     * @param encoderToApply
     *            the encoder to apply
     * @param encodingState
     *            the current encoding state
     * @return true, if should encode
     */
    public boolean shouldEncode(Encoder encoderToApply, EncodingState encodingState);
}
