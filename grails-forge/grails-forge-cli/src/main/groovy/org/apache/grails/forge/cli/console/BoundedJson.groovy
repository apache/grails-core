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
package org.apache.grails.forge.cli.console

import groovy.transform.CompileStatic

/**
 * Reads a JSON document that was not written by the CLI: strictly by the grammar, and within bounds
 * that are given before anything is read, so that neither the depth of the document, nor the number
 * of its values, nor the length of a text is the document's to choose. What is refused is said
 * without repeating anything of the document.
 *
 * <p>An object is a {@link Map} in the order of the document, an array a {@link List}, a text a
 * {@link String}, a number that is written with digits only a {@link Long} and any other number a {@link BigDecimal}, and {@code true}, {@code false} and {@code null}
 * are themselves. An object that has a key twice is refused: which of the two a reader takes would
 * be the reader's choice.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class BoundedJson {

    private static final int MAX_NUMBER = 32

    private final String text
    private final int maxDepth
    private final int maxValues
    private final int maxText
    private int at
    private int values

    private BoundedJson(String text, int maxDepth, int maxValues, int maxText) {
        this.text = text
        this.maxDepth = maxDepth
        this.maxValues = maxValues
        this.maxText = maxText
    }

    /**
     * @param text the document
     * @param maxDepth how deep objects and arrays are nested, at most; the document itself is at depth 1
     * @param maxValues how many values the document has, at most, the keys of objects not counted
     * @param maxText how many characters a text or a key has, at most
     * @return the value the document is
     * @throws Refused when it is not one JSON value, or is beyond a bound
     */
    static Object parse(String text, int maxDepth, int maxValues, int maxText) throws Refused {
        BoundedJson json = new BoundedJson(text, maxDepth, maxValues, maxText)
        json.space()
        Object value = json.value(1)
        json.space()
        if (json.at < text.length()) {
            throw new Refused('has something after the JSON document')
        }
        return value
    }

    private Object value(int depth) throws Refused {
        if (++values > maxValues) {
            throw new Refused("has more than ${maxValues} values")
        }
        if (at >= text.length()) {
            throw new Refused('ends before the JSON document does')
        }
        char next = text.charAt(at)
        if (next == ('{' as char) || next == ('[' as char)) {
            if (depth > maxDepth) {
                throw new Refused("is nested deeper than ${maxDepth}")
            }
            at++
            return next == ('{' as char) ? members(depth) : elements(depth)
        }
        if (next == ('"' as char)) {
            return string()
        }
        if (next == ('-' as char) || (next >= ('0' as char) && next <= ('9' as char))) {
            return number()
        }
        for (Map.Entry<String, Object> literal : (['true': Boolean.TRUE, 'false': Boolean.FALSE, 'null': null] as Map<String, Object>).entrySet()) {
            if (text.startsWith(literal.key, at)) {
                at += literal.key.length()
                return literal.value
            }
        }
        throw new Refused('is not JSON')
    }

    private Map<String, Object> members(int depth) throws Refused {
        Map<String, Object> map = new LinkedHashMap<>()
        space()
        if (take('}' as char)) {
            return map
        }
        while (true) {
            space()
            if (at >= text.length() || text.charAt(at) != ('"' as char)) {
                throw new Refused('is not JSON')
            }
            String key = string()
            space()
            if (!take(':' as char)) {
                throw new Refused('is not JSON')
            }
            space()
            if (map.containsKey(key)) {
                throw new Refused('has an object with the same key twice')
            }
            map.put(key, value(depth + 1))
            space()
            if (take('}' as char)) {
                return map
            }
            if (!take(',' as char)) {
                throw new Refused('is not JSON')
            }
        }
    }

    private List<Object> elements(int depth) throws Refused {
        List<Object> list = []
        space()
        if (take(']' as char)) {
            return list
        }
        while (true) {
            space()
            list.add(value(depth + 1))
            space()
            if (take(']' as char)) {
                return list
            }
            if (!take(',' as char)) {
                throw new Refused('is not JSON')
            }
        }
    }

    private String string() throws Refused {
        at++
        StringBuilder read = new StringBuilder()
        while (true) {
            if (at >= text.length()) {
                throw new Refused('ends before the JSON document does')
            }
            char next = text.charAt(at++)
            if (next == ('"' as char)) {
                return read.toString()
            }
            if (next < (' ' as char)) {
                throw new Refused('has a control character in a text')
            }
            if (read.length() >= maxText) {
                throw new Refused("has a text of more than ${maxText} characters")
            }
            if (next != ('\\' as char)) {
                read.append(next)
                continue
            }
            if (at >= text.length()) {
                throw new Refused('ends before the JSON document does')
            }
            char escaped = text.charAt(at++)
            int plain = '"\\/bfnrt'.indexOf((int) escaped)
            if (plain > -1) {
                read.append('"\\/\b\f\n\r\t'.charAt(plain))
            } else if (escaped == ('u' as char) && at + 4 <= text.length() && isHexadecimal(at)) {
                read.append((char) Integer.parseInt(text.substring(at, at + 4), 16))
                at += 4
            } else {
                throw new Refused('has an escape in a text that JSON does not have')
            }
        }
    }

    private boolean isHexadecimal(int from) {
        for (int i = from; i < from + 4; i++) {
            if ('0123456789abcdefABCDEF'.indexOf((int) text.charAt(i)) < 0) {
                return false
            }
        }
        return true
    }

    private Number number() throws Refused {
        int start = at
        take('-' as char)
        int whole = digits()
        if (whole == 0 || (whole > 1 && text.charAt(at - whole) == ('0' as char))) {
            throw new Refused('has a number that JSON does not have')
        }
        if (take('.' as char) && digits() == 0) {
            throw new Refused('has a number that JSON does not have')
        }
        if (take('e' as char) || take('E' as char)) {
            if (!take('+' as char)) {
                take('-' as char)
            }
            if (digits() == 0) {
                throw new Refused('has a number that JSON does not have')
            }
        }
        if (at - start > MAX_NUMBER) {
            throw new Refused("has a number of more than ${MAX_NUMBER} characters")
        }
        try {
            String written = text.substring(start, at)
            // a whole number as it is written, so that 1 is told from 1.0 and from 1e0
            if (written.length() <= 18 && written ==~ /-?[0-9]+/) {
                return Long.valueOf(written)
            }
            return new BigDecimal(written)
        } catch (NumberFormatException | ArithmeticException ignored) {
            throw new Refused('has a number that cannot be read')
        }
    }

    private int digits() {
        int start = at
        // a long run of digits is not read to its end: it is over the bound either way
        while (at < text.length() && at - start <= MAX_NUMBER && text.charAt(at) >= ('0' as char) && text.charAt(at) <= ('9' as char)) {
            at++
        }
        return at - start
    }

    private boolean take(char expected) {
        if (at < text.length() && text.charAt(at) == expected) {
            at++
            return true
        }
        return false
    }

    private void space() {
        while (at < text.length() && ' \t\n\r'.indexOf((int) text.charAt(at)) > -1) {
            at++
        }
    }

    /** What is wrong with a document, as the end of a sentence that starts with what the document is. */
    static final class Refused extends Exception {

        Refused(String message) {
            super(message)
        }
    }
}
