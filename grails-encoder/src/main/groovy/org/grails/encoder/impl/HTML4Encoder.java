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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.encoder.AbstractCharReplacementEncoder;
import org.grails.encoder.CodecIdentifier;
import org.grails.encoder.DefaultCodecIdentifier;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * This encoder is for HTML4 documents. It uses escaping information from
 * Spring's HtmlUtils so that this is compatible with the previous
 * "encodeAsHTML" in older Grails versions.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public class HTML4Encoder extends AbstractCharReplacementEncoder {
    private static final Log log = LogFactory.getLog(HTML4Encoder.class);
    static final String HTML4_CODEC_NAME = "HTML4";
    static final CodecIdentifier HTML4_CODEC_IDENTIFIER = new DefaultCodecIdentifier(HTML4_CODEC_NAME);
    Map<Character, String> replacements = new ConcurrentHashMap<Character, String>();
    private static final String NULL_MARKER = "NULL_MARKER";

    public HTML4Encoder() {
        super(HTML4_CODEC_IDENTIFIER);
    }

    /*
     * (non-Javadoc)
     * @see
     * AbstractCharReplacementEncoder
     * #escapeCharacter(char, char)
     */
    @Override
    protected String escapeCharacter(char ch, char previousChar) {
        Character key = Character.valueOf(ch);
        String replacement = replacements.get(key);
        if (replacement == null) {
            replacement = StreamingHTMLEncoderHelper.convertToReference(ch);
            replacements.put(key, replacement != null ? replacement : NULL_MARKER);
        }
        return replacement != NULL_MARKER ? replacement : null;
    }

    /**
     * Calls Spring's HtmlUtils's private method to convert characters to HTML entities.
     */
    private static final class StreamingHTMLEncoderHelper {
        private static Object instance;
        private static Method mapMethod;
        private static boolean disabled = false;
        static {
            try {
                Field instanceField = ReflectionUtils.findField(HtmlUtils.class, "characterEntityReferences");
                ReflectionUtils.makeAccessible(instanceField);
                instance = instanceField.get(null);
                mapMethod = ReflectionUtils.findMethod(instance.getClass(), "convertToReference", char.class);
                if (mapMethod != null)
                    ReflectionUtils.makeAccessible(mapMethod);
            }
            catch (Exception e) {
                log.warn("Couldn't use reflection for resolving characterEntityReferences in HtmlUtils class", e);
                disabled = true;
            }
        }

        /**
         * Convert to html reference.
         *
         * @param c the character to convert
         * @return the converted entity, returns null if the character doesn't have a replacement
         */
        public static final String convertToReference(char c) {
            if (!disabled) {
                return (String)ReflectionUtils.invokeMethod(mapMethod, instance, c);
            }

            String charAsString = String.valueOf(c);
            String replacement = HtmlUtils.htmlEscape(charAsString);
            if (charAsString.equals(replacement)) {
                return null;
            }
            return replacement;
        }
    }
}
