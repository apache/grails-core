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
package org.apache.grails.gradle.model.cli

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

/**
 * Reads JSON that somebody else wrote: the descriptor of a command in the jar of a dependency, and
 * a file of the arguments of a command.
 *
 * <p>The whole grammar of RFC 8259 and nothing beside it: one value with nothing after it but white
 * space, no comments, no single quotes, no trailing commas, no byte order mark, no control character
 * in a string, no escape other than the nine the grammar has, {@code \u} with four hexadecimal
 * digits, and a surrogate only as the half of a pair. A name that an object has twice is refused,
 * since two readers would take different values for it. Arrays and objects are read to a depth the
 * caller gives, so the reader needs a stack of that depth and no more. Whatever is wrong is a
 * {@link Refused}: no other exception leaves the reader, whatever it is given.</p>
 *
 * <p>A value is a {@link Map} (in the order of the text), a {@link List}, a {@link String}, a
 * {@link Numeral}, a {@link Boolean} or {@code null}.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class StrictJson {

    private static final char[] HEX = '0123456789abcdef'.toCharArray()

    private final String text
    private final int maxDepth
    private int at

    private StrictJson(String text, int maxDepth) {
        this.text = text
        this.maxDepth = maxDepth
    }

    /**
     * Says that a text is not the JSON it has to be. The message says what kind of thing is wrong
     * and repeats nothing of the text.
     */
    static final class Refused extends Exception {

        Refused(String message) {
            super(message)
        }
    }

    /**
     * A number, as it is written: the reader decides nothing about its size or precision.
     */
    static final class Numeral {

        final String text

        Numeral(String text) {
            this.text = text
        }

        /**
         * @return whether the number is written as this integer, without a fraction or an exponent
         */
        boolean isInteger(int value) {
            text == Integer.toString(value)
        }

        @Override
        boolean equals(Object other) {
            other instanceof Numeral && ((Numeral) other).text == text
        }

        @Override
        int hashCode() {
            text.hashCode()
        }

        @Override
        String toString() {
            text
        }
    }

    /**
     * @param bytes the text, which has to be UTF-8
     * @param maxDepth how deep arrays and objects may be inside one another; 1 is a value with scalars in it
     * @return the value
     * @throws Refused when the bytes are not UTF-8 or the text is not one JSON value of at most that depth
     */
    static Object parse(byte[] bytes, int maxDepth) throws Refused {
        String decoded
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (CharacterCodingException ignored) {
            throw new Refused('is not UTF-8')
        }
        parse(decoded, maxDepth)
    }

    /**
     * @param text the text
     * @param maxDepth how deep arrays and objects may be inside one another
     * @return the value
     * @throws Refused when the text is not one JSON value of at most that depth
     */
    static Object parse(String text, int maxDepth) throws Refused {
        StrictJson reader = new StrictJson(text, maxDepth)
        reader.skipWhiteSpace()
        Object value = reader.value(0)
        reader.skipWhiteSpace()
        if (reader.at != text.length()) {
            throw new Refused('has something after the JSON value')
        }
        value
    }

    /**
     * Reads an array of strings and nothing else.
     *
     * @param bytes the text, which has to be UTF-8
     * @return the strings
     * @throws Refused when it is anything else
     */
    static List<String> stringArray(byte[] bytes) throws Refused {
        Object value = parse(bytes, 1)
        if (!(value instanceof List)) {
            throw new Refused('is not a JSON array')
        }
        for (Object element : (List) value) {
            if (!(element instanceof String)) {
                throw new Refused('has something in the array that is not a string')
            }
        }
        (List<String>) value
    }

    /**
     * Writes a value this class reads, in ASCII, without white space, the same text for the same value.
     *
     * @param value a map, a list, a string, a {@link Numeral}, an integer, a boolean or {@code null}
     * @return the JSON text
     */
    static String write(Object value) {
        StringBuilder json = new StringBuilder()
        append(json, value)
        json.toString()
    }

    private static void append(StringBuilder json, Object value) {
        if (value == null) {
            json.append('null')
        } else if (value instanceof CharSequence) {
            writeString(json, value.toString())
        } else if (value instanceof Map) {
            json.append('{')
            boolean first = true
            for (Map.Entry entry : ((Map) value).entrySet()) {
                if (!first) {
                    json.append(',')
                }
                first = false
                writeString(json, entry.key.toString())
                json.append(':')
                append(json, entry.value)
            }
            json.append('}')
        } else if (value instanceof Collection) {
            json.append('[')
            boolean first = true
            for (Object element : (Collection) value) {
                if (!first) {
                    json.append(',')
                }
                first = false
                append(json, element)
            }
            json.append(']')
        } else if (value instanceof Numeral || value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            json.append(value.toString())
        } else {
            throw new IllegalArgumentException("Not a JSON value: ${value.getClass().name}")
        }
    }

    private static void writeString(StringBuilder json, String value) {
        json.append('"')
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index)
            switch (character) {
                case '"': json.append('\\"'); break
                case '\\': json.append('\\\\'); break
                case '\n': json.append('\\n'); break
                case '\r': json.append('\\r'); break
                case '\t': json.append('\\t'); break
                case '\b': json.append('\\b'); break
                case '\f': json.append('\\f'); break
                default:
                    int code = (int) character
                    if (code < 0x20 || code > 0x7e) {
                        json.append('\\u')
                                .append(HEX[(code >> 12) & 0xf]).append(HEX[(code >> 8) & 0xf])
                                .append(HEX[(code >> 4) & 0xf]).append(HEX[code & 0xf])
                    } else {
                        json.append(character)
                    }
            }
        }
        json.append('"')
    }

    private Object value(int depth) throws Refused {
        if (at >= text.length()) {
            throw new Refused('ends where a JSON value has to be')
        }
        char character = text.charAt(at)
        if (character == ('"' as char)) {
            return string()
        }
        if (character == ('{' as char)) {
            return object(depth + 1)
        }
        if (character == ('[' as char)) {
            return array(depth + 1)
        }
        if (character == ('t' as char)) {
            literal('true')
            return Boolean.TRUE
        }
        if (character == ('f' as char)) {
            literal('false')
            return Boolean.FALSE
        }
        if (character == ('n' as char)) {
            literal('null')
            return null
        }
        if (character == ('-' as char) || isDigit(character)) {
            return number()
        }
        throw new Refused('has something that is not a JSON value')
    }

    private void literal(String word) throws Refused {
        if (!text.startsWith(word, at)) {
            throw new Refused('has something that is not a JSON value')
        }
        at += word.length()
    }

    private Map<String, Object> object(int depth) throws Refused {
        if (depth > maxDepth) {
            throw new Refused('is nested deeper than it can be')
        }
        Map<String, Object> members = new LinkedHashMap<String, Object>()
        at++
        skipWhiteSpace()
        if (at < text.length() && text.charAt(at) == ('}' as char)) {
            at++
            return members
        }
        while (true) {
            skipWhiteSpace()
            if (at >= text.length() || text.charAt(at) != ('"' as char)) {
                throw new Refused('has an object without a name where one has to be')
            }
            String name = string()
            skipWhiteSpace()
            if (at >= text.length() || text.charAt(at) != (':' as char)) {
                throw new Refused('has a name of an object without a colon after it')
            }
            at++
            skipWhiteSpace()
            Object value = value(depth)
            if (members.containsKey(name)) {
                throw new Refused('has an object with the same name twice')
            }
            members.put(name, value)
            skipWhiteSpace()
            if (at >= text.length()) {
                throw new Refused('ends inside an object')
            }
            char character = text.charAt(at++)
            if (character == ('}' as char)) {
                return members
            }
            if (character != (',' as char)) {
                throw new Refused('has an object whose members are not separated by commas')
            }
        }
    }

    private List<Object> array(int depth) throws Refused {
        if (depth > maxDepth) {
            throw new Refused('is nested deeper than it can be')
        }
        List<Object> elements = new ArrayList<Object>()
        at++
        skipWhiteSpace()
        if (at < text.length() && text.charAt(at) == (']' as char)) {
            at++
            return elements
        }
        while (true) {
            skipWhiteSpace()
            elements.add(value(depth))
            skipWhiteSpace()
            if (at >= text.length()) {
                throw new Refused('ends inside an array')
            }
            char character = text.charAt(at++)
            if (character == (']' as char)) {
                return elements
            }
            if (character != (',' as char)) {
                throw new Refused('has an array whose elements are not separated by commas')
            }
        }
    }

    private String string() throws Refused {
        StringBuilder string = new StringBuilder()
        at++
        while (true) {
            if (at >= text.length()) {
                throw new Refused('ends inside a string')
            }
            char character = text.charAt(at++)
            if (character == ('"' as char)) {
                return string.toString()
            }
            if (character < (0x20 as char)) {
                throw new Refused('has a control character in a string')
            }
            if (character == ('\\' as char)) {
                character = escape()
            }
            if (Character.isHighSurrogate(character)) {
                char low = next()
                if (!Character.isLowSurrogate(low)) {
                    throw new Refused('has half of a surrogate pair in a string')
                }
                string.append(character).append(low)
            } else if (Character.isLowSurrogate(character)) {
                throw new Refused('has half of a surrogate pair in a string')
            } else {
                string.append(character)
            }
        }
    }

    // the character after a high surrogate: as it is, or the one an escape gives
    private char next() throws Refused {
        if (at >= text.length()) {
            throw new Refused('ends inside a string')
        }
        char character = text.charAt(at++)
        if (character == ('\\' as char)) {
            // only \\u can give the other half, and anything else is refused as the half that is missing
            if (at >= text.length() || text.charAt(at) != ('u' as char)) {
                throw new Refused('has half of a surrogate pair in a string')
            }
            return escape()
        }
        character
    }

    private char escape() throws Refused {
        if (at >= text.length()) {
            throw new Refused('ends inside a string')
        }
        char character = text.charAt(at++)
        switch (character) {
            case '"': return '"' as char
            case '\\': return '\\' as char
            case '/': return '/' as char
            case 'b': return '\b' as char
            case 'f': return '\f' as char
            case 'n': return '\n' as char
            case 'r': return '\r' as char
            case 't': return '\t' as char
            case 'u':
                if (at + 4 > text.length()) {
                    throw new Refused('ends inside a string')
                }
                int code = 0
                for (int index = 0; index < 4; index++) {
                    int digit = Character.digit(text.charAt(at++), 16)
                    // Character.digit also takes the digits of other scripts
                    if (digit < 0 || text.charAt(at - 1) > (0x7f as char)) {
                        throw new Refused('has an escape in a string that JSON does not have')
                    }
                    code = code * 16 + digit
                }
                return (char) code
            default:
                throw new Refused('has an escape in a string that JSON does not have')
        }
    }

    private Numeral number() throws Refused {
        int start = at
        if (text.charAt(at) == ('-' as char)) {
            at++
        }
        if (at >= text.length() || !isDigit(text.charAt(at))) {
            throw new Refused('has a number that is not written as JSON writes one')
        }
        if (text.charAt(at) == ('0' as char)) {
            at++
        } else {
            digits()
        }
        if (at < text.length() && text.charAt(at) == ('.' as char)) {
            at++
            if (at >= text.length() || !isDigit(text.charAt(at))) {
                throw new Refused('has a number that is not written as JSON writes one')
            }
            digits()
        }
        if (at < text.length() && (text.charAt(at) == ('e' as char) || text.charAt(at) == ('E' as char))) {
            at++
            if (at < text.length() && (text.charAt(at) == ('+' as char) || text.charAt(at) == ('-' as char))) {
                at++
            }
            if (at >= text.length() || !isDigit(text.charAt(at))) {
                throw new Refused('has a number that is not written as JSON writes one')
            }
            digits()
        }
        new Numeral(text.substring(start, at))
    }

    private void digits() {
        while (at < text.length() && isDigit(text.charAt(at))) {
            at++
        }
    }

    private static boolean isDigit(char character) {
        character >= ('0' as char) && character <= ('9' as char)
    }

    private void skipWhiteSpace() {
        while (at < text.length()) {
            char character = text.charAt(at)
            if (character != (' ' as char) && character != ('\t' as char) && character != ('\n' as char) && character != ('\r' as char)) {
                return
            }
            at++
        }
    }
}
