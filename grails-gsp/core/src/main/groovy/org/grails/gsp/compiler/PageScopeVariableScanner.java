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
package org.grails.gsp.compiler;

import java.util.Set;

/**
 * Finds the names a page introduces through the {@code var} and {@code status} attributes of the
 * namespaced tags it calls: {@code <g:set var="total"/>}, {@code <g:each var="book" status="i">},
 * {@code <g:eachError var="error">}.
 *
 * <p>Read from the page source rather than from the parsed attributes because attributes are parsed
 * only on the pass that writes the class, by which point the annotation carrying these names has
 * already been written. Matching a name that turns out not to be a page scope variable costs only
 * that the name resolves dynamically, so the scan errs towards matching.</p>
 *
 * <p>One forward pass that steps over each expression and attribute value as a unit, ending it where
 * {@link GroovyPageExpressionParser} ends it when the page is compiled. A regular expression
 * cannot: it pairs the quotes of an attribute without knowing what an expression is, so a quote
 * inside one &mdash; {@code content="${t ?: 'Untitled'.replaceAll('"', '\'')}"} &mdash; ends the
 * attribute as far as it can tell, the page's later quotes pair up across tags and swallow the
 * {@code >} that should end the attempt, and the pattern reads on through the rest of the page,
 * recursing once per character until the compiling thread runs out of stack. Here the work is linear
 * in the page and the depth of the call stack is constant, whatever the page contains.</p>
 */
final class PageScopeVariableScanner {

    private PageScopeVariableScanner() {
    }

    /**
     * Adds to {@code names} every identifier named by a {@code var} or {@code status} attribute of a
     * namespaced tag in {@code source}.
     */
    static void collect(String source, Set<String> names) {
        int length = source.length();
        int i = 0;
        while (i < length) {
            int afterNamespace = source.charAt(i) == '<' ? namespaceEnd(source, i + 1) : -1;
            i = afterNamespace < 0 ? i + 1 : scanTag(source, afterNamespace, names);
        }
    }

    /** The index just past {@code \w+:} starting at {@code from}, or -1 when there is none there. */
    private static int namespaceEnd(String source, int from) {
        int i = from;
        while (i < source.length() && isWordChar(source.charAt(i))) {
            i++;
        }
        return i > from && i < source.length() && source.charAt(i) == ':' ? i + 1 : -1;
    }

    /**
     * Reads the rest of one tag, recording each {@code var} or {@code status} it carries, and returns
     * the index just past its closing {@code >}, or the end of the source when it has none.
     */
    private static int scanTag(String source, int from, Set<String> names) {
        int length = source.length();
        int i = from;
        while (i < length) {
            char c = source.charAt(i);
            if (c == '>') {
                return i + 1;
            }
            if (isExpressionStart(source, i)) {
                i = skipExpression(source, i + 2);
            } else if (c == '"' || c == '\'') {
                i = skipAttributeValue(source, i + 1, c);
            } else if (isWordChar(c) && !isWordChar(source.charAt(i - 1))) {
                i = readAttributeName(source, i, names);
            } else {
                i++;
            }
        }
        return length;
    }

    /**
     * Reads an attribute name starting at {@code from}. When it is {@code var} or {@code status} and
     * its value is a quoted identifier, records the identifier. Returns the index just past the name,
     * leaving the value to be stepped over as any other quoted value is.
     */
    private static int readAttributeName(String source, int from, Set<String> names) {
        int length = source.length();
        int end = from;
        while (end < length && isWordChar(source.charAt(end))) {
            end++;
        }
        String name = source.substring(from, end);
        if (!name.equals("var") && !name.equals("status")) {
            return end;
        }
        int i = skipWhitespace(source, end);
        if (i >= length || source.charAt(i) != '=') {
            return end;
        }
        i = skipWhitespace(source, i + 1);
        if (i >= length || (source.charAt(i) != '"' && source.charAt(i) != '\'')) {
            return end;
        }
        int identifierStart = i + 1;
        int identifierEnd = identifierStart;
        if (identifierEnd < length && isIdentifierStart(source.charAt(identifierEnd))) {
            identifierEnd++;
            while (identifierEnd < length && isIdentifierPart(source.charAt(identifierEnd))) {
                identifierEnd++;
            }
            char closing = identifierEnd < length ? source.charAt(identifierEnd) : 0;
            if (closing == '"' || closing == '\'') {
                names.add(source.substring(identifierStart, identifierEnd));
            }
        }
        return end;
    }

    /**
     * Steps over a quoted attribute value whose opening quote is just before {@code from}, finding its
     * end exactly as {@link GroovyPageParser} does when it reads the attribute, so a quote inside an
     * expression does not end the value. Returns the index just past the closing quote, or the end of
     * the source.
     */
    private static int skipAttributeValue(String source, int from, char quote) {
        int end = new GroovyPageExpressionParser(source, from, quote, (char) 0, false).parse();
        return end < 0 ? source.length() : end + 1;
    }

    /**
     * Steps over the body of a {@code ${...}} expression starting at {@code from}, just past its
     * opening brace, finding its end exactly as {@link GroovyPageScanner} does. Returns the index just
     * past the closing brace, or the end of the source.
     */
    private static int skipExpression(String source, int from) {
        int end = new GroovyPageExpressionParser(source, from, '}', (char) 0, true).parse();
        return end < 0 ? source.length() : end + 1;
    }

    private static boolean isExpressionStart(String source, int i) {
        return source.charAt(i) == '$' && i + 1 < source.length() && source.charAt(i + 1) == '{';
    }

    private static int skipWhitespace(String source, int from) {
        int i = from;
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
            i++;
        }
        return i;
    }

    /** {@code \w} as a regular expression reads it: ASCII letters, digits and underscore. */
    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return isWordChar(c) || c == '$';
    }
}
