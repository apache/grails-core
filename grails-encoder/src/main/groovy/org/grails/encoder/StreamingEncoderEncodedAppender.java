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

import java.io.IOException;

/**
 * EncodedAppender implementation used for piping / chaining several StreamingEncoders
 *
 */
public class StreamingEncoderEncodedAppender extends AbstractEncodedAppender {
    private final StreamingEncoder encoder;
    private final EncodedAppender target;
    
    public StreamingEncoderEncodedAppender(StreamingEncoder encoder, EncodedAppender target) {
        this.encoder = encoder;
        this.target = target;
    }

    @Override
    public void close() throws IOException {
        target.close();
    }
    
    @Override
    public void flush() throws IOException {
        target.flush();
    }

    @Override
    protected void write(EncodingState encodingState, char[] b, int off, int len) throws IOException {
        if (shouldEncode(encoder, encodingState)) {
            encoder.encodeToStream(encoder, CharSequences.createCharSequence(b), off, len, target, createNewEncodingState(encoder, encodingState));
        } else {
            target.appendEncoded(null, encodingState, b, off, len);
        }
    }

    @Override
    protected void write(EncodingState encodingState, String str, int off, int len) throws IOException {
        if (shouldEncode(encoder, encodingState)) {
            encoder.encodeToStream(encoder, str, off, len, target, createNewEncodingState(encoder, encodingState));
        } else {
            target.appendEncoded(null, encodingState, str, off, len);
        }
    }

    @Override
    protected void appendCharSequence(EncodingState encodingState, CharSequence str, int start, int end)
            throws IOException {
        if (shouldEncode(encoder, encodingState.getPreviousEncodingState())) {
            encoder.encodeToStream(encoder, str, start, end-start, target, encodingState);
        } else {
            target.appendEncoded(null, encodingState, str, start, end-start);
        }
    }
}
