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

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Marks a class capable of encoding to target Writer
 * 
 * @author Lari Hotari
 * @since 2.3.10
 *
 */
public interface EncodesToWriter {
    public void encodeToWriter(CharSequence str, int off, int len, Writer writer, EncodingState encodingState) throws IOException;
    public void encodeToWriter(char[] buf, int off, int len, Writer writer, EncodingState encodingState) throws IOException;
    public EncodesToWriter createChainingEncodesToWriter(List<StreamingEncoder> additionalEncoders, boolean applyAdditionalFirst);
}
