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
package org.apache.grails.data.core.querytail

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * The rules of standard SQL, which every database shares: a single-quoted string in which the
 * quote is written twice, a double-quoted name in which the quote is written twice, a number, a
 * {@code --} comment to the end of the line, and a {@code /* ... *}{@code /} comment that does not
 * nest. The syntax of a database extends it and overrides the hooks its database differs on.
 */
@CompileStatic
@PackageScope
abstract class StandardSqlSyntax implements QueryTailSyntax {

    private static final char SPACE = ' '
    private static final char ZERO = '0'
    private static final char NINE = '9'

    private final String name

    protected StandardSqlSyntax(String name) {
        this.name = name
    }

    @Override
    int endOfComment(String sql, int index) {
        if (sql.startsWith('/*', index)) {
            return endOfBlockComment(sql, index)
        }
        if (sql.startsWith('--', index)) {
            return endOfLine(sql, index)
        }
        NONE
    }

    @Override
    int endOfValue(String sql, int index) {
        char c = sql.charAt(index)
        if (c == '\'') {
            return endOfString(sql, index)
        }
        if (c == '"' && doubleQuotedStrings()) {
            return endOfQuoted(sql, index, '"' as char, backslashEscapes(), true)
        }
        if (startsNumber(sql, index) && standalone(sql, index)) {
            return endOfNumber(sql, index)
        }
        NONE
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        if (sql.charAt(index) == '"' && !doubleQuotedStrings()) {
            return endOfQuoted(sql, index, '"' as char, false, true)
        }
        NONE
    }

    @Override
    String toString() {
        name
    }

    /** Whether a block comment can contain another. */
    protected boolean nestedComments() {
        false
    }

    /** Whether a line comment ends at a carriage return as well as at a line feed. */
    protected boolean carriageReturnEndsLine() {
        true
    }

    /** Whether a backslash escapes the character after it in a string. */
    protected boolean backslashEscapes() {
        false
    }

    /** Whether double quotes delimit a string rather than a name. */
    protected boolean doubleQuotedStrings() {
        false
    }

    /** Whether a character can be part of an unquoted name: a letter, a digit, an underscore or a dollar. */
    protected boolean isNamePart(char c) {
        isWordPart(c)
    }

    /** Ends the single-quoted string at the index, in which the quote is written twice. */
    protected int endOfString(String sql, int index) {
        endOfQuoted(sql, index, '\'' as char, backslashEscapes(), true)
    }

    /** Ends the line comment at the index: at a line feed, or at a carriage return where one ends a line. */
    protected int endOfLine(String sql, int start) {
        boolean carriageReturn = carriageReturnEndsLine()
        int i = start
        while (i < sql.length()) {
            char c = sql.charAt(i)
            if (c == '\n' || (c == '\r' && carriageReturn)) {
                return i
            }
            i++
        }
        i
    }

    /** Ends the block comment at the index, counting the comments inside it where they nest. */
    protected int endOfBlockComment(String sql, int start) {
        boolean nested = nestedComments()
        int depth = 1
        int i = start + 2
        while (i + 1 < sql.length()) {
            char c = sql.charAt(i)
            char next = sql.charAt(i + 1)
            if (c == '*' && next == '/') {
                depth--
                i += 2
                if (depth == 0) {
                    return i
                }
            }
            else if (nested && c == '/' && next == '*') {
                depth++
                i += 2
            }
            else {
                i++
            }
        }
        UNTERMINATED
    }

    /**
     * Ends the number at the index: digits with a fraction and an exponent, or a hexadecimal number.
     * Letters that follow it belong to it, so that nothing of a malformed number is left behind.
     */
    protected int endOfNumber(String sql, int start) {
        int length = sql.length()
        int i = start
        if (sql.charAt(i) == '0' && i + 1 < length && (sql.charAt(i + 1) == 'x' || sql.charAt(i + 1) == 'X')) {
            i += 2
        }
        else {
            while (i < length && isDigit(sql.charAt(i))) {
                i++
            }
            if (i < length && sql.charAt(i) == '.') {
                i++
                while (i < length && isDigit(sql.charAt(i))) {
                    i++
                }
            }
            if (i < length && (sql.charAt(i) == 'e' || sql.charAt(i) == 'E')) {
                boolean signed = i + 1 < length && (sql.charAt(i + 1) == '+' || sql.charAt(i + 1) == '-')
                int exponent = signed ? i + 2 : i + 1
                if (exponent < length && isDigit(sql.charAt(exponent))) {
                    i = exponent
                }
            }
        }
        while (i < length && isWordPart(sql.charAt(i))) {
            i++
        }
        i
    }

    /**
     * Ends the dollar-quoted string at the index, which opens with {@code $tag$} and ends at the same
     * tag, or with {@code $$} alone where tags are not read. A dollar after a name character, or
     * before a digit, where it is a positional parameter, opens nothing.
     *
     * @param tagged whether a tag can be written between the dollars
     */
    protected int endOfDollarQuoted(String sql, int index, boolean tagged) {
        if (sql.charAt(index) != '$' || !standalone(sql, index)) {
            return NONE
        }
        int tagLength = tagged ? dollarTagLength(sql, index) : (sql.startsWith('$$', index) ? 2 : 0)
        if (tagLength == 0) {
            return NONE
        }
        String tag = sql.substring(index, index + tagLength)
        int close = sql.indexOf(tag, index + tagLength)
        close < 0 ? UNTERMINATED : close + tagLength
    }

    /** Whether the character at the index starts a word: no name character precedes it. */
    protected boolean standalone(String sql, int index) {
        index <= 0 || !isNamePart(sql.charAt(index - 1))
    }

    /**
     * Ends a quoted run.
     *
     * @param backslashEscapes whether a backslash escapes the character after it
     * @param doubledClose whether the closing quote written twice stands for itself
     * @return the index after the closing quote, or {@link #UNTERMINATED}
     */
    protected static int endOfQuoted(String sql, int start, char close, boolean backslashEscapes, boolean doubledClose) {
        int i = start + 1
        while (i < sql.length()) {
            char c = sql.charAt(i)
            if (backslashEscapes && c == '\\') {
                i += 2
            }
            else if (c == close) {
                if (doubledClose && i + 1 < sql.length() && sql.charAt(i + 1) == close) {
                    i += 2
                }
                else {
                    return i + 1
                }
            }
            else {
                i++
            }
        }
        UNTERMINATED
    }

    /** @return whether a number starts at the index: a digit, or a point followed by one */
    protected static boolean startsNumber(String sql, int index) {
        if (index >= sql.length()) {
            return false
        }
        char c = sql.charAt(index)
        isDigit(c) || (c == '.' && index + 1 < sql.length() && isDigit(sql.charAt(index + 1)))
    }

    /** @return the character before the index, or 0 at the start */
    protected static char previous(String sql, int index) {
        index > 0 ? sql.charAt(index - 1) : 0 as char
    }

    /** @return whether a carriage return that no line feed follows is in the statement */
    protected static boolean hasLoneCarriageReturn(String sql) {
        for (int i = sql.indexOf('\r'); i >= 0; i = sql.indexOf('\r', i + 1)) {
            if (i + 1 >= sql.length() || sql.charAt(i + 1) != '\n') {
                return true
            }
        }
        false
    }

    /** @return whether a character is ASCII whitespace or an ASCII control character */
    protected static boolean isControlOrSpace(char c) {
        c <= SPACE || c == 0x7F
    }

    /** @return whether a character can be part of an unquoted name in every syntax */
    protected static boolean isWordPart(char c) {
        Character.isLetterOrDigit(c) || c == '_' || c == '$'
    }

    protected static boolean isDigit(char c) {
        c >= ZERO && c <= NINE
    }

    /** @return the length of the {@code $tag$} at the index, or 0 when there is none */
    private static int dollarTagLength(String sql, int start) {
        int i = start + 1
        while (i < sql.length()) {
            char c = sql.charAt(i)
            if (c == '$') {
                return i + 1 - start
            }
            boolean letter = Character.isLetter(c) || c == '_'
            if (!letter && (i == start + 1 || !isDigit(c))) {
                return 0
            }
            i++
        }
        0
    }
}
