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

import org.grails.encoder.CodecIdentifier;
import org.grails.encoder.Decoder;
import org.springframework.web.util.HtmlUtils;

/**
 * HTML4 decoder that uses Spring's HtmlUtils.htmlUnescape to do the unescaping.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public class HTML4Decoder implements Decoder {

    /* (non-Javadoc)
     * @see Decoder#decode(java.lang.Object)
     */
    public Object decode(Object o) {
        if (o == null) {
            return null;
        }
        return HtmlUtils.htmlUnescape(String.valueOf(o));
    }

    /* (non-Javadoc)
     * @see CodecIdentifierProvider#getCodecIdentifier()
     */
    public CodecIdentifier getCodecIdentifier() {
        return HTML4Encoder.HTML4_CODEC_IDENTIFIER;
    }
}
