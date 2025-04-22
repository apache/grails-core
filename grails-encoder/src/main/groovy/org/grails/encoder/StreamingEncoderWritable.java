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
import java.io.Writer;

/**
 * Marks a class capable of encoding itself with given EncodesToWriter instance to given
 * Writer instance
 *
 * @author Lari Hotari
 * @since 2.3.10
 */
public interface StreamingEncoderWritable {

    /**
     * Asks the instance to use given writer and EncodesToWriter instance to encode 
     * it's content
     *
     * @param writer
     *            the target writer instance
     * @param encoder
     *            the encoder
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException;
}
