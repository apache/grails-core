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
 * Streaming encoder interface that makes it possible to encode a portion of a
 * CharSequence and append it directly to the EncodedAppender instance. This
 * solution makes it possible to just check if the input is ok and only replace
 * the characters in the input that have to be escaped.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public interface StreamingEncoder extends Encoder {
    /**
     * Encode and append portion of source CharSequence to the appender.
     *
     * @param source
     *            The source CharSequence
     * @param offset
     *            Offset from which to start encoding characters
     * @param len
     *            Number of characters to encode
     * @param appender
     *            the appender to write to
     * @param encodingState
     *            the current encoding state
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    void encodeToStream(Encoder thisInstance, CharSequence source, int offset, int len, EncodedAppender appender,
            EncodingState encodingState) throws IOException;
}
