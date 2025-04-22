/* Copyright 2013 the original author or authors.
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
package org.grails.encoder.impl;

import java.io.IOException;

import org.grails.encoder.CodecFactory;
import org.grails.encoder.CodecIdentifier;
import org.grails.encoder.Decoder;
import org.grails.encoder.DefaultCodecIdentifier;
import org.grails.encoder.EncodedAppender;
import org.grails.encoder.Encoder;
import org.grails.encoder.EncodingState;
import org.grails.encoder.StreamingEncoder;

/**
 * Codec that doesn't do any encoding or decoding. This is for marking some text
 * as "safe" in the buffer. "safe" part of the buffer won't be encoded later if
 * a codec is applied to the buffer
 *
 * @author Lari Hotari
 * @since 2.3
 */
public class RawCodec implements Encoder, Decoder, StreamingEncoder, CodecFactory {
    static final CodecIdentifier RAW_CODEC_IDENTIFIER = new DefaultCodecIdentifier("Raw") {
        // using RawCodec will prevent all other codecs from encoding a part encoded with this codec
        public boolean isEquivalent(CodecIdentifier other) { return true; };
    };

    /* (non-Javadoc)
     * @see Decoder#decode(java.lang.Object)
     */
    public Object decode(Object o) {
        return o;
    }

    /* (non-Javadoc)
     * @see Encoder#isSafe()
     */
    public boolean isSafe() {
        return true;
    }

    /* (non-Javadoc)
     * @see Encoder#encode(java.lang.Object)
     */
    public Object encode(Object o) {
        if(o instanceof String) {
            // create a new copy of the String instance            
            return new String((String)o);
        } else if(o instanceof CharSequence) {
            // convert CharSequence to String so that we have a new instance
            return String.valueOf(o);
        } else {
            return o;
        }
    }

    /* (non-Javadoc)
     * @see Encoder#markEncoded(java.lang.CharSequence)
     */
    public void markEncoded(CharSequence string) {

    }

    /* (non-Javadoc)
     * @see StreamingEncoder#encodeToStream(Encoder, java.lang.CharSequence, int, int, EncodedAppender, EncodingState)
     */
    public void encodeToStream(Encoder thisInstance, CharSequence source, int offset, int len, EncodedAppender appender,
            EncodingState encodingState) throws IOException {
        appender.appendEncoded(thisInstance, encodingState, source, offset, len);
    }

    /* (non-Javadoc)
     * @see CodecIdentifierProvider#getCodecIdentifier()
     */
    public CodecIdentifier getCodecIdentifier() {
        return RAW_CODEC_IDENTIFIER;
    }

    /* (non-Javadoc)
     * @see Encoder#isApplyToSafelyEncoded()
     */
    public boolean isApplyToSafelyEncoded() {
        return false;
    }

    @Override
    public Encoder getEncoder() {
        return this;
    }

    @Override
    public Decoder getDecoder() {
        return this;
    }
}
