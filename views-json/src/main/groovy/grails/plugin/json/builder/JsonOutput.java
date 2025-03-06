package grails.plugin.json.builder;
/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

import groovy.json.JsonException;
import groovy.json.JsonLexer;
import groovy.json.JsonToken;
import groovy.lang.Closure;
import groovy.lang.Writable;
import groovy.util.Expando;
import org.apache.groovy.json.internal.CharBuf;
import org.apache.groovy.json.internal.Chr;
import org.grails.buffer.FastStringWriter;

import jakarta.validation.constraints.NotNull;
import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * Temporary fork of {@link groovy.json.JsonOutput} until Groovy 2.5.0 is out.
 *
 * <p>Class responsible for the actual String serialization of the possible values of a JSON structure.
 * This class can also be used as a category, to add <code>toJson()</code> methods to various types.
 *
 * @author Guillaume Laforge
 * @author Roshan Dawrani
 * @author Andrey Bloschetsov
 * @author Rick Hightower
 * @author Graeme Rocher
 *
 * @since 1.8.0
 */
public class JsonOutput {

    public static final char OPEN_BRACKET = '[';
    public static final char CLOSE_BRACKET = ']';
    public static final char OPEN_BRACE = '{';
    public static final char CLOSE_BRACE = '}';
    public static final char COLON = ':';
    public static final char COMMA = ',';
    public static final char SPACE = ' ';
    public static final char NEW_LINE = '\n';
    public static final char QUOTE = '"';

    public static final String NULL_VALUE = "null";
    public static final String JSON_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String DEFAULT_TIMEZONE = "GMT";

    static final char[] EMPTY_STRING_CHARS = Chr.array(QUOTE, QUOTE);
    static final char[] EMPTY_MAP_CHARS = {OPEN_BRACE, CLOSE_BRACE};
    static final char[] EMPTY_LIST_CHARS = {OPEN_BRACKET, CLOSE_BRACKET};

    static final JsonGenerator DEFAULT_GENERATOR;

    static {
        JsonGenerator.Options options = new JsonGenerator.Options()
                .disableUnicodeEscaping()
                .dateFormat(JSON_DATE_FORMAT)
                .timezone(DEFAULT_TIMEZONE);

        DEFAULT_GENERATOR = new DefaultJsonGenerator(options);
    }

    /**
     * @param bool a {@link Boolean}
     * @return "true" or "false" for a boolean value
     */
    public static String toJson(Boolean bool) {
        return DEFAULT_GENERATOR.toJson(bool);
    }

    /**
     * @param n a {@link Number} to write as JSON string
     * @return a string representation for a number
     * @throws groovy.json.JsonException
     *         If the number is infinite or not a number
     */
    public static String toJson(Number n) {
        return DEFAULT_GENERATOR.toJson(n);
    }

    /**
     * @param c a {@link Character}
     * @return a JSON string representation of the character
     */
    public static String toJson(Character c) {
        return DEFAULT_GENERATOR.toJson(c);
    }

    /**
     * @param s a {@link String} to be represented in JSON
     * @return a properly encoded string with escape sequences
     */
    public static String toJson(String s) {
        return DEFAULT_GENERATOR.toJson(s);
    }

    /**
     * Format a date that is parseable from JavaScript, according to ISO-8601.
     *
     * @param date the date to format to a JSON string
     * @return a formatted date in the form of a string
     */
    public static String toJson(Date date) {
        return DEFAULT_GENERATOR.toJson(date);
    }

    /**
     * Format a calendar instance that is parseable from JavaScript, according to ISO-8601.
     *
     * @param cal the calendar to format to a JSON string
     * @return a formatted date in the form of a string
     */
    public static String toJson(Calendar cal) {
        return DEFAULT_GENERATOR.toJson(cal);
    }

    /**
     * @param uuid a {@link UUID}
     * @return the string representation of an uuid
     */
    public static String toJson(UUID uuid) {
        return DEFAULT_GENERATOR.toJson(uuid);
    }

    /**
     * @param url a {@link URL}
     * @return the string representation of the URL
     */
    public static String toJson(URL url) {
        return DEFAULT_GENERATOR.toJson(url);
    }

    /**
     * @param closure a {@link Closure} to format to JSON string
     * @return an object representation of a closure
     */
    public static String toJson(Closure closure) {
        return DEFAULT_GENERATOR.toJson(closure);
    }

    /**
     * @param expando a {@link Expando} object to format to JSON string
     * @return an object representation of an Expando
     */
    public static String toJson(Expando expando) {
        return DEFAULT_GENERATOR.toJson(expando);
    }

