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
package org.grails.encoder.impl;

import java.io.IOException;

import org.grails.encoder.CodecIdentifier;
import org.grails.encoder.DefaultCodecIdentifier;
import org.grails.encoder.EncodedAppender;
import org.grails.encoder.Encoder;
import org.grails.encoder.EncodingState;
import org.grails.encoder.StreamingEncoder;

/**
 * @author Lari Hotari
 * @since 2.3
 */
public class NoneEncoder implements StreamingEncoder {
    static final CodecIdentifier CODEC_IDENTIFIER = new DefaultCodecIdentifier("None");

    public Object encode(Object o) {
        return o;
    }

    public boolean isSafe() {
        return false;
    }

    public boolean isApplyToSafelyEncoded() {
        return false;
    }

    public void markEncoded(CharSequence string) {

    }

    public CodecIdentifier getCodecIdentifier() {
        return CODEC_IDENTIFIER;
    }

    public void encodeToStream(Encoder thisInstance, CharSequence source, int offset, int len, EncodedAppender appender,
            EncodingState encodingState) throws IOException {
        appender.append(null, encodingState, source, offset, len);
    }
}
