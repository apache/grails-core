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

import org.grails.charsequences.CharSequences

@CompileStatic
class EncodesToWriterAdapter implements EncodesToWriter {

    private final StreamingEncoder encoder
    private boolean ignoreEncodingState

    EncodesToWriterAdapter(StreamingEncoder encoder) {
        this(encoder, false)
    }

    EncodesToWriterAdapter(StreamingEncoder encoder, boolean ignoreEncodingState) {
        this.encoder = encoder
        this.ignoreEncodingState = ignoreEncodingState
    }

    @Override
    void encodeToWriter(CharSequence str, int off, int len, Writer writer, EncodingState encodingState) throws IOException {
        if (shouldEncodeWith(encoder, encodingState)) {
            encoder.encodeToStream(encoder, str, off, len, new WriterEncodedAppender(writer), createNewEncodingState(encoder, encodingState))
        } else {
            CharSequences.writeCharSequence(writer, str, off, len)
        }
    }

    @Override
    void encodeToWriter(char[] buf, int off, int len, Writer writer, EncodingState encodingState) throws IOException {
        if (shouldEncodeWith(encoder, encodingState)) {
            encoder.encodeToStream(encoder, CharSequences.createCharSequence(buf, off, len), 0, len, new WriterEncodedAppender(writer), createNewEncodingState(encoder, encodingState))
        } else {
            writer.write(buf, off, len)
        }
    }

    protected EncodingState createNewEncodingState(Encoder encoder, EncodingState encodingState) {
        if (encodingState == null) {
            return new EncodingStateImpl(encoder, null)
        }
        return encodingState.appendEncoder(encoder)
    }

    protected boolean shouldEncodeWith(Encoder encoderToApply, EncodingState encodingState) {
        return ignoreEncodingState || encodingState == null || DefaultEncodingStateRegistry.shouldEncodeWith(encoderToApply,
                encodingState)
    }

    StreamingEncoder getStreamingEncoder() {
        return encoder
    }

    boolean isIgnoreEncodingState() {
        return ignoreEncodingState
    }

    void setIgnoreEncodingState(boolean ignoreEncodingState) {
        this.ignoreEncodingState = ignoreEncodingState
    }

    @Override
    EncodesToWriter createChainingEncodesToWriter(List<StreamingEncoder> additionalEncoders, boolean applyAdditionalFirst) {
        EncodesToWriterAdapter chained = createChainingEncodesToWriter(getStreamingEncoder(), additionalEncoders, applyAdditionalFirst)
        chained.setIgnoreEncodingState(isIgnoreEncodingState())
        return chained
    }

    static EncodesToWriterAdapter createChainingEncodesToWriter(StreamingEncoder baseEncoder, List<StreamingEncoder> additionalEncoders, boolean applyAdditionalFirst) {
        boolean baseEncoderShouldBeApplied = ChainedEncoders.shouldApplyEncoder(baseEncoder)
        List<StreamingEncoder> allEncoders = new ArrayList<>(additionalEncoders.size() + 1)
        if (!applyAdditionalFirst && baseEncoderShouldBeApplied) {
            allEncoders.add(baseEncoder)
        }
        for (StreamingEncoder additional : additionalEncoders) {
            if (ChainedEncoders.shouldApplyEncoder(additional)) {
                allEncoders.add(additional)
            }
        }
        if (applyAdditionalFirst && baseEncoderShouldBeApplied) {
            allEncoders.add(baseEncoder)
        }
        return new EncodesToWriterAdapter(ChainedEncoder.createFor(allEncoders))
    }
}