    /**
     * @param object an {@link Object} to format to JSON string
     * @return "null" for a null value, or a JSON array representation for a collection, array, iterator or enumeration,
     * or representation for other object.
     */
    public static String toJson(Object object) {
        return DEFAULT_GENERATOR.toJson(object);
    }

    /**
     * @param m a {@link Map} to format to JSON string
     * @return a JSON object representation for a map
     */
    public static String toJson(Map m) {
        return DEFAULT_GENERATOR.toJson(m);
    }

    /**
     * Pretty print a JSON payload.
     *
     * @param jsonPayload a JSON payload
     * @return a pretty representation of JSON payload.
     */
    public static String prettyPrint(String jsonPayload) {
        int indentSize = 0;
        // Just a guess that the pretty view will take a 20 percent more than original.
        final CharBuf output = CharBuf.create((int) (jsonPayload.length() * 1.2));

        JsonLexer lexer = new JsonLexer(new StringReader(jsonPayload));
        // Will store already created indents.
        Map<Integer, char[]> indentCache = new HashMap<>();
        while (lexer.hasNext()) {
            JsonToken token = lexer.next();
            switch (token.getType()) {
                case OPEN_CURLY:
                    indentSize += 4;
                    output.addChars(Chr.array(OPEN_BRACE, NEW_LINE)).addChars(getIndent(indentSize, indentCache));

                    break;
                case CLOSE_CURLY:
                    indentSize -= 4;
                    output.addChar(NEW_LINE);
                    if (indentSize > 0) {
                        output.addChars(getIndent(indentSize, indentCache));
                    }
                    output.addChar(CLOSE_BRACE);

                    break;
                case OPEN_BRACKET:
                    indentSize += 4;
                    output.addChars(Chr.array(OPEN_BRACKET, NEW_LINE)).addChars(getIndent(indentSize, indentCache));

                    break;
                case CLOSE_BRACKET:
                    indentSize -= 4;
                    output.addChar(NEW_LINE);
                    if (indentSize > 0) {
                        output.addChars(getIndent(indentSize, indentCache));
                    }
                    output.addChar(CLOSE_BRACKET);

                    break;
                case COMMA:
                    output.addChars(Chr.array(COMMA, NEW_LINE)).addChars(getIndent(indentSize, indentCache));

                    break;
                case COLON:
                    output.addChars(Chr.array(COLON, SPACE));

                    break;
                case STRING:
                    String textStr = token.getText();
                    String textWithoutQuotes = textStr.substring(1, textStr.length() - 1);
                    if (!textWithoutQuotes.isEmpty()) {
                        output.addJsonEscapedString(textWithoutQuotes);
                    } else {
                        output.addQuoted(Chr.array());
                    }

                    break;
                default:
                    output.addString(token.getText());
            }
        }

        return output.toString();
    }

    /**
     * Creates new indent if it not exists in the indent cache.
     *
     * @return indent with the specified size.
     */
    private static char[] getIndent(int indentSize, Map<Integer, char[]> indentCache) {
        char[] indent = indentCache.get(indentSize);
        if (indent == null) {
            indent = new char[indentSize];
            Arrays.fill(indent, SPACE);
            indentCache.put(indentSize, indent);
        }

        return indent;
    }

    /**
     * Obtains JSON unescaped text for the given text
     *
     * @param text The text
     * @return The unescaped text
     */
    public static JsonUnescaped unescaped(CharSequence text) {
        return new JsonUnescaped(text);
    }

    /**
     * Represents unescaped JSON
     */
    public static class JsonUnescaped {
        private final CharSequence text;

        public JsonUnescaped(CharSequence text) {
            this.text = text;
        }

        public CharSequence getText() {
            return text;
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }

    /**
     * Represents unescaped JSON
     */
    public static abstract class JsonWritable implements Writable, CharSequence {

        protected boolean inline = false;
        protected boolean first = true;

        public void setInline(boolean inline) {
            this.inline = inline;
        }

        public void setFirst(boolean first) {
            this.first = first;
        }

        @Override
        public String toString() {
            FastStringWriter out = new FastStringWriter();
            try {
                writeTo(out);
            } catch (IOException e) {
                throw new JsonException("Error writing JSON writable: " + e.getMessage(), e);
            }
            return out.toString();
        }

        @Override
        public int length() {
            return toString().length();
        }

        @Override
        public char charAt(int index) {
            return toString().charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toString().subSequence(start, end);
        }
    }
}
