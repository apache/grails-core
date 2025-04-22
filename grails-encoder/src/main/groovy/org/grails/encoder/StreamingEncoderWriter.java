/*
 * Copyright 2014 the original author or authors.
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

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

public class StreamingEncoderWriter extends FilterWriter implements EncodedAppenderFactory, EncoderAware {
    private EncodesToWriter encodesToWriter;
    private StreamingEncoder encoder;
    private EncodingStateRegistry encodingStateRegistry;
    
    public StreamingEncoderWriter(Writer out, StreamingEncoder encoder, EncodingStateRegistry encodingStateRegistry) {
        super(out);
        this.encoder = encoder;
        if(encoder instanceof EncodesToWriter) {
            this.encodesToWriter = ((EncodesToWriter)encoder);
        } else {
            this.encodesToWriter = new EncodesToWriterAdapter(encoder, true); 
        }
        this.encodingStateRegistry = encodingStateRegistry;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        encodesToWriter.encodeToWriter(cbuf, off, len, out, null);
    }
    
    @Override
    public void write(String str, int off, int len) throws IOException {
        final EncodingState encodingState = lookupEncodingState(str, off, len);
        if(shouldEncodeWith(encoder, encodingState)) {
            encodesToWriter.encodeToWriter(str, off, len, out, encodingState);
        } else {
            out.write(str, off, len);
        }
    }

    protected boolean shouldEncodeWith(Encoder encoderToApply, EncodingState encodingState) {
        return encodingState == null || DefaultEncodingStateRegistry.shouldEncodeWith(encoderToApply,
                encodingState);
    }
    
    protected EncodingState lookupEncodingState(String str, int off, int len) {
        if(encodingStateRegistry != null) {
            return encodingStateRegistry.getEncodingStateFor(str);
        } else {
            return null;
        }
    }

    @Override
    public void write(int c) throws IOException {
        encodesToWriter.encodeToWriter(CharSequences.createSingleCharSequence(c), 0, 1, out, null);
    }

    @Override
    public EncodedAppender getEncodedAppender() {
        EncodedAppender encodedAppender = new WriterEncodedAppender(out);
        encodedAppender.setIgnoreEncodingState(encodingStateRegistry == null);
        return encodedAppender;
    }

    @Override
    public Encoder getEncoder() {
        return encoder;
    }
}
