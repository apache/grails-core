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

import org.grails.encoder.AbstractCharReplacementEncoder;
import org.grails.encoder.CodecIdentifier;
import org.grails.encoder.DefaultCodecIdentifier;

/**
 * Used for making strings safe to be included in a SCRIPT tag besides normal
 * Javascript escaping, possibly "unsafe" characters are escaped too so that
 * it's safe to include an escaped string in a HTML SCRIPT tag.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public class JavaScriptEncoder extends AbstractCharReplacementEncoder {
    public static final CodecIdentifier JAVASCRIPT_CODEC_IDENTIFIER = new DefaultCodecIdentifier(
            "JavaScript", "Js") {
        public boolean isEquivalent(CodecIdentifier other) {
            return super.isEquivalent(other) || BasicJSONEncoder.JSON_CODEC_IDENTIFIER.getCodecName().equals(other.getCodecName());
        };
    };

    public JavaScriptEncoder() {
        super(JAVASCRIPT_CODEC_IDENTIFIER);
    }

    /* (non-Javadoc)
     * @see AbstractCharReplacementEncoder#escapeCharacter(char, char)
     */
    @Override
    protected String escapeCharacter(char ch, char previousChar) {
        switch (ch) {
            case '"':
                return "\\u0022";
            case '\'':
                return "\\u0027";
            case '`': // backtick
                return "\\u0060";                
            case '\\':
                return "\\u005c";
            case '/':
                return "\\u002f";
            case '\t':
                return "\\t";
            case '\n':
                if (previousChar == '\r') {
                    return "";
                } else {
                    return "\\n";
                }
            case '\r':
                return "\\n";
            case '\f':
                return "\\f";
            case '\b':
                return "\\b";
            case '\u000B': // vertical tab: http://bclary.com/2004/11/07/#a-7.8.4
                return "\\v";
            case '&':
                return "\\u0026";
            case '<':
                return "\\u003c";
            case '>':
                return "\\u003e";
            case '(':
                return "\\u0028";
            case ')':
                return "\\u0029";
            case '[':
                return "\\u005b";
            case ']':
                return "\\u005d";
            case '{':
                return "\\u007b";
            case '}':
                return "\\u007d";
            case ',':
                return "\\u002c";
            case ';':
                return "\\u003b";
            case '@':
                return "\\u0040";
            case '\u2028':
                return "\\u2028"; // Line separator
            case '\u2029':
                return "\\u2029"; // Paragraph separator
        }
        if(ch < ' ') {
            // remove all other control characters
            return "";
        }
        return null;
    }

    @Override
    public boolean isApplyToSafelyEncoded() {
        return true;
    }
}
