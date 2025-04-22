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
import java.util.List;

import org.grails.charsequences.CharSequences;
import org.grails.encoder.CodecIdentifier;
import org.grails.encoder.EncodedAppender;
import org.grails.encoder.Encoder;
import org.grails.encoder.EncodesToWriter;
import org.grails.encoder.EncodesToWriterAdapter;
import org.grails.encoder.EncodingState;
import org.grails.encoder.StreamingEncoder;

/**
 * Abstract base class for implementing encoders that do character replacements
 * Implements the {@link StreamingEncoder} interface that enables efficient
 * streaming encoding
 *
 * @author Lari Hotari
 * @since 2.3
 */
public abstract class AbstractCharReplacementEncoder implements Encoder, StreamingEncoder, EncodesToWriter {
    protected CodecIdentifier codecIdentifier;

    public AbstractCharReplacementEncoder(CodecIdentifier codecIdentifier) {
        this.codecIdentifier = codecIdentifier;
    }

    /**
     * Escape the character, return null if no replacement has to be made
     *
     * @param ch the character to escape
     * @param previousChar  the previous char
     * @return the replacement string, null if no replacement has to be made
     */
    protected abstract String escapeCharacter(char ch, char previousChar);

    /* (non-Javadoc)
     * @see Encoder#encode(java.lang.Object)
     */
    public Object encode(Object o) {
        return doCharReplacementEncoding(o);
    }

    protected final Object doCharReplacementEncoding(Object o) {
        if (o == null) {
            return null;
        }

        CharSequence str = null;
        if (o instanceof CharSequence) {
            str = (CharSequence)o;
        }
        else if (o instanceof Character) {
            String escaped = escapeCharacter((Character)o, (char)0);
            if (escaped != null) {
                return escaped;
            }
            else {
                return o;
            }
        }
        else {
            str = convertToString(o);
        }

        return escapeCharSequence(str);
    }

    protected String convertToString(Object o) {
        return String.valueOf(o);
    }

    protected Object escapeCharSequence(CharSequence str) {
        if (str == null || str.length() == 0) {
            return str;
        }

        StringBuilder sb = null;
        int n = str.length(), i;
        int startPos = -1;
        char prevChar = (char)0;
        for (i = 0; i < n; i++) {
            char ch = str.charAt(i);
            if (startPos == -1) {
                startPos = i;
            }
            String escaped = escapeCharacter(ch, prevChar);
            if (escaped != null) {
                if (sb == null) {
                    sb = new StringBuilder(str.length() * 110 / 100);
                }
                if (i - startPos > 0) {
                    sb.append(str, startPos, i);
                }
                if (escaped.length() > 0) {
                    sb.append(escaped);
                }
                startPos = -1;
            }
            prevChar = ch;
        }
        if (sb != null) {
            if (startPos > -1 && i - startPos > 0) {
                sb.append(str, startPos, i);
            }
            return sb.toString();
        }
        else {
            return str;
        }
    }
    
    @Override
    public void encodeToWriter(CharSequence str, int off, int len, Writer writer, EncodingState encodingState) throws IOException {
        if (str == null || len <= 0) {
            return;
        }
        int n = Math.min(str.length(), off + len);
        int i;
        int startPos = -1;
        char prevChar = (char)0;
        for (i = off; i < n; i++) {
            char ch = str.charAt(i);
            if (startPos == -1) {
                startPos = i;
            }
            String escaped = escapeCharacter(ch, prevChar);
            if (escaped != null) {
                if (i - startPos > 0) {
                    CharSequences.writeCharSequence(writer, str, startPos, i);
                }
                if (escaped.length() > 0) {
                    writer.write(escaped);
                }
                startPos = -1;
            }
            prevChar = ch;
        }
        if (startPos > -1 && i - startPos > 0) {
            CharSequences.writeCharSequence(writer, str, startPos, i);
        }
    }
    
    @Override
    public void encodeToWriter(char[] buf, int off, int len, Writer writer, EncodingState encodingState) throws IOException {
        if (buf == null || len <= 0) {
            return;
        }
        int n = Math.min(buf.length, off + len);
        int i;
        int startPos = -1;
        char prevChar = (char)0;
        for (i = off; i < n; i++) {
            char ch = buf[i];
            if (startPos == -1) {
                startPos = i;
            }
            String escaped = escapeCharacter(ch, prevChar);
            if (escaped != null) {
                if (i - startPos > 0) {
                    writer.write(buf, startPos, i - startPos);
                }
                if (escaped.length() > 0) {
                    writer.write(escaped);
                }
                startPos = -1;
            }
            prevChar = ch;
        }
        if (startPos > -1 && i - startPos > 0) {
            writer.write(buf, startPos, i - startPos);
        }
    }
    
    @Override
    public EncodesToWriter createChainingEncodesToWriter(List<StreamingEncoder> encoders, boolean applyAdditionalFirst) {
        return EncodesToWriterAdapter.createChainingEncodesToWriter(this, encoders, applyAdditionalFirst);
    }

    /* (non-Javadoc)
     * @see StreamingEncoder#encodeToStream(Encoder, java.lang.CharSequence, int, int, EncodedAppender, EncodingState)
     */
    public void encodeToStream(Encoder thisInstance, CharSequence str, int off, int len, EncodedAppender appender, EncodingState encodingState)
            throws IOException {
        if (str == null || len <= 0) {
            return;
        }
        int n = Math.min(str.length(), off + len);
        int i;
        int startPos = -1;
        char prevChar = (char)0;
        for (i = off; i < n; i++) {
            char ch = str.charAt(i);
            if (startPos == -1) {
                startPos = i;
            }
            String escaped = escapeCharacter(ch, prevChar);
            if (escaped != null) {
                if (i - startPos > 0) {
                    appender.appendEncoded(thisInstance, encodingState, str, startPos, i - startPos);
                }
                if (escaped.length() > 0) {
                    appender.appendEncoded(thisInstance, encodingState, escaped, 0, escaped.length());
                }
                startPos = -1;
            }
            prevChar = ch;
        }
        if (startPos > -1 && i - startPos > 0) {
            appender.appendEncoded(thisInstance, encodingState, str, startPos, i - startPos);
        }
    }

    /* (non-Javadoc)
     * @see Encoder#markEncoded(java.lang.CharSequence)
     */
    public void markEncoded(CharSequence string) {
        // no need to implement, wrapped automaticly
    }

    /* (non-Javadoc)
     * @see Encoder#isSafe()
     */
    public boolean isSafe() {
        return true;
    }

    /* (non-Javadoc)
     * @see Encoder#isApplyToSafelyEncoded()
     */
    public boolean isApplyToSafelyEncoded() {
        return false;
    }

    /* (non-Javadoc)
     * @see CodecIdentifierProvider#getCodecIdentifier()
     */
    public CodecIdentifier getCodecIdentifier() {
        return codecIdentifier;
    }
}
