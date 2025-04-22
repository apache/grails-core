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

import org.grails.charsequences.CharSequences;

import java.io.IOException;

/**
 * Abstract base class for implementations of {@link EncodedAppender} interface
 *
 * @author Lari Hotari
 * @since 2.3
 */
public abstract class AbstractEncodedAppender implements EncodedAppender {
    private boolean ignoreEncodingState;
    
    /**
     * Append a portion of a char array to the buffer and attach the
     * encodingState information to it
     *
     * @param encodingState
     *            the new encoding state of the char array
     * @param b
     *            a char array
     * @param off
     *            Offset from which to start encoding characters
     * @param len
     *            Number of characters to encode
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    protected abstract void write(EncodingState encodingState, char[] b, int off, int len) throws IOException;

    /**
     * Append a portion of a string to the buffer and attach the encodingState
     * information to it
     *
     * @param encodingState
     *            the new encoding state of the string
     * @param str
     *            A String
     * @param off
     *            Offset from which to start encoding characters
     * @param len
     *            Number of characters to encode
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    protected abstract void write(EncodingState encodingState, String str, int off, int len) throws IOException;

    /**
     * Append a portion of a CharSequence to the buffer and attach the
     * encodingState information to it
     *
     * @param encodingState
     *            the new encoding state of the CharSequence portion
     * @param str
     *            a CharSequence
     * @param start
     *            the start index, inclusive
     * @param end
     *            the end index, exclusive
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    protected abstract void appendCharSequence(EncodingState encodingState, CharSequence str, int start, int end)
            throws IOException;

    /*
     * (non-Javadoc)
     * @see
     * EncodedAppender#append(org
     * .codehaus.groovy.grails.support.encoding.Encoder,
     * EncodingState, char[], int,
     * int)
     */
    public void append(Encoder encoder, EncodingState encodingState, char[] b, int off, int len) throws IOException {
        if (b == null || len <= 0) {
            return;
        }
        if (shouldEncode(encoder, encodingState)) {
            EncodingState newEncoders = createNewEncodingState(encoder, encodingState);
            if (encoder instanceof StreamingEncoder) {
                ((StreamingEncoder)encoder).encodeToStream(encoder, CharSequences.createCharSequence(b, off, len), 0, len, this,
                        newEncoders);
            }
            else {
                encodeAndWrite(encoder, newEncoders, String.valueOf(b, off, len));
            }
        }
        else {
            write(encodingState, b, off, len);
        }
    }

    protected EncodingState createNewEncodingState(Encoder encoder, EncodingState encodingState) {
        if (encodingState == null) {
            return new EncodingStateImpl(encoder, null);
        }
        return encodingState.appendEncoder(encoder);
    }

    /*
     * (non-Javadoc)
     * @see
     * EncodedAppender#append(org
     * .codehaus.groovy.grails.support.encoding.Encoder,
     * EncodingState,
     * java.lang.CharSequence, int, int)
     */
    public void append(Encoder encoder, EncodingState encodingState, CharSequence str, int off, int len)
            throws IOException {
        if (str == null || len <= 0) {
            return;
        }
        if (shouldEncode(encoder, encodingState)) {
            EncodingState newEncoders = createNewEncodingState(encoder, encodingState);
            if (encoder instanceof StreamingEncoder) {
                ((StreamingEncoder)encoder).encodeToStream(encoder, str, off, len, this, newEncoders);
            }
            else {
                CharSequence source;
                if (CharSequences.canUseOriginalForSubSequence(str, off, len)) {
                    source = str;
                }
                else {
                    source = str.subSequence(off, off + len);
                }
                encodeAndWrite(encoder, newEncoders, source);
            }
        }
        else {
            appendCharSequence(encodingState, str, off, off + len);
        }
    }

    public void appendEncoded(Encoder encoder, EncodingState encodingState, char[] b, int off, int len)
            throws IOException {
        write(createNewEncodingState(encoder, encodingState), b, off, len);
    }

    public void appendEncoded(Encoder encoder, EncodingState encodingState, CharSequence str, int off, int len)
            throws IOException {
        appendCharSequence(createNewEncodingState(encoder, encodingState), str, off, off + len);
    }

    /**
     * Check if the encoder should be used to a input with certain encodingState
     *
     * @param encoderToApply
     *            the encoder to apply
     * @param encodingState
     *            the current encoding state
     * @return true, if should encode
     */
    public boolean shouldEncode(Encoder encoderToApply, EncodingState encodingState) {
        return ignoreEncodingState || (encoderToApply != null
                && (encodingState == null || shouldEncodeWith(encoderToApply, encodingState)));
    }

    protected boolean shouldEncodeWith(Encoder encoderToApply, EncodingState encodingState) {
        return DefaultEncodingStateRegistry.shouldEncodeWith(encoderToApply,
                encodingState);
    }

    /**
     * Encode and write input to buffer using a non-streaming encoder
     *
     * @param encoder
     *            the encoder to use
     * @param newEncodingState
     *            the new encoding state after encoder has been applied
     * @param input
     *            the input CharSequence
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    protected void encodeAndWrite(Encoder encoder, EncodingState newEncodingState, CharSequence input)
            throws IOException {
        Object encoded = encoder.encode(input);
        if (encoded != null) {
            String encodedStr = String.valueOf(encoded);
            write(newEncodingState, encodedStr, 0, encodedStr.length());
        }
    }

    /*
     * (non-Javadoc)
     * @see
     * EncodedAppender#append(org
     * .codehaus.groovy.grails.support.encoding.Encoder,
     * StreamEncodeable)
     */
    public void append(Encoder encoder, StreamEncodeable streamEncodeable) throws IOException {
        streamEncodeable.encodeTo(this, encoder);
    }

    /*
     * (non-Javadoc)
     * @see EncodedAppender#flush()
     */
    public void flush() throws IOException {

    }

    public boolean isIgnoreEncodingState() {
        return ignoreEncodingState;
    }

    public void setIgnoreEncodingState(boolean ignoreEncodingState) {
        this.ignoreEncodingState = ignoreEncodingState;
    }
}
